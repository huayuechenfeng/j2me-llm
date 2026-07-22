
package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public final class ChatCanvas extends Canvas {
    public final Command composeCommand = new Command("输入", Command.OK, 1);
    public final Command imageCommand = new Command("图片", Command.SCREEN, 2);
    public final Command profilesCommand = new Command("档案", Command.SCREEN, 3);
    public final Command settingsCommand = new Command("设置", Command.SCREEN, 4);
    public final Command thinkingCommand = new Command("思维链", Command.SCREEN, 5);
    public final Command clearCommand = new Command("清空", Command.SCREEN, 6);
    public final Command stopCommand = new Command("停止", Command.STOP, 1);
    public final Command exitCommand = new Command("退出", Command.EXIT, 9);

    private static final int COLOR_BACKGROUND = 0xf3f5fa;
    private static final int COLOR_HEADER = 0x4b3f8f;
    private static final int COLOR_USER = 0x6554c0;
    private static final int COLOR_ASSISTANT = 0xffffff;
    private static final int COLOR_TEXT = 0x202433;
    private static final int COLOR_MUTED = 0x73798c;
    private static final int COLOR_REASONING = 0xeeeafd;
    private static final int COLOR_ERROR = 0xb42318;
    private static final int REPAINT_INTERVAL_MS = 100;

    private Vector messages;
    private ProviderProfile profile;
    private final Vector layouts = new Vector();
    private int scroll;
    private int maximumScroll;
    private boolean scrollToBottom = true;
    private boolean showReasoning;
    private volatile boolean busy;
    private boolean repaintDirty;
    private boolean repaintWorkerRunning;

    public ChatCanvas(Vector initialMessages, ProviderProfile initialProfile,
            CommandListener listener) {
        messages = initialMessages;
        profile = initialProfile;
        showReasoning = initialProfile != null && initialProfile.reasoningExpanded;
        setFullScreenMode(false);
        addCommand(profilesCommand);
        addCommand(settingsCommand);
        addCommand(thinkingCommand);
        addCommand(clearCommand);
        addCommand(exitCommand);
        refreshSendCommands();
        setCommandListener(listener);
    }

    public void setConversation(Vector value, ProviderProfile valueProfile) {
        messages = value == null ? new Vector() : value;
        profile = valueProfile;
        showReasoning = valueProfile != null && valueProfile.reasoningExpanded;
        scroll = 0;
        scrollToBottom = true;
        layouts.removeAllElements();
        refreshSendCommands();
        repaint();
    }

    public void setProfile(ProviderProfile value) {
        profile = value;
        showReasoning = value != null && value.reasoningExpanded;
        layouts.removeAllElements();
        refreshSendCommands();
        repaint();
    }

    public void setBusy(boolean value) {
        if (busy == value) return;
        busy = value;
        refreshSendCommands();
        if (busy) requestThrottledRepaint();
        else repaint();
    }

    public void toggleReasoning() {
        showReasoning = !showReasoning;
        if (profile != null) profile.reasoningExpanded = showReasoning;
        scrollToBottom = true;
        layouts.removeAllElements();
        repaint();
    }

    public boolean isReasoningExpanded() {
        return showReasoning;
    }

    public void contentChanged() {
        scrollToBottom = true;
        if (busy) requestThrottledRepaint();
        else repaint();
    }

    public void clearLayoutCache() {
        layouts.removeAllElements();
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        int step = Font.getDefaultFont().getHeight() * 3;
        if (action == UP) {
            scroll -= step;
            if (scroll < 0) scroll = 0;
            scrollToBottom = false;
            repaint();
        } else if (action == DOWN) {
            scroll += step;
            if (scroll > maximumScroll) scroll = maximumScroll;
            scrollToBottom = false;
            repaint();
        }
    }

    protected void paint(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        Font normal = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        int headerHeight = bold.getHeight() + 14;
        int statusHeight = normal.getHeight() + 6;
        int viewHeight = height - headerHeight - statusHeight;

        graphics.setColor(COLOR_BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        drawHeader(graphics, width, headerHeight, bold);

        trimLayoutVector();
        int totalHeight = measureMessages(normal, bold, width);
        maximumScroll = totalHeight - viewHeight;
        if (maximumScroll < 0) maximumScroll = 0;
        if (scrollToBottom) {
            scroll = maximumScroll;
            scrollToBottom = false;
        }
        if (scroll > maximumScroll) scroll = maximumScroll;

        graphics.setClip(0, headerHeight, width, viewHeight);
        int y = headerHeight + 8 - scroll;
        if (messages.size() == 0) {
            graphics.setFont(normal);
            graphics.setColor(COLOR_MUTED);
            String empty = profile != null && profile.multimodal
                    ? "按“输入”或“图片”开始聊天" : "按“输入”开始聊天";
            graphics.drawString(empty, width / 2, headerHeight + viewHeight / 2,
                    Graphics.HCENTER | Graphics.BASELINE);
        } else {
            int i;
            for (i = 0; i < messages.size(); i++) {
                MessageLayout layout = layoutAt(i, normal, bold, width);
                if (y + layout.height >= headerHeight && y <= headerHeight + viewHeight) {
                    drawMessage(graphics, layout, y, normal, bold, width);
                }
                y += layout.height + 8;
            }
        }

        graphics.setClip(0, 0, width, height);
        graphics.setColor(0xe6e9f2);
        graphics.fillRect(0, height - statusHeight, width, statusHeight);
        graphics.setFont(normal);
        graphics.setColor(COLOR_MUTED);
        graphics.drawString(statusText(), 6, height - statusHeight + 3,
                Graphics.TOP | Graphics.LEFT);
    }

    private void drawHeader(Graphics graphics, int width, int height, Font font) {
        graphics.setColor(COLOR_HEADER);
        graphics.fillRect(0, 0, width, height);
        graphics.setFont(font);
        graphics.setColor(0xffffff);
        String title = profile == null ? "J2ME LLM" : profile.displayName();
        if (profile != null && profile.model != null && profile.model.length() > 0) {
            title += " · " + profile.model;
        }
        if (title.length() > 30) title = title.substring(0, 29) + "…";
        graphics.drawString(title, 9, 7, Graphics.TOP | Graphics.LEFT);
        graphics.setColor(busy ? 0xffd166 : 0x72e0a8);
        graphics.fillArc(width - 17, 11, 7, 7, 0, 360);
    }

    private String statusText() {
        if (busy) return "● 正在接收模型响应";
        String mode = "自动";
        if (profile != null && (profile.thinkingMode == ProviderProfile.THINKING_ON
                || ProviderPresets.isKimiAlwaysThinking(profile))) mode = "开";
        else if (profile != null && profile.thinkingMode == ProviderProfile.THINKING_OFF) mode = "关";
        return "请求思考：" + mode + " · 思维链：" + (showReasoning ? "展开" : "折叠");
    }

    private int measureMessages(Font normal, Font bold, int width) {
        int total = 16;
        int i;
        for (i = 0; i < messages.size(); i++) {
            total += layoutAt(i, normal, bold, width).height + 8;
        }
        return total;
    }

    private MessageLayout layoutAt(int index, Font normal, Font bold, int width) {
        ChatMessage message = (ChatMessage) messages.elementAt(index);
        MessageLayout layout = index < layouts.size()
                ? (MessageLayout) layouts.elementAt(index) : null;
        if (layout == null || layout.message != message || layout.revision != message.getRevision()
                || layout.width != width || layout.expanded != showReasoning) {
            layout = new MessageLayout(message, normal, bold, width, showReasoning);
            if (index < layouts.size()) layouts.setElementAt(layout, index);
            else {
                while (layouts.size() < index) layouts.addElement(null);
                layouts.addElement(layout);
            }
        }
        return layout;
    }

    private void trimLayoutVector() {
        while (layouts.size() > messages.size()) layouts.removeElementAt(layouts.size() - 1);
    }

    private void drawMessage(Graphics graphics, MessageLayout layout, int y,
            Font normal, Font bold, int width) {
        ChatMessage message = layout.message;
        boolean user = ChatMessage.ROLE_USER.equals(message.role);
        int bubbleWidth = (width * 82) / 100;
        int x = user ? width - bubbleWidth - 6 : 6;
        int textX = x + 8;
        int textWidth = bubbleWidth - 16;

        graphics.setColor(user ? COLOR_USER : COLOR_ASSISTANT);
        graphics.fillRoundRect(x, y, bubbleWidth, layout.height, 12, 12);
        if (!user) {
            graphics.setColor(0xdfe3ec);
            graphics.drawRoundRect(x, y, bubbleWidth, layout.height, 12, 12);
        }

        int cursorY = y + 5;
        graphics.setFont(bold);
        graphics.setColor(user ? 0xffffff : COLOR_MUTED);
        graphics.drawString(user ? "你" : "AI", textX, cursorY, Graphics.TOP | Graphics.LEFT);
        cursorY += bold.getHeight();

        if (layout.hasReasoning) {
            graphics.setColor(user ? 0x8172d5 : COLOR_REASONING);
            int reasonHeight = normal.getHeight() + 3;
            if (layout.expanded) reasonHeight += layout.reasonLines.count * normal.getHeight() + 4;
            graphics.fillRoundRect(textX, cursorY + 2, textWidth, reasonHeight, 8, 8);
            graphics.setFont(normal);
            graphics.setColor(user ? 0xffffff : COLOR_HEADER);
            graphics.drawString(layout.expanded ? "思考" : "思考 · 已折叠", textX + 4,
                    cursorY + 3, Graphics.TOP | Graphics.LEFT);
            cursorY += normal.getHeight() + 5;
            if (layout.expanded) {
                graphics.setColor(user ? 0xffffff : COLOR_MUTED);
                cursorY = drawLines(graphics, layout.reasoning, layout.reasonLines,
                        textX + 4, cursorY, normal);
                cursorY += 4;
            }
        }

        graphics.setFont(normal);
        graphics.setColor(message.error ? COLOR_ERROR : (user ? 0xffffff : COLOR_TEXT));
        cursorY = drawLines(graphics, layout.content, layout.contentLines,
                textX, cursorY + 3, normal);
        cursorY += 5;

        if (message.hasMedia()) {
            graphics.setColor(user ? 0xded8ff : COLOR_MUTED);
            String label = message.getImageName().length() > 0
                    ? "▣ " + message.getImageName() : "▣ 模型图片";
            if (label.length() > 30) label = label.substring(0, 29) + "…";
            graphics.drawString(label, textX, cursorY, Graphics.TOP | Graphics.LEFT);
            cursorY += normal.getHeight() + 3;
            Image image = message.getImagePreview();
            if (image != null) {
                graphics.drawImage(image, textX + (textWidth - image.getWidth()) / 2, cursorY,
                        Graphics.TOP | Graphics.LEFT);
            } else if (layout.status.length() > 0) {
                graphics.setColor(user ? 0xded8ff : COLOR_MUTED);
                drawLines(graphics, layout.status, layout.statusLines, textX, cursorY, normal);
            }
        }
    }

    private int drawLines(Graphics graphics, String text, LineMap lines, int x, int y, Font font) {
        int i;
        for (i = 0; i < lines.count; i++) {
            int length = lines.lengths[i];
            if (length > 0) graphics.drawSubstring(text, lines.starts[i], length, x, y,
                    Graphics.TOP | Graphics.LEFT);
            y += font.getHeight();
        }
        return y;
    }

    private void refreshSendCommands() {
        removeCommand(composeCommand);
        removeCommand(imageCommand);
        removeCommand(stopCommand);
        if (busy) addCommand(stopCommand);
        else {
            addCommand(composeCommand);
            if (profile != null && profile.multimodal) addCommand(imageCommand);
        }
    }

    private synchronized void requestThrottledRepaint() {
        repaintDirty = true;
        if (repaintWorkerRunning) return;
        repaintWorkerRunning = true;
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try { Thread.sleep(REPAINT_INTERVAL_MS); }
                    catch (InterruptedException ignored) { }
                    boolean paintNow;
                    synchronized (ChatCanvas.this) {
                        paintNow = repaintDirty;
                        repaintDirty = false;
                    }
                    if (paintNow) repaint();
                    synchronized (ChatCanvas.this) {
                        if (!busy && !repaintDirty) {
                            repaintWorkerRunning = false;
                            return;
                        }
                    }
                }
            }
        }).start();
    }

    private static final class MessageLayout {
        final ChatMessage message;
        final int revision;
        final int width;
        final boolean expanded;
        final String content;
        final boolean hasReasoning;
        final String reasoning;
        final String status;
        final LineMap contentLines;
        final LineMap reasonLines;
        final LineMap statusLines;
        final int height;

        MessageLayout(ChatMessage value, Font normal, Font bold, int canvasWidth,
                boolean showReasoning) {
            message = value;
            revision = value.getRevision();
            width = canvasWidth;
            expanded = showReasoning;
            int bubbleWidth = (canvasWidth * 82) / 100;
            int textWidth = bubbleWidth - 16;
            String valueContent = value.getContent();
            if (valueContent.length() == 0 && value.pending) valueContent = "正在思考…";
            content = valueContent;
            hasReasoning = value.hasReasoning();
            reasoning = showReasoning && hasReasoning ? value.getReasoning() : "";
            status = value.getImageStatus();
            contentLines = new LineMap(content, normal, textWidth);
            reasonLines = showReasoning && hasReasoning
                    ? new LineMap(reasoning, normal, textWidth - 8) : null;
            statusLines = new LineMap(status, normal, textWidth);

            int measured = 8 + bold.getHeight();
            if (hasReasoning) {
                measured += normal.getHeight() + 5;
                if (showReasoning) measured += reasonLines.count * normal.getHeight() + 4;
            }
            measured += contentLines.count * normal.getHeight() + 8;
            if (value.hasMedia()) {
                measured += normal.getHeight() + 4;
                Image image = value.getImagePreview();
                if (image != null) measured += image.getHeight() + 5;
                else if (status.length() > 0) measured += statusLines.count * normal.getHeight() + 2;
            }
            height = measured;
        }
    }

    private static final class LineMap {
        int[] starts = new int[8];
        int[] lengths = new int[8];
        int count;

        LineMap(String text, Font font, int maximumWidth) {
            if (maximumWidth < 1) maximumWidth = 1;
            int lineStart = 0;
            int lineWidth = 0;
            int i;
            for (i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\n') {
                    add(lineStart, i - lineStart);
                    lineStart = i + 1;
                    lineWidth = 0;
                } else if (ch != '\r') {
                    int charWidth = font.charWidth(ch);
                    if (lineWidth + charWidth > maximumWidth && i > lineStart) {
                        add(lineStart, i - lineStart);
                        lineStart = i;
                        lineWidth = charWidth;
                    } else lineWidth += charWidth;
                }
            }
            if (lineStart < text.length()) add(lineStart, text.length() - lineStart);
            else if (count == 0 || text.endsWith("\n")) add(text.length(), 0);
        }

        private void add(int start, int length) {
            if (count == starts.length) {
                int[] newStarts = new int[count * 2];
                int[] newLengths = new int[count * 2];
                System.arraycopy(starts, 0, newStarts, 0, count);
                System.arraycopy(lengths, 0, newLengths, 0, count);
                starts = newStarts;
                lengths = newLengths;
            }
            starts[count] = start;
            lengths[count] = length;
            count++;
        }
    }
}

