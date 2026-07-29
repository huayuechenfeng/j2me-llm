
package com.chihoko.j2mellm.ui;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
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
    public final Command composeCommand = new Command(
            I18n.text(TextId.COMPOSE), Command.OK, 1);
    public final Command imageCommand = new Command(
            I18n.text(TextId.IMAGE), Command.SCREEN, 2);
    public final Command conversationsCommand = new Command(
            I18n.text(TextId.CONVERSATIONS), Command.SCREEN, 3);
    public final Command messagesCommand = new Command(
            I18n.text(TextId.MESSAGE_LIST), Command.SCREEN, 4);
    public final Command profilesCommand = new Command(
            I18n.text(TextId.PROFILES), Command.SCREEN, 5);
    public final Command settingsCommand = new Command(
            I18n.text(TextId.SETTINGS), Command.SCREEN, 6);
    public final Command searchSettingsCommand = new Command(
            I18n.text(TextId.SEARCH_SETTINGS), Command.SCREEN, 7);
    public final Command limitsCommand = new Command(
            I18n.text(TextId.LIMITS), Command.SCREEN, 8);
    public final Command languageCommand = new Command(
            I18n.text(TextId.LANGUAGE), Command.SCREEN, 9);
    public final Command thinkingCommand = new Command(
            I18n.text(TextId.REASONING), Command.SCREEN, 10);
    public final Command clearCommand = new Command(
            I18n.text(TextId.CLEAR), Command.SCREEN, 11);
    public final Command stopCommand = new Command(
            I18n.text(TextId.STOP), Command.STOP, 1);
    public final Command exitCommand = new Command(
            I18n.text(TextId.EXIT), Command.EXIT, 9);

    private static final int COLOR_BACKGROUND = 0xf3f5fa;
    private static final int COLOR_HEADER = 0x4b3f8f;
    private static final int COLOR_USER = 0x6554c0;
    private static final int COLOR_ASSISTANT = 0xffffff;
    private static final int COLOR_TEXT = 0x202433;
    private static final int COLOR_MUTED = 0x73798c;
    private static final int COLOR_REASONING = 0xeeeafd;
    private static final int COLOR_ERROR = 0xb42318;
    private static final int COLOR_TOOLBAR = 0x342d69;
    private static final int COLOR_TOOLBAR_PRESSED = 0x6554c0;
    private static final int REPAINT_INTERVAL_MS = 100;
    private static final int DRAG_THRESHOLD = 4;
    private static final int MORE_ITEM_COUNT = 10;

    private Vector messages;
    private ProviderProfile profile;
    private final CommandListener commandListener;
    private final boolean touchEnabled;
    private final Vector layouts = new Vector();
    private int scroll;
    private int maximumScroll;
    private boolean scrollToBottom = true;
    private boolean showReasoning;
    private volatile boolean busy;
    private boolean repaintDirty;
    private boolean repaintWorkerRunning;
    private boolean moreMenuOpen;
    private int pointerStartX;
    private int pointerStartY;
    private int pointerStartScroll;
    private boolean pointerCanScroll;
    private boolean pointerDragging;

    public ChatCanvas(Vector initialMessages, ProviderProfile initialProfile,
            CommandListener listener) {
        messages = initialMessages;
        profile = initialProfile;
        commandListener = listener;
        touchEnabled = hasPointerEvents();
        showReasoning = initialProfile != null && initialProfile.reasoningExpanded;
        setFullScreenMode(touchEnabled);
        addCommand(conversationsCommand);
        addCommand(messagesCommand);
        addCommand(profilesCommand);
        addCommand(settingsCommand);
        addCommand(searchSettingsCommand);
        addCommand(limitsCommand);
        addCommand(languageCommand);
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
        moreMenuOpen = false;
        layouts.removeAllElements();
        refreshSendCommands();
        repaint();
    }

    public void setProfile(ProviderProfile value) {
        profile = value;
        showReasoning = value != null && value.reasoningExpanded;
        moreMenuOpen = false;
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

    protected void sizeChanged(int width, int height) {
        boolean wasAtBottom = scroll >= maximumScroll;
        layouts.removeAllElements();
        maximumScroll = 0;
        if (wasAtBottom) scrollToBottom = true;
        repaint();
    }

    protected void keyPressed(int keyCode) {
        if (moreMenuOpen) {
            moreMenuOpen = false;
            repaint();
            return;
        }
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

    protected void pointerPressed(int x, int y) {
        if (!touchEnabled) return;
        pointerStartX = x;
        pointerStartY = y;
        pointerStartScroll = scroll;
        pointerDragging = false;
        pointerCanScroll = !moreMenuOpen && y >= contentTop() && y < contentBottom();
    }

    protected void pointerDragged(int x, int y) {
        if (!touchEnabled || !pointerCanScroll) return;
        int distance = y - pointerStartY;
        if (!pointerDragging && absolute(distance) < DRAG_THRESHOLD) return;
        pointerDragging = true;
        scroll = pointerStartScroll - distance;
        clampScroll();
        scrollToBottom = false;
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        if (!touchEnabled) return;
        if (pointerDragging) {
            pointerDragging = false;
            pointerCanScroll = false;
            return;
        }
        pointerCanScroll = false;

        if (moreMenuOpen) {
            int row = moreMenuRowAt(x, y);
            if (row >= 0 && row == moreMenuRowAt(pointerStartX, pointerStartY)) {
                moreMenuOpen = false;
                repaint();
                fireCommand(moreCommand(row));
                return;
            }
            if (toolbarButtonAt(x, y) == 2
                    && toolbarButtonAt(pointerStartX, pointerStartY) == 2) {
                moreMenuOpen = false;
                repaint();
                return;
            }
            moreMenuOpen = false;
            repaint();
            return;
        }

        int button = toolbarButtonAt(x, y);
        if (button != toolbarButtonAt(pointerStartX, pointerStartY)) return;
        if (button == 0) {
            fireCommand(busy ? stopCommand : composeCommand);
        } else if (button == 1) {
            fireCommand(profile != null && profile.multimodal
                    ? imageCommand : profilesCommand);
        } else if (button == 2) {
            moreMenuOpen = true;
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
        int toolbarHeight = touchEnabled ? normal.getHeight() + 14 : 0;
        int statusY = height - toolbarHeight - statusHeight;
        int viewHeight = statusY - headerHeight;
        if (viewHeight < 1) viewHeight = 1;

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
                    ? I18n.text(TextId.EMPTY_CHAT_WITH_IMAGE)
                    : I18n.text(TextId.EMPTY_CHAT);
            empty = ellipsize(empty, normal, width - 16);
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
        graphics.fillRect(0, statusY, width, statusHeight);
        graphics.setFont(normal);
        graphics.setColor(COLOR_MUTED);
        graphics.drawString(ellipsize(statusText(), normal, width - 12), 6, statusY + 3,
                Graphics.TOP | Graphics.LEFT);
        if (touchEnabled) drawTouchToolbar(graphics, normal, width, height, toolbarHeight);
        if (moreMenuOpen) drawMoreMenu(graphics, normal, width, height, toolbarHeight);
    }

    private void drawHeader(Graphics graphics, int width, int height, Font font) {
        graphics.setColor(COLOR_HEADER);
        graphics.fillRect(0, 0, width, height);
        graphics.setFont(font);
        graphics.setColor(0xffffff);
        String title = profile == null ? "J2ME LLM" : I18n.profileName(profile);
        if (profile != null && profile.model != null && profile.model.length() > 0) {
            title += " · " + profile.model;
        }
        title = ellipsize(title, font, width - 34);
        graphics.drawString(title, 9, 7, Graphics.TOP | Graphics.LEFT);
        graphics.setColor(busy ? 0xffd166 : 0x72e0a8);
        graphics.fillArc(width - 17, 11, 7, 7, 0, 360);
    }

    private String statusText() {
        if (busy) return I18n.text(TextId.RECEIVING_RESPONSE);
        String mode = I18n.text(TextId.AUTO);
        if (profile != null && (profile.thinkingMode == ProviderProfile.THINKING_ON
                || ProviderPresets.isKimiAlwaysThinking(profile))) {
            mode = I18n.text(TextId.ON);
        } else if (profile != null && profile.thinkingMode == ProviderProfile.THINKING_OFF) {
            mode = I18n.text(TextId.OFF);
        }
        return I18n.text(TextId.REQUEST_REASONING_PREFIX) + mode
                + I18n.text(TextId.CHAIN_PREFIX)
                + I18n.text(showReasoning ? TextId.EXPANDED : TextId.COLLAPSED);
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
        graphics.drawString(user ? I18n.text(TextId.YOU) : "AI",
                textX, cursorY, Graphics.TOP | Graphics.LEFT);
        cursorY += bold.getHeight();

        if (layout.hasReasoning) {
            graphics.setColor(user ? 0x8172d5 : COLOR_REASONING);
            int reasonHeight = normal.getHeight() + 3;
            if (layout.expanded) reasonHeight += layout.reasonLines.count * normal.getHeight() + 4;
            graphics.fillRoundRect(textX, cursorY + 2, textWidth, reasonHeight, 8, 8);
            graphics.setFont(normal);
            graphics.setColor(user ? 0xffffff : COLOR_HEADER);
            graphics.drawString(layout.expanded
                            ? I18n.text(TextId.THINKING)
                            : I18n.text(TextId.THINKING_COLLAPSED),
                    textX + 4,
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

        if (layout.sourceLabel.length() > 0) {
            graphics.setColor(user ? 0xded8ff : COLOR_MUTED);
            graphics.drawString(layout.sourceLabel, textX, cursorY,
                    Graphics.TOP | Graphics.LEFT);
            cursorY += normal.getHeight() + 2;
        }

        if (message.hasMedia()) {
            graphics.setColor(user ? 0xded8ff : COLOR_MUTED);
            String label = message.getImageName().length() > 0
                    ? "▣ " + message.getImageName()
                    : "▣ " + I18n.text(TextId.MODEL_IMAGE);
            label = ellipsize(label, normal, textWidth);
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

    private void drawTouchToolbar(Graphics graphics, Font font, int width, int height,
            int toolbarHeight) {
        int top = height - toolbarHeight;
        String first = I18n.text(busy ? TextId.STOP : TextId.COMPOSE);
        String second = I18n.text(profile != null && profile.multimodal
                ? TextId.IMAGE : TextId.PROFILES);
        int i;
        for (i = 0; i < 3; i++) {
            int left = (width * i) / 3;
            int right = (width * (i + 1)) / 3;
            graphics.setColor(i == 2 && moreMenuOpen
                    ? COLOR_TOOLBAR_PRESSED : COLOR_TOOLBAR);
            graphics.fillRect(left, top, right - left, toolbarHeight);
            if (i > 0) {
                graphics.setColor(0x746ba8);
                graphics.drawLine(left, top + 4, left, height - 5);
            }
            graphics.setFont(font);
            graphics.setColor(0xffffff);
            String label = i == 0 ? first
                    : (i == 1 ? second : I18n.text(TextId.MORE));
            label = ellipsize(label, font, right - left - 8);
            graphics.drawString(label, left + (right - left) / 2,
                    top + (toolbarHeight - font.getHeight()) / 2,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    private void drawMoreMenu(Graphics graphics, Font font, int width, int height,
            int toolbarHeight) {
        int rowHeight = font.getHeight() + 8;
        int left = width / 5;
        int right = width - 4;
        int bottom = height - toolbarHeight;
        int top = bottom - rowHeight * MORE_ITEM_COUNT;
        graphics.setColor(0xffffff);
        graphics.fillRect(left, top, right - left, bottom - top);
        graphics.setFont(font);
        int i;
        for (i = 0; i < MORE_ITEM_COUNT; i++) {
            int y = top + i * rowHeight;
            graphics.setColor(0xd7dbea);
            graphics.drawLine(left, y, right, y);
            graphics.setColor(i == MORE_ITEM_COUNT - 1 ? COLOR_ERROR : COLOR_TEXT);
            graphics.drawString(ellipsize(moreLabel(i), font, right - left - 16),
                    left + 8, y + 4, Graphics.TOP | Graphics.LEFT);
        }
        graphics.setColor(COLOR_HEADER);
        graphics.drawRect(left, top, right - left, bottom - top);
    }

    private int toolbarButtonAt(int x, int y) {
        if (!touchEnabled) return -1;
        int height = getHeight();
        int toolbarHeight = Font.getFont(
                Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL).getHeight() + 14;
        if (x < 0 || x >= getWidth() || y < height - toolbarHeight || y >= height) {
            return -1;
        }
        int button = (x * 3) / getWidth();
        return button > 2 ? 2 : button;
    }

    private int moreMenuRowAt(int x, int y) {
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int toolbarHeight = font.getHeight() + 14;
        int rowHeight = font.getHeight() + 8;
        int left = getWidth() / 5;
        int bottom = getHeight() - toolbarHeight;
        int top = bottom - rowHeight * MORE_ITEM_COUNT;
        if (x < left || x >= getWidth() - 4 || y < top || y >= bottom) return -1;
        int row = (y - top) / rowHeight;
        return row >= MORE_ITEM_COUNT ? -1 : row;
    }

    private Command moreCommand(int row) {
        switch (row) {
            case 0: return conversationsCommand;
            case 1: return messagesCommand;
            case 2: return profilesCommand;
            case 3: return settingsCommand;
            case 4: return searchSettingsCommand;
            case 5: return limitsCommand;
            case 6: return languageCommand;
            case 7: return thinkingCommand;
            case 8: return clearCommand;
            default: return exitCommand;
        }
    }

    private String moreLabel(int row) {
        switch (row) {
            case 0: return I18n.text(TextId.CONVERSATIONS);
            case 1: return I18n.text(TextId.MESSAGE_LIST);
            case 2: return I18n.text(TextId.PROFILES);
            case 3: return I18n.text(TextId.SETTINGS);
            case 4: return I18n.text(TextId.SEARCH_SETTINGS);
            case 5: return I18n.text(TextId.LIMITS);
            case 6: return I18n.text(TextId.LANGUAGE);
            case 7: return I18n.text(TextId.REASONING);
            case 8: return I18n.text(TextId.CLEAR);
            default: return I18n.text(TextId.EXIT);
        }
    }

    private void fireCommand(Command command) {
        if (commandListener != null && command != null) {
            commandListener.commandAction(command, this);
        }
    }

    private int contentTop() {
        Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        return bold.getHeight() + 14;
    }

    private int contentBottom() {
        Font normal = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int bottom = getHeight() - normal.getHeight() - 6;
        if (touchEnabled) bottom -= normal.getHeight() + 14;
        return bottom;
    }

    private void clampScroll() {
        if (scroll < 0) scroll = 0;
        if (scroll > maximumScroll) scroll = maximumScroll;
    }

    private static int absolute(int value) {
        return value < 0 ? -value : value;
    }

    private static String ellipsize(String value, Font font, int maximumWidth) {
        if (value == null) return "";
        if (maximumWidth <= 0) return "";
        if (font.stringWidth(value) <= maximumWidth) return value;
        String ellipsis = "…";
        int ellipsisWidth = font.stringWidth(ellipsis);
        if (ellipsisWidth > maximumWidth) return "";
        int length = value.length();
        while (length > 0
                && font.substringWidth(value, 0, length) + ellipsisWidth > maximumWidth) {
            length--;
        }
        return value.substring(0, length) + ellipsis;
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
        final String sourceLabel;
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
            if (valueContent.length() == 0 && value.pending) {
                valueContent = I18n.text(TextId.THINKING_PENDING);
            }
            content = valueContent;
            hasReasoning = value.hasReasoning();
            reasoning = showReasoning && hasReasoning ? value.getReasoning() : "";
            status = value.getImageStatus();
            sourceLabel = value.getSearchBundle() == null ? ""
                    : I18n.text(TextId.SEARCH_SOURCES) + ": "
                    + value.getSearchBundle().results.size();
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
            if (sourceLabel.length() > 0) measured += normal.getHeight() + 2;
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
