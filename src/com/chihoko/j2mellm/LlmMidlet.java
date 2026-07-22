


package com.chihoko.j2mellm;

import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ImageAttachment;
import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.net.ChatListener;
import com.chihoko.j2mellm.net.ModelCatalogClient;
import com.chihoko.j2mellm.net.ModelCatalogListener;
import com.chihoko.j2mellm.net.OpenAiChatClient;
import com.chihoko.j2mellm.provision.ProvisioningFileController;
import com.chihoko.j2mellm.provision.ProvisioningMapper;
import com.chihoko.j2mellm.provision.ProvisioningPackage;
import com.chihoko.j2mellm.store.ProfileConversationStore;
import com.chihoko.j2mellm.store.ProfileStore;
import com.chihoko.j2mellm.ui.ChatCanvas;
import com.chihoko.j2mellm.ui.ConfigPickListener;
import com.chihoko.j2mellm.ui.ConfigPickerController;
import com.chihoko.j2mellm.ui.ImageLoadListener;
import com.chihoko.j2mellm.ui.ImageLoader;
import com.chihoko.j2mellm.ui.ImagePickListener;
import com.chihoko.j2mellm.ui.ImagePickerController;
import com.chihoko.j2mellm.ui.ImageScaler;
import com.chihoko.j2mellm.ui.ModelListScreen;
import com.chihoko.j2mellm.ui.ProfileListScreen;
import com.chihoko.j2mellm.ui.SettingsForm;
import com.chihoko.j2mellm.util.ImageDimensions;
import com.chihoko.j2mellm.util.ImageReferenceParser;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

public final class LlmMidlet extends MIDlet implements CommandListener {
    private static final int MAX_CONVERSATION_CHARS = 49152;
    private static final int MAX_IN_MEMORY_MESSAGES = 32;

    private final ProfileStore profileStore = new ProfileStore();
    private final OpenAiChatClient client = new OpenAiChatClient();
    private final ModelCatalogClient modelClient = new ModelCatalogClient();
    private final Command sendCommand = new Command("发送", Command.OK, 1);
    private final Command cancelEditCommand = new Command("返回", Command.BACK, 2);
    private final Command deleteImportedCommand = new Command("删除配置包", Command.OK, 1);
    private final Command keepImportedCommand = new Command("保留文件", Command.BACK, 2);

    private Display display;
    private ProfileState profileState;
    private ProviderProfile profile;
    private ProfileConversationStore conversationStore;
    private Vector messages;
    private ChatCanvas chatCanvas;
    private SettingsForm settingsForm;
    private ProfileListScreen profileList;
    private ModelListScreen modelList;
    private TextBox editor;
    private Displayable settingsBack;
    private Displayable modelsBack;
    private boolean modelsReturnToSettings;
    private ChatMessage activeAssistant;
    private ImageAttachment pendingAttachment;
    private Alert importResultAlert;
    private String importedFileUrl;
    private int modelRequestGeneration;
    private boolean started;

    protected void startApp() {
        if (started) return;
        started = true;
        display = Display.getDisplay(this);
        profileState = profileStore.load();
        profile = profileState.getActiveProfile();
        if (profile == null) throw new IllegalStateException("没有可用模型档案");
        loadConversation(profile.id);
        chatCanvas = new ChatCanvas(messages, profile, this);

        Displayable target;
        if (profile.isReady()) target = chatCanvas;
        else {
            profileList = new ProfileListScreen(profileState, this);
            target = profileList;
        }
        String notice = startupNotice();
        if (notice.length() > 0) showAlert("v0.2 数据检查", notice, AlertType.INFO, target, 5000);
        else display.setCurrent(target);
    }

    protected void pauseApp() {
        saveConversationQuietly();
        saveProfilesQuietly();
        if (chatCanvas != null) chatCanvas.clearLayoutCache();
    }

    protected void destroyApp(boolean unconditional) {
        client.cancel();
        modelClient.cancel();
        saveConversationQuietly();
        saveProfilesQuietly();
    }

    public void commandAction(Command command, Displayable source) {
        if (source == chatCanvas) handleChatCommand(command);
        else if (source == editor) handleEditorCommand(command);
        else if (source == settingsForm) handleSettingsCommand(command);
        else if (source == profileList) handleProfileListCommand(command);
        else if (source == modelList) handleModelListCommand(command);
        else if (source == importResultAlert) handleImportResultCommand(command);
    }

    private void handleChatCommand(Command command) {
        if (command == chatCanvas.composeCommand) {
            if (!client.isRunning()) {
                pendingAttachment = null;
                openEditor();
            }
        } else if (command == chatCanvas.imageCommand) {
            if (!client.isRunning()) openImagePicker();
        } else if (command == chatCanvas.profilesCommand) {
            if (ensureIdle()) openProfileList();
        } else if (command == chatCanvas.settingsCommand) {
            if (ensureIdle()) openSettings(chatCanvas);
        } else if (command == chatCanvas.thinkingCommand) {
            chatCanvas.toggleReasoning();
            saveProfilesQuietly();
        } else if (command == chatCanvas.clearCommand) {
            client.cancel();
            releaseMessages(messages);
            messages.removeAllElements();
            activeAssistant = null;
            pendingAttachment = null;
            chatCanvas.setBusy(false);
            conversationStore.clear();
            chatCanvas.contentChanged();
        } else if (command == chatCanvas.stopCommand) {
            client.cancel();
            finishCancelled();
        } else if (command == chatCanvas.exitCommand) {
            destroyApp(true);
            notifyDestroyed();
        }
    }

    private void handleEditorCommand(Command command) {
        if (command == sendCommand) {
            String text = editor.getString().trim();
            if (text.length() > 0 || pendingAttachment != null) {
                if (text.length() == 0) text = "请描述这张图片。";
                send(text, pendingAttachment);
                pendingAttachment = null;
            }
        } else if (command == cancelEditCommand) {
            pendingAttachment = null;
            display.setCurrent(chatCanvas);
        }
    }

    private void handleSettingsCommand(Command command) {
        if (command == settingsForm.saveCommand) {
            persistSettings(true, true);
        } else if (command == settingsForm.modelsCommand) {
            if (persistSettings(false, false)) openModels(settingsBack, true);
        } else if (command == settingsForm.backCommand) {
            display.setCurrent(settingsBack == null ? chatCanvas : settingsBack);
        }
    }

    private void handleProfileListCommand(Command command) {
        if (command == List.SELECT_COMMAND) {
            if (activateSelectedProfile()) {
                if (profile.isReady()) display.setCurrent(chatCanvas);
                else openSettings(profileList);
            }
        } else if (command == profileList.settingsCommand) {
            if (activateSelectedProfile()) openSettings(profileList);
        } else if (command == profileList.modelsCommand) {
            if (activateSelectedProfile()) openModels(profileList, false);
        } else if (command == profileList.importCommand) {
            openConfigPicker();
        } else if (command == profileList.exportCommand) {
            exportConfiguration();
        } else if (command == profileList.backCommand) {
            display.setCurrent(chatCanvas);
        }
    }

    private void handleModelListCommand(Command command) {
        if (command == List.SELECT_COMMAND) {
            String selected = modelList.selectedModel();
            if (selected != null) selectModel(selected);
        } else if (command == modelList.refreshCommand) {
            fetchModels();
        } else if (command == modelList.backCommand) {
            returnFromModels();
        }
    }

    private void handleImportResultCommand(Command command) {
        if (command == deleteImportedCommand) {
            final String url = importedFileUrl;
            importResultAlert = null;
            importedFileUrl = null;
            deleteImportedFile(url);
        } else if (command == keepImportedCommand) {
            importResultAlert = null;
            importedFileUrl = null;
            openProfileList();
        }
    }

    private void openEditor() {
        if (client.isRunning()) return;
        String title = pendingAttachment == null ? "发送消息" : "图片：" + pendingAttachment.name;
        editor = new TextBox(title, "", 4096, TextField.ANY);
        editor.addCommand(sendCommand);
        editor.addCommand(cancelEditCommand);
        editor.setCommandListener(this);
        display.setCurrent(editor);
    }

    private void openImagePicker() {
        if (!profile.multimodal) {
            showAlert("多模态已关闭", "请先在当前档案设置中开启多模态。",
                    AlertType.WARNING, chatCanvas, 2500);
            return;
        }
        try {
            Object instance = Class.forName("com.chihoko.j2mellm.ui.ImagePicker").newInstance();
            ImagePickerController picker = (ImagePickerController) instance;
            picker.open(display, chatCanvas, new ImagePickListener() {
                public void onImagePicked(ImageAttachment attachment) {
                    pendingAttachment = attachment;
                    openEditor();
                }

                public void onImagePickError(String message) {
                    showAlert("图片选择失败", message, AlertType.ERROR, chatCanvas, 3500);
                }
            });
        } catch (Throwable failure) {
            showAlert("不支持文件选择", "此手机需要 JSR-75 FileConnection。",
                    AlertType.WARNING, chatCanvas, 3500);
        }
    }

    private void openSettings(Displayable back) {
        settingsBack = back;
        settingsForm = new SettingsForm(profile, this);
        display.setCurrent(settingsForm);
    }

    private boolean persistSettings(boolean confirmation, boolean requireModel) {
        ProviderProfile original = profile;
        ProviderProfile edited = original.copy();
        String oldModelsEndpoint = edited.modelsEndpoint;
        settingsForm.copyTo(edited);
        if (!startsWithHttp(edited.endpoint)) {
            showAlert("配置错误", "聊天端点必须以 http:// 或 https:// 开头。",
                    AlertType.ERROR, settingsForm, 3000);
            return false;
        }
        if (!startsWithHttp(edited.modelsEndpoint)) {
            showAlert("配置错误", "模型列表端点必须以 http:// 或 https:// 开头。",
                    AlertType.ERROR, settingsForm, 3000);
            return false;
        }
        if (requireModel && edited.model.length() == 0) {
            showAlert("配置错误", "请填写或从模型列表选择模型。",
                    AlertType.ERROR, settingsForm, 3000);
            return false;
        }
        if (!oldModelsEndpoint.equals(edited.modelsEndpoint)) edited.clearModelCache();
        edited.addCachedModel(edited.model);
        profileState.replace(edited);
        profile = edited;
        try {
            profileStore.save(profileState);
        } catch (Exception failure) {
            profileState.replace(original);
            profile = original;
            showAlert("保存失败", message(failure), AlertType.ERROR, settingsForm, 3500);
            return false;
        }
        chatCanvas.setProfile(profile);
        if (profileList != null) profileList.refresh();
        if (confirmation) {
            Displayable next = settingsBack == null ? chatCanvas : settingsBack;
            showAlert("已保存", "当前档案已保存到带恢复副本的 RMS。",
                    AlertType.CONFIRMATION, next, 2500);
        }
        return true;
    }

    private void openProfileList() {
        profileList = new ProfileListScreen(profileState, this);
        display.setCurrent(profileList);
    }

    private boolean activateSelectedProfile() {
        String id = profileList.selectedProfileId();
        return id != null && activateProfile(id);
    }

    private boolean activateProfile(String id) {
        if (profile != null && profile.id.equals(id)) return true;
        if (!ensureIdle()) return false;
        ProviderProfile target = profileState.find(id);
        if (target == null) return false;
        invalidateModelRequest();
        saveConversationQuietly();
        String oldId = profileState.activeProfileId;
        profileState.activeProfileId = id;
        try {
            profileStore.save(profileState);
        } catch (Exception failure) {
            profileState.activeProfileId = oldId;
            showAlert("切换失败", message(failure), AlertType.ERROR, profileList, 3500);
            return false;
        }
        profile = target;
        loadConversation(profile.id);
        chatCanvas.setConversation(messages, profile);
        if (profileList != null) profileList.refresh();
        if (conversationStore.didRecoverFromBackup()) {
            showAlert("历史已恢复", "当前档案的主记录损坏，已从 RMS 恢复副本载入。",
                    AlertType.WARNING, chatCanvas, 4000);
        }
        return true;
    }

    private void openModels(Displayable back, boolean returnToSettings) {
        invalidateModelRequest();
        modelsBack = back;
        modelsReturnToSettings = returnToSettings;
        modelList = new ModelListScreen(profile, this);
        display.setCurrent(modelList);
    }

    private void fetchModels() {
        if (modelClient.isRunning()) return;
        final int requestGeneration = ++modelRequestGeneration;
        final ProviderProfile requestedProfile = profile;
        final ModelListScreen requestedScreen = modelList;
        requestedScreen.setTitle("正在获取模型…");
        modelClient.fetch(requestedProfile.modelsEndpoint, requestedProfile.apiKey,
                new ModelCatalogListener() {
            public void onModels(final Vector ids, final boolean truncated) {
                display.callSerially(new Runnable() {
                    public void run() {
                        if (!isCurrentModelRequest(requestGeneration,
                                requestedProfile, requestedScreen)) return;
                        requestedProfile.clearModelCache();
                        if (requestedProfile.model.length() > 0) {
                            requestedProfile.addCachedModel(requestedProfile.model);
                        }
                        int i;
                        for (i = 0; i < ids.size(); i++) {
                            requestedProfile.addCachedModel((String) ids.elementAt(i));
                        }
                        requestedProfile.modelsCachedAt = System.currentTimeMillis();
                        String saveError = null;
                        try {
                            profileStore.save(profileState);
                        } catch (Exception failure) {
                            saveError = message(failure);
                        }
                        requestedScreen.setTitle("选择模型");
                        requestedScreen.refresh(requestedProfile);
                        String text = "已获取 " + requestedProfile.cachedModels.size() + " 个模型。";
                        if (truncated) text += " 列表已按 64 项内存上限截断。";
                        if (saveError == null) {
                            showAlert("模型列表已更新", text + " 已保存到 RMS。",
                                    AlertType.CONFIRMATION, requestedScreen, 3000);
                        } else {
                            showAlert("模型已获取但未保存",
                                    text + " 当前会话仍可选择；重启后可能丢失。\n" + saveError,
                                    AlertType.WARNING, requestedScreen, 5000);
                        }
                    }
                });
            }

            public void onError(final String error) {
                display.callSerially(new Runnable() {
                    public void run() {
                        if (!isCurrentModelRequest(requestGeneration,
                                requestedProfile, requestedScreen)) return;
                        requestedScreen.setTitle("选择模型");
                        showAlert("获取失败", error, AlertType.ERROR, requestedScreen, 4000);
                    }
                });
            }
        });
    }

    private boolean isCurrentModelRequest(int generation, ProviderProfile requestedProfile,
            ModelListScreen requestedScreen) {
        return generation == modelRequestGeneration
                && requestedProfile == profile
                && requestedScreen == modelList
                && display.getCurrent() == requestedScreen;
    }

    private void invalidateModelRequest() {
        modelRequestGeneration++;
        modelClient.cancel();
    }

    private void selectModel(String selected) {
        invalidateModelRequest();
        String old = profile.model;
        profile.model = selected;
        profile.addCachedModel(selected);
        try {
            profileStore.save(profileState);
        } catch (Exception failure) {
            profile.model = old;
            showAlert("保存失败", message(failure), AlertType.ERROR, modelList, 3500);
            return;
        }
        chatCanvas.setProfile(profile);
        returnFromModels();
    }

    private void returnFromModels() {
        invalidateModelRequest();
        if (modelsReturnToSettings) openSettings(modelsBack);
        else display.setCurrent(modelsBack == null ? chatCanvas : modelsBack);
    }

    private void openConfigPicker() {
        try {
            Object instance = Class.forName(
                    "com.chihoko.j2mellm.ui.ConfigPackagePicker").newInstance();
            ConfigPickerController picker = (ConfigPickerController) instance;
            picker.open(display, profileList, new ConfigPickListener() {
                public void onConfigPicked(String fileUrl) {
                    importConfiguration(fileUrl);
                }

                public void onConfigPickError(String error) {
                    showAlert("导入失败", error, AlertType.ERROR, profileList, 4000);
                }
            });
        } catch (Throwable failure) {
            showAlert("不支持导入", "此手机需要 JSR-75 FileConnection。",
                    AlertType.WARNING, profileList, 3500);
        }
    }

    private void importConfiguration(final String fileUrl) {
        showProgress("正在导入", "正在校验 .j2cfg 配置包…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    ProvisioningPackage pack = newFileService().importFile(fileUrl);
                    final ProfileState imported = ProvisioningMapper.importProfiles(pack, profileState);
                    display.callSerially(new Runnable() {
                        public void run() { finishImport(fileUrl, imported); }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert("导入失败", message(failure), AlertType.ERROR,
                                    profileList, 5000);
                        }
                    });
                }
            }
        }).start();
    }

    private void finishImport(String fileUrl, ProfileState imported) {
        invalidateModelRequest();
        saveConversationQuietly();
        try {
            profileStore.save(imported);
        } catch (Exception failure) {
            showAlert("导入失败", "RMS 写入失败：" + message(failure), AlertType.ERROR,
                    profileList, 5000);
            return;
        }
        profileState = imported;
        profile = profileState.getActiveProfile();
        loadConversation(profile.id);
        chatCanvas.setConversation(messages, profile);
        profileList = new ProfileListScreen(profileState, this);
        importedFileUrl = fileUrl;
        importResultAlert = new Alert("导入成功",
                "配置档案已写入 RMS。配置包含明文密钥；若不再需要，建议现在删除蓝牙传来的文件。",
                null, AlertType.CONFIRMATION);
        importResultAlert.setTimeout(Alert.FOREVER);
        importResultAlert.addCommand(deleteImportedCommand);
        importResultAlert.addCommand(keepImportedCommand);
        importResultAlert.setCommandListener(this);
        display.setCurrent(importResultAlert);
    }

    private void exportConfiguration() {
        final ProvisioningPackage pack = ProvisioningMapper.exportProfiles(profileState);
        showProgress("正在导出", "正在生成含校验值的 .j2cfg 备份…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    ProvisioningFileController files = newFileService();
                    final String url = files.defaultExportUrl();
                    files.exportFile(url, pack);
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert("导出成功", url + "\n文件含明文密钥，请妥善保管。",
                                    AlertType.CONFIRMATION, profileList, 5000);
                        }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert("导出失败", message(failure), AlertType.ERROR,
                                    profileList, 5000);
                        }
                    });
                }
            }
        }).start();
    }

    private void deleteImportedFile(final String fileUrl) {
        showProgress("正在删除", "正在删除已导入的明文配置包…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    newFileService().deleteFile(fileUrl);
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert("已删除", "配置包已从文件系统删除，档案仍保存在 RMS。",
                                    AlertType.CONFIRMATION, profileList, 3500);
                        }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert("删除失败", message(failure), AlertType.ERROR,
                                    profileList, 4500);
                        }
                    });
                }
            }
        }).start();
    }

    private ProvisioningFileController newFileService() throws Exception {
        Object value = Class.forName(
                "com.chihoko.j2mellm.provision.ProvisioningFileService").newInstance();
        return (ProvisioningFileController) value;
    }

    private void send(String text, ImageAttachment attachment) {
        if (!profile.isReady()) {
            showAlert("尚未配置", "请先填写端点和模型名称。",
                    AlertType.WARNING, chatCanvas, 3000);
            return;
        }
        if (attachment != null && !profile.multimodal) attachment = null;
        final ChatMessage user = new ChatMessage(ChatMessage.ROLE_USER, text);
        if (attachment != null) {
            user.setAttachment(attachment);
            try {
                ImageDimensions dimensions = ImageDimensions.parse(attachment.data);
                if (dimensions == null || !dimensions.fitsPixelLimit(65536)) {
                    throw new IOException("图片尺寸不适合本机预览");
                }
                ensurePreviewMemory(attachment.data.length, dimensions.pixelCountOrMaximum());
                Image preview = Image.createImage(attachment.data, 0, attachment.data.length);
                user.setImagePreview(ImageScaler.fit(preview, previewWidth(), 160));
                preview = null;
            } catch (Throwable ignored) {
                user.setImageStatus("图片已附带（本机内存不足或无法预览）");
            }
        }
        final ChatMessage assistant = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "");
        assistant.pending = true;
        messages.addElement(user);
        messages.addElement(assistant);
        pruneConversation();
        activeAssistant = assistant;
        chatCanvas.setBusy(true);
        chatCanvas.contentChanged();
        display.setCurrent(chatCanvas);

        Vector requestHistory = new Vector();
        int i;
        for (i = 0; i < messages.size(); i++) requestHistory.addElement(messages.elementAt(i));
        final ProviderProfile requestProfile = profile.copy();
        client.send(requestProfile, requestHistory, new ChatListener() {
            public void onContent(String value) {
                assistant.appendContent(value);
                chatCanvas.contentChanged();
            }

            public void onReasoning(String value) {
                assistant.appendReasoning(value);
                chatCanvas.contentChanged();
            }

            public void onImage(String source) {
                if (!requestProfile.multimodal) return;
                assistant.setImageSource(source);
                assistant.setImageStatus("正在加载图片…");
                chatCanvas.contentChanged();
            }

            public void onComplete() {
                display.callSerially(new Runnable() {
                    public void run() {
                        assistant.pending = false;
                        String source = assistant.getImageSource();
                        if (requestProfile.multimodal && source.length() == 0) {
                            source = ImageReferenceParser.firstImageSource(assistant.getContent());
                            if (source != null) assistant.setImageSource(source);
                        }
                        if (assistant.getContent().length() == 0) {
                            assistant.appendContent(source == null || source.length() == 0
                                    ? "（模型未返回正文）" : "（图片响应）");
                        } else if (source != null && source.startsWith("data:image/")
                                && assistant.getContent().startsWith(source)) {
                            assistant.replaceContent("（图片响应）");
                        }
                        if (activeAssistant == assistant) activeAssistant = null;
                        pruneConversation();
                        chatCanvas.setBusy(false);
                        chatCanvas.contentChanged();
                        saveConversationQuietly();
                        if (requestProfile.multimodal) loadImage(assistant);
                    }
                });
            }

            public void onError(final String error) {
                display.callSerially(new Runnable() {
                    public void run() {
                        assistant.pending = false;
                        assistant.error = true;
                        if (assistant.getContent().length() == 0) {
                            assistant.appendContent("请求失败：" + error);
                        }
                        if (activeAssistant == assistant) activeAssistant = null;
                        pruneConversation();
                        chatCanvas.setBusy(false);
                        chatCanvas.contentChanged();
                        saveConversationQuietly();
                    }
                });
            }
        });
    }

    private void loadImage(final ChatMessage message) {
        String source = message.getImageSource();
        if (source == null || source.length() == 0) return;
        message.setImageStatus("正在加载图片…");
        new ImageLoader().load(source, previewWidth(), 180, new ImageLoadListener() {
            public void onImageLoaded(final Image image) {
                display.callSerially(new Runnable() {
                    public void run() {
                        message.setImagePreview(image);
                        message.setImageStatus("");
                        message.releaseInlineImageSource();
                        chatCanvas.contentChanged();
                    }
                });
            }

            public void onImageLoadError(final String reason) {
                display.callSerially(new Runnable() {
                    public void run() {
                        String value = reason == null ? "未知错误" : reason;
                        if (value.length() > 80) value = value.substring(0, 80) + "…";
                        message.setImageStatus("图片无法显示：" + value);
                        message.releaseInlineImageSource();
                        chatCanvas.contentChanged();
                    }
                });
            }
        });
    }

    private void finishCancelled() {
        if (activeAssistant != null) {
            activeAssistant.pending = false;
            if (activeAssistant.getContent().length() == 0) activeAssistant.appendContent("（已停止）");
            activeAssistant = null;
        }
        chatCanvas.setBusy(false);
        chatCanvas.contentChanged();
        saveConversationQuietly();
    }

    private void loadConversation(String profileId) {
        conversationStore = new ProfileConversationStore(profileId);
        messages = conversationStore.load();
        pruneConversation();
    }

    private void pruneConversation() {
        if (messages == null) return;
        while ((messages.size() > MAX_IN_MEMORY_MESSAGES
                || conversationCost(messages) > MAX_CONVERSATION_CHARS) && messages.size() > 2) {
            ChatMessage first = (ChatMessage) messages.elementAt(0);
            first.releaseImageData();
            first.releaseMediaPreview();
            messages.removeElementAt(0);
            if (ChatMessage.ROLE_USER.equals(first.role) && messages.size() > 2) {
                ChatMessage second = (ChatMessage) messages.elementAt(0);
                if (ChatMessage.ROLE_ASSISTANT.equals(second.role)) {
                    second.releaseImageData();
                    second.releaseMediaPreview();
                    messages.removeElementAt(0);
                }
            }
        }
        int i;
        for (i = 0; i < messages.size() - 4; i++) {
            ChatMessage old = (ChatMessage) messages.elementAt(i);
            old.releaseImageData();
            old.releaseMediaPreview();
        }
    }

    private int conversationCost(Vector source) {
        int cost = 0;
        int i;
        for (i = 0; i < source.size(); i++) {
            ChatMessage value = (ChatMessage) source.elementAt(i);
            int messageCost = value.getCharacterCost() + 64;
            if (Integer.MAX_VALUE - cost < messageCost) return Integer.MAX_VALUE;
            cost += messageCost;
        }
        return cost;
    }

    private void releaseMessages(Vector source) {
        if (source == null) return;
        int i;
        for (i = 0; i < source.size(); i++) {
            ChatMessage value = (ChatMessage) source.elementAt(i);
            value.releaseImageData();
            value.releaseMediaPreview();
        }
    }

    private void saveConversationQuietly() {
        if (conversationStore == null || messages == null) return;
        try { conversationStore.save(messages); } catch (Exception ignored) { }
    }

    private void saveProfilesQuietly() {
        if (profileState == null) return;
        try { profileStore.save(profileState); } catch (Exception ignored) { }
    }

    private boolean ensureIdle() {
        if (client.isRunning()) {
            showAlert("请求进行中", "请先停止当前请求，再切换档案或设置。",
                    AlertType.WARNING, chatCanvas, 2500);
            return false;
        }
        return true;
    }

    private int previewWidth() {
        int width = (chatCanvas.getWidth() * 82) / 100 - 16;
        return width < 48 ? 48 : width;
    }

    private void ensurePreviewMemory(int compressedBytes, int pixels) throws IOException {
        long free = Runtime.getRuntime().freeMemory();
        long reserve = compressedBytes + pixels * 4L + 131072L;
        if (free > 0 && free < reserve) throw new IOException("可用内存不足");
    }

    private String startupNotice() {
        StringBuffer text = new StringBuffer();
        if (profileState.migratedThisLoad) {
            text.append("已把 v0.1 配置迁移到“自定义（旧配置）”，旧 RMS 保留。\n");
        }
        if (profileState.recoveredFromBackup) {
            text.append("档案主记录损坏，已从 RMS 恢复副本修复。\n");
        }
        if (profileState.storageCorrupt) {
            text.append("档案主副记录均不可读，当前载入安全默认值；请导入 .j2cfg 备份。");
        }
        if (conversationStore.didMigrateLegacy()) text.append("旧聊天记录已迁移。\n");
        if (conversationStore.didRecoverFromBackup()) text.append("聊天记录已从恢复副本载入。");
        return text.toString().trim();
    }

    private boolean startsWithHttp(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null ? failure.toString() : value;
    }

    private void showProgress(String title, String text) {
        Alert alert = new Alert(title, text, null, AlertType.INFO);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    private void showAlert(String title, String text, AlertType type,
            Displayable next, int timeout) {
        Alert alert = new Alert(title, text, null, type);
        alert.setTimeout(timeout);
        display.setCurrent(alert, next);
    }
}



