


package com.chihoko.j2mellm;

import com.chihoko.j2mellm.i18n.I18n;
import com.chihoko.j2mellm.i18n.TextId;
import com.chihoko.j2mellm.model.ChatMessage;
import com.chihoko.j2mellm.model.ConversationMeta;
import com.chihoko.j2mellm.model.ConversationState;
import com.chihoko.j2mellm.model.ImageAttachment;
import com.chihoko.j2mellm.model.ProfileState;
import com.chihoko.j2mellm.model.ProviderProfile;
import com.chihoko.j2mellm.model.ResourceLimits;
import com.chihoko.j2mellm.model.SearchBundle;
import com.chihoko.j2mellm.model.SearchConfig;
import com.chihoko.j2mellm.model.SearchResult;
import com.chihoko.j2mellm.net.ChatListener;
import com.chihoko.j2mellm.net.ModelCatalogClient;
import com.chihoko.j2mellm.net.ModelCatalogListener;
import com.chihoko.j2mellm.net.OpenAiChatClient;
import com.chihoko.j2mellm.net.SearchClient;
import com.chihoko.j2mellm.net.SearchListener;
import com.chihoko.j2mellm.provision.ProvisioningFileController;
import com.chihoko.j2mellm.provision.ProvisioningMapper;
import com.chihoko.j2mellm.provision.ProvisioningPackage;
import com.chihoko.j2mellm.store.ConversationIds;
import com.chihoko.j2mellm.store.ConversationIndexStore;
import com.chihoko.j2mellm.store.ConversationStoreV3;
import com.chihoko.j2mellm.store.ProfileConversationStore;
import com.chihoko.j2mellm.store.ProfileStore;
import com.chihoko.j2mellm.store.LanguageStore;
import com.chihoko.j2mellm.store.ResourceLimitsStore;
import com.chihoko.j2mellm.store.SearchConfigStore;
import com.chihoko.j2mellm.ui.ChatCanvas;
import com.chihoko.j2mellm.ui.ConfigPickListener;
import com.chihoko.j2mellm.ui.ConfigPickerController;
import com.chihoko.j2mellm.ui.ConversationListScreen;
import com.chihoko.j2mellm.ui.ImageLoadListener;
import com.chihoko.j2mellm.ui.ImageLoader;
import com.chihoko.j2mellm.ui.ImagePickListener;
import com.chihoko.j2mellm.ui.ImagePickerController;
import com.chihoko.j2mellm.ui.ImageScaler;
import com.chihoko.j2mellm.ui.LanguageScreen;
import com.chihoko.j2mellm.ui.MessageListScreen;
import com.chihoko.j2mellm.ui.ModelListScreen;
import com.chihoko.j2mellm.ui.ProfileListScreen;
import com.chihoko.j2mellm.ui.ResourceLimitsForm;
import com.chihoko.j2mellm.ui.SearchSettingsForm;
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
    private final ProfileStore profileStore = new ProfileStore();
    private final LanguageStore languageStore = new LanguageStore();
    private final ConversationIndexStore conversationIndexStore =
            new ConversationIndexStore();
    private final ResourceLimitsStore resourceLimitsStore = new ResourceLimitsStore();
    private final SearchConfigStore searchConfigStore = new SearchConfigStore();
    private final OpenAiChatClient client = new OpenAiChatClient();
    private final ModelCatalogClient modelClient = new ModelCatalogClient();
    private final SearchClient searchClient = new SearchClient();
    private Command sendCommand;
    private Command searchSendCommand;
    private Command cancelEditCommand;
    private Command deleteImportedCommand;
    private Command keepImportedCommand;
    private Command confirmDeleteConversationCommand;
    private Command cancelSearchCommand;

    private Display display;
    private ProfileState profileState;
    private ProviderProfile profile;
    private ConversationState conversationState;
    private ConversationMeta conversation;
    private ConversationMeta renamingConversation;
    private ConversationStoreV3 conversationStore;
    private ResourceLimits limits;
    private SearchConfig searchConfig;
    private Vector messages;
    private ChatCanvas chatCanvas;
    private SettingsForm settingsForm;
    private ProfileListScreen profileList;
    private ConversationListScreen conversationList;
    private MessageListScreen messageList;
    private ModelListScreen modelList;
    private LanguageScreen languageScreen;
    private SearchSettingsForm searchSettingsForm;
    private ResourceLimitsForm resourceLimitsForm;
    private TextBox editor;
    private TextBox conversationTitleEditor;
    private int editingMessageIndex = -1;
    private Displayable settingsBack;
    private Displayable modelsBack;
    private boolean modelsReturnToSettings;
    private ChatMessage activeAssistant;
    private ImageAttachment pendingAttachment;
    private Alert importResultAlert;
    private Alert deleteConversationAlert;
    private Alert searchProgressAlert;
    private Displayable searchReturnDisplay;
    private ConversationMeta pendingDeleteConversation;
    private String importedFileUrl;
    private int modelRequestGeneration;
    private boolean started;

    protected void startApp() {
        if (started) return;
        started = true;
        display = Display.getDisplay(this);
        I18n.initialize(languageStore.load(), systemLocale());
        createCommands();
        limits = resourceLimitsStore.load();
        ChatMessage.configureLimits(limits);
        searchConfig = searchConfigStore.load();
        profileState = profileStore.load();
        profile = profileState.getActiveProfile();
        if (profile == null) {
            throw new IllegalStateException(I18n.text(TextId.NO_AVAILABLE_PROFILES));
        }
        initializeConversations();
        loadConversation(conversationState.getActive());
        chatCanvas = new ChatCanvas(messages, profile, this);

        Displayable target;
        if (profile.isReady()) target = chatCanvas;
        else {
            profileList = new ProfileListScreen(profileState, this);
            target = profileList;
        }
        String notice = startupNotice();
        if (notice.length() > 0) {
            showAlert(I18n.text(TextId.STARTUP_CHECK), notice, AlertType.INFO, target, 5000);
        }
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
        searchClient.cancel();
        saveConversationQuietly();
        saveProfilesQuietly();
    }

    public void commandAction(Command command, Displayable source) {
        if (source == chatCanvas) handleChatCommand(command);
        else if (source == editor) handleEditorCommand(command);
        else if (source == settingsForm) handleSettingsCommand(command);
        else if (source == profileList) handleProfileListCommand(command);
        else if (source == conversationList) handleConversationListCommand(command);
        else if (source == messageList) handleMessageListCommand(command);
        else if (source == conversationTitleEditor) handleConversationTitleCommand(command);
        else if (source == modelList) handleModelListCommand(command);
        else if (source == languageScreen) handleLanguageCommand(command);
        else if (source == searchSettingsForm) handleSearchSettingsCommand(command);
        else if (source == resourceLimitsForm) handleResourceLimitsCommand(command);
        else if (source == importResultAlert) handleImportResultCommand(command);
        else if (source == deleteConversationAlert) handleDeleteConversationAlert(command);
        else if (source == searchProgressAlert) handleSearchProgressCommand(command);
    }

    private void handleChatCommand(Command command) {
        if (command == chatCanvas.composeCommand) {
            if (!client.isRunning()) {
                pendingAttachment = null;
                openEditor();
            }
        } else if (command == chatCanvas.imageCommand) {
            if (!client.isRunning()) openImagePicker();
        } else if (command == chatCanvas.conversationsCommand) {
            if (ensureIdle()) openConversationList();
        } else if (command == chatCanvas.messagesCommand) {
            if (ensureIdle()) openMessageList();
        } else if (command == chatCanvas.profilesCommand) {
            if (ensureIdle()) openProfileList();
        } else if (command == chatCanvas.settingsCommand) {
            if (ensureIdle()) openSettings(chatCanvas);
        } else if (command == chatCanvas.searchSettingsCommand) {
            if (ensureIdle()) openSearchSettings();
        } else if (command == chatCanvas.limitsCommand) {
            if (ensureIdle()) openResourceLimits();
        } else if (command == chatCanvas.languageCommand) {
            if (ensureIdle()) openLanguage();
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
            updateConversationMetadata();
            saveConversationIndexQuietly();
            chatCanvas.contentChanged();
        } else if (command == chatCanvas.stopCommand) {
            client.cancel();
            searchClient.cancel();
            finishCancelled();
        } else if (command == chatCanvas.exitCommand) {
            destroyApp(true);
            notifyDestroyed();
        }
    }

    private void handleEditorCommand(Command command) {
        if (command == sendCommand || command == searchSendCommand) {
            String text = editor.getString().trim();
            if (text.length() > 0 || pendingAttachment != null) {
                if (text.length() == 0) text = I18n.text(TextId.DEFAULT_IMAGE_PROMPT);
                if (command == searchSendCommand) {
                    searchAndSend(text, pendingAttachment, editingMessageIndex);
                } else {
                    if (!ensureProfileReady()) return;
                    if (editingMessageIndex >= 0) {
                        truncateConversation(editingMessageIndex);
                        editingMessageIndex = -1;
                    }
                    send(text, pendingAttachment, null);
                    pendingAttachment = null;
                }
            }
        } else if (command == cancelEditCommand) {
            pendingAttachment = null;
            editingMessageIndex = -1;
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

    private void handleConversationListCommand(Command command) {
        if (command == List.SELECT_COMMAND) {
            String selected = conversationList.selectedConversationId();
            if (selected == null) createConversation();
            else switchConversation(selected);
        } else if (command == conversationList.newCommand) {
            createConversation();
        } else if (command == conversationList.renameCommand) {
            openConversationTitleEditor();
        } else if (command == conversationList.deleteCommand) {
            deleteSelectedConversation();
        } else if (command == conversationList.backCommand) {
            display.setCurrent(chatCanvas);
        }
    }

    private void handleMessageListCommand(Command command) {
        if (command == messageList.backCommand) {
            display.setCurrent(chatCanvas);
            return;
        }
        ChatMessage selected = messageList.selectedMessage();
        int index = messageList.selectedMessageIndex();
        if (selected == null || index < 0) return;
        boolean edit = command == messageList.editCommand
                || (command == List.SELECT_COMMAND
                && ChatMessage.ROLE_USER.equals(selected.role));
        if (edit) {
            if (ChatMessage.ROLE_USER.equals(selected.role)) openMessageEditor(index);
            return;
        }
        if (command == messageList.regenerateCommand || command == List.SELECT_COMMAND) {
            regenerateFrom(index);
        }
    }

    private void handleConversationTitleCommand(Command command) {
        if (command == sendCommand) {
            String value = conversationTitleEditor.getString().trim();
            if (value.length() == 0) value = I18n.text(TextId.NEW_CHAT_TITLE);
            if (value.length() > ConversationMeta.MAX_TITLE_CHARS) {
                value = value.substring(0, ConversationMeta.MAX_TITLE_CHARS);
            }
            if (renamingConversation == null) return;
            renamingConversation.title = value;
            renamingConversation.updatedAt = System.currentTimeMillis();
            renamingConversation = null;
            saveConversationIndexQuietly();
            if (conversationList != null) conversationList.refresh();
            display.setCurrent(conversationList);
        } else if (command == cancelEditCommand) {
            renamingConversation = null;
            display.setCurrent(conversationList);
        }
    }

    private void handleDeleteConversationAlert(Command command) {
        if (command == confirmDeleteConversationCommand) {
            ConversationMeta target = pendingDeleteConversation;
            pendingDeleteConversation = null;
            deleteConversationAlert = null;
            if (target != null) deleteConversation(target);
        } else if (command == cancelEditCommand) {
            pendingDeleteConversation = null;
            deleteConversationAlert = null;
            display.setCurrent(conversationList);
        }
    }

    private void handleSearchProgressCommand(Command command) {
        if (command != cancelSearchCommand) return;
        searchClient.cancel();
        Displayable next = searchReturnDisplay == null ? chatCanvas : searchReturnDisplay;
        searchProgressAlert = null;
        searchReturnDisplay = null;
        display.setCurrent(next);
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

    private void handleLanguageCommand(Command command) {
        if (command == languageScreen.applyCommand) {
            int preference = languageScreen.selectedPreference();
            try {
                languageStore.save(preference);
            } catch (Exception failure) {
                showAlert(I18n.text(TextId.LANGUAGE_SAVE_FAILED), message(failure),
                        AlertType.ERROR, languageScreen, 3500);
                return;
            }
            I18n.initialize(preference, systemLocale());
            createCommands();
            chatCanvas = new ChatCanvas(messages, profile, this);
            settingsForm = null;
            profileList = null;
            modelList = null;
            languageScreen = null;
            searchSettingsForm = null;
            resourceLimitsForm = null;
            display.setCurrent(chatCanvas);
        } else if (command == languageScreen.backCommand) {
            display.setCurrent(chatCanvas);
        }
    }

    private void handleSearchSettingsCommand(Command command) {
        if (command == searchSettingsForm.backCommand) {
            display.setCurrent(chatCanvas);
            return;
        }
        SearchConfig edited = searchConfig.copy();
        searchSettingsForm.copyTo(edited);
        if (!startsWithHttp(edited.endpoint)) {
            showAlert(I18n.text(TextId.CONFIG_ERROR),
                    I18n.text(TextId.SEARCH_BAD_CONFIG),
                    AlertType.ERROR, searchSettingsForm, 3000);
            return;
        }
        if (edited.requiresKey() && edited.apiKey.length() == 0) {
            showAlert(I18n.text(TextId.CONFIG_ERROR),
                    I18n.text(TextId.SEARCH_API_KEY),
                    AlertType.ERROR, searchSettingsForm, 3000);
            return;
        }
        if (command == searchSettingsForm.testCommand) {
            testSearch(edited);
            return;
        }
        if (command == searchSettingsForm.saveCommand) {
            try {
                searchConfigStore.save(edited);
                searchConfig = edited;
            } catch (Exception failure) {
                showAlert(I18n.text(TextId.SAVE_FAILED), message(failure),
                        AlertType.ERROR, searchSettingsForm, 3500);
                return;
            }
            showAlert(I18n.text(TextId.SAVED),
                    I18n.text(TextId.SEARCH_SETTINGS_SAVED),
                    AlertType.CONFIRMATION, chatCanvas, 2500);
        }
    }

    private void handleResourceLimitsCommand(Command command) {
        if (command == resourceLimitsForm.backCommand) {
            display.setCurrent(chatCanvas);
            return;
        }
        if (command == resourceLimitsForm.saveCommand) {
            ResourceLimits edited = resourceLimitsForm.read();
            try {
                resourceLimitsStore.save(edited);
                limits = edited;
                ChatMessage.configureLimits(limits);
                pruneConversation();
                saveConversationQuietly();
            } catch (Exception failure) {
                showAlert(I18n.text(TextId.SAVE_FAILED), message(failure),
                        AlertType.ERROR, resourceLimitsForm, 3500);
                return;
            }
            showAlert(I18n.text(TextId.SAVED),
                    edited.mode == ResourceLimits.MODE_CUSTOM
                            ? I18n.text(TextId.CUSTOM_LIMIT_WARNING)
                            : I18n.text(TextId.RECOMMENDED),
                    AlertType.CONFIRMATION, chatCanvas, 3000);
        }
    }

    private void openLanguage() {
        languageScreen = new LanguageScreen(this);
        display.setCurrent(languageScreen);
    }

    private void openSearchSettings() {
        searchSettingsForm = new SearchSettingsForm(searchConfig, this);
        display.setCurrent(searchSettingsForm);
    }

    private void openResourceLimits() {
        resourceLimitsForm = new ResourceLimitsForm(limits, this);
        display.setCurrent(resourceLimitsForm);
    }

    private void openConversationList() {
        saveConversationQuietly();
        conversationList = new ConversationListScreen(conversationState, this);
        display.setCurrent(conversationList);
    }

    private void openMessageList() {
        messageList = new MessageListScreen(messages, this);
        display.setCurrent(messageList);
    }

    private void openEditor() {
        if (client.isRunning()) return;
        editingMessageIndex = -1;
        String title = pendingAttachment == null
                ? I18n.text(TextId.SEND_MESSAGE)
                : I18n.text(TextId.IMAGE_PREFIX) + pendingAttachment.name;
        editor = new TextBox(title, "", editorLimit(), TextField.ANY);
        editor.addCommand(sendCommand);
        if (searchConfig.enabled) editor.addCommand(searchSendCommand);
        editor.addCommand(cancelEditCommand);
        editor.setCommandListener(this);
        display.setCurrent(editor);
    }

    private void openMessageEditor(int index) {
        if (index < 0 || index >= messages.size() || client.isRunning()) return;
        ChatMessage message = (ChatMessage) messages.elementAt(index);
        if (!ChatMessage.ROLE_USER.equals(message.role)) return;
        editingMessageIndex = index;
        byte[] data = message.getImageData();
        pendingAttachment = data == null ? null : new ImageAttachment(
                message.getImageName(), message.getImageMime(), data);
        editor = new TextBox(I18n.text(TextId.EDIT_MESSAGE),
                message.getContent(), editorLimit(), TextField.ANY);
        editor.addCommand(sendCommand);
        if (searchConfig.enabled) editor.addCommand(searchSendCommand);
        editor.addCommand(cancelEditCommand);
        editor.setCommandListener(this);
        display.setCurrent(editor);
    }

    private int editorLimit() {
        int value = limits == null ? 4096 : limits.messageContentChars;
        if (value < 4096) value = 4096;
        if (value > 32767) value = 32767;
        return value;
    }

    private void openConversationTitleEditor() {
        String id = conversationList.selectedConversationId();
        ConversationMeta selected = conversationState.find(id);
        if (selected == null) return;
        renamingConversation = selected;
        String title = selected.title == null ? "" : selected.title;
        conversationTitleEditor = new TextBox(I18n.text(TextId.RENAME_CHAT),
                title, ConversationMeta.MAX_TITLE_CHARS, TextField.ANY);
        conversationTitleEditor.addCommand(sendCommand);
        conversationTitleEditor.addCommand(cancelEditCommand);
        conversationTitleEditor.setCommandListener(this);
        display.setCurrent(conversationTitleEditor);
    }

    private void createConversation() {
        saveConversationQuietly();
        String oldActive = conversationState.activeConversationId;
        ConversationMeta created = new ConversationMeta(
                ConversationIds.next(conversationState), profile.id);
        conversationState.add(created);
        try {
            conversationIndexStore.save(conversationState);
        } catch (Exception failure) {
            conversationState.remove(created.id);
            conversationState.activeConversationId = oldActive;
            showAlert(I18n.text(TextId.SAVE_FAILED), message(failure),
                    AlertType.ERROR, conversationList, 3500);
            return;
        }
        loadConversation(created);
        chatCanvas.setConversation(messages, profile);
        display.setCurrent(chatCanvas);
    }

    private void switchConversation(String id) {
        ConversationMeta target = conversationState.find(id);
        if (target == null) return;
        if (conversation != null && target.id.equals(conversation.id)) {
            display.setCurrent(chatCanvas);
            return;
        }
        saveConversationQuietly();
        conversationState.activeConversationId = target.id;
        ProviderProfile targetProfile = profileState.find(target.profileId);
        if (targetProfile != null) {
            profile = targetProfile;
            profileState.activeProfileId = targetProfile.id;
            saveProfilesQuietly();
        } else {
            target.profileId = profile.id;
        }
        saveConversationIndexQuietly();
        loadConversation(target);
        chatCanvas.setConversation(messages, profile);
        if (conversationStore.didRecoverFromBackup()) {
            showAlert(I18n.text(TextId.HISTORY_RECOVERED),
                    I18n.text(TextId.CONVERSATION_RECOVERED),
                    AlertType.WARNING, chatCanvas, 4000);
        } else {
            display.setCurrent(chatCanvas);
        }
    }

    private void deleteSelectedConversation() {
        String id = conversationList.selectedConversationId();
        ConversationMeta selected = conversationState.find(id);
        if (selected == null) return;
        pendingDeleteConversation = selected;
        deleteConversationAlert = new Alert(I18n.text(TextId.DELETE_CHAT),
                I18n.text(TextId.DELETE_CHAT_CONFIRM), null, AlertType.CONFIRMATION);
        deleteConversationAlert.setTimeout(Alert.FOREVER);
        deleteConversationAlert.addCommand(confirmDeleteConversationCommand);
        deleteConversationAlert.addCommand(cancelEditCommand);
        deleteConversationAlert.setCommandListener(this);
        display.setCurrent(deleteConversationAlert);
    }

    private void deleteConversation(ConversationMeta target) {
        if (conversation != null && target.id.equals(conversation.id)) {
            saveConversationQuietly();
        }
        String oldActive = conversationState.activeConversationId;
        conversationState.remove(target.id);
        ConversationMeta replacement = null;
        if (conversationState.conversations.size() == 0) {
            replacement = new ConversationMeta(
                    ConversationIds.next(conversationState), profile.id);
            conversationState.add(replacement);
        }
        try {
            conversationIndexStore.save(conversationState);
        } catch (Exception failure) {
            if (replacement != null) conversationState.remove(replacement.id);
            conversationState.add(target);
            conversationState.activeConversationId = oldActive;
            showAlert(I18n.text(TextId.DELETE_FAILED), message(failure),
                    AlertType.ERROR, conversationList, 3500);
            return;
        }
        new ConversationStoreV3(target).clear();
        if (conversation != null && target.id.equals(conversation.id)) {
            ConversationMeta next = conversationState.getActive();
            ProviderProfile nextProfile = profileState.find(next.profileId);
            if (nextProfile != null) profile = nextProfile;
            loadConversation(next);
            chatCanvas.setConversation(messages, profile);
        }
        conversationList = new ConversationListScreen(conversationState, this);
        display.setCurrent(conversationList);
    }

    private void regenerateFrom(int selectedIndex) {
        if (!ensureIdle() || !ensureProfileReady()
                || selectedIndex < 0 || selectedIndex >= messages.size()) return;
        int userIndex = selectedIndex;
        while (userIndex >= 0) {
            ChatMessage value = (ChatMessage) messages.elementAt(userIndex);
            if (ChatMessage.ROLE_USER.equals(value.role)) break;
            userIndex--;
        }
        if (userIndex < 0) return;
        ChatMessage user = (ChatMessage) messages.elementAt(userIndex);
        String text = user.getContent();
        byte[] data = user.getImageData();
        ImageAttachment attachment = data == null ? null : new ImageAttachment(
                user.getImageName(), user.getImageMime(), data);
        truncateConversation(userIndex);
        display.setCurrent(chatCanvas);
        send(text, attachment, null);
    }

    private void truncateConversation(int fromIndex) {
        if (fromIndex < 0) fromIndex = 0;
        while (messages.size() > fromIndex) {
            ChatMessage removed = (ChatMessage) messages.elementAt(messages.size() - 1);
            removed.releaseImageData();
            removed.releaseMediaPreview();
            messages.removeElementAt(messages.size() - 1);
        }
        activeAssistant = null;
        chatCanvas.contentChanged();
        saveConversationQuietly();
    }

    private void testSearch(final SearchConfig edited) {
        if (searchClient.isRunning()) return;
        showSearchProgress(searchSettingsForm);
        searchClient.search(edited, "OpenAI", new SearchListener() {
            public void onResults(final SearchBundle bundle) {
                display.callSerially(new Runnable() {
                    public void run() {
                        clearSearchProgress();
                        showAlert(I18n.text(TextId.WEB_SEARCH),
                                I18n.text(TextId.SEARCH_RESULTS) + ": "
                                + bundle.results.size(),
                                AlertType.CONFIRMATION, searchSettingsForm, 3500);
                    }
                });
            }

            public void onError(final String error) {
                display.callSerially(new Runnable() {
                    public void run() {
                        clearSearchProgress();
                        showAlert(I18n.text(TextId.SEARCH_FAILED), I18n.error(error),
                                AlertType.ERROR, searchSettingsForm, 4500);
                    }
                });
            }
        });
    }

    private void searchAndSend(final String text, final ImageAttachment attachment,
            final int editIndex) {
        if (!ensureProfileReady()) return;
        if (!searchConfig.enabled) {
            showAlert(I18n.text(TextId.WEB_SEARCH),
                    I18n.text(TextId.SEARCH_DISABLED),
                    AlertType.WARNING, editor, 3000);
            return;
        }
        if (searchClient.isRunning()) return;
        showSearchProgress(editor);
        searchClient.search(searchConfig, text, new SearchListener() {
            public void onResults(final SearchBundle bundle) {
                display.callSerially(new Runnable() {
                    public void run() {
                        clearSearchProgress();
                        if (bundle.results.size() == 0) {
                            showAlert(I18n.text(TextId.WEB_SEARCH),
                                    I18n.text(TextId.SEARCH_NO_RESULTS),
                                    AlertType.WARNING, editor, 3500);
                            return;
                        }
                        SearchBundle bounded = boundSearchBundle(bundle);
                        if (editIndex >= 0) truncateConversation(editIndex);
                        editingMessageIndex = -1;
                        pendingAttachment = null;
                        send(text, attachment, bounded);
                    }
                });
            }

            public void onError(final String error) {
                display.callSerially(new Runnable() {
                    public void run() {
                        clearSearchProgress();
                        showAlert(I18n.text(TextId.SEARCH_FAILED), I18n.error(error),
                                AlertType.ERROR, editor, 4500);
                    }
                });
            }
        });
    }

    private SearchBundle boundSearchBundle(SearchBundle source) {
        SearchBundle result = new SearchBundle(source.query, source.provider);
        result.searchedAt = source.searchedAt;
        int maximumResults = limits.searchResults;
        if (searchConfig.maximumResults < maximumResults) {
            maximumResults = searchConfig.maximumResults;
        }
        int cost = 0;
        int i;
        for (i = 0; i < source.results.size() && result.results.size() < maximumResults; i++) {
            SearchResult value = (SearchResult) source.results.elementAt(i);
            int remaining = limits.searchContextChars - cost;
            if (remaining < 160) break;
            String title = truncate(value.title, remaining > 256 ? 256 : remaining);
            remaining -= title.length();
            String url = truncate(value.url, remaining > 768 ? 768 : remaining);
            remaining -= url.length();
            String snippet = truncate(value.snippet, remaining);
            SearchResult copy = new SearchResult(title, url, snippet);
            copy.publishedAt = value.publishedAt;
            result.add(copy);
            cost += title.length() + url.length() + snippet.length() + 32;
        }
        return result;
    }

    private void openImagePicker() {
        if (!profile.multimodal) {
            showAlert(I18n.text(TextId.MULTIMODAL_DISABLED),
                    I18n.text(TextId.ENABLE_MULTIMODAL_FIRST),
                    AlertType.WARNING, chatCanvas, 2500);
            return;
        }
        try {
            Object instance = Class.forName("com.chihoko.j2mellm.ui.ImagePicker").newInstance();
            ImagePickerController picker = (ImagePickerController) instance;
            picker.configureLimits(limits);
            picker.open(display, chatCanvas, new ImagePickListener() {
                public void onImagePicked(ImageAttachment attachment) {
                    pendingAttachment = attachment;
                    openEditor();
                }

                public void onImagePickError(String message) {
                    showAlert(I18n.text(TextId.IMAGE_PICK_FAILED), I18n.error(message),
                            AlertType.ERROR, chatCanvas, 3500);
                }
            });
        } catch (Throwable failure) {
            showAlert(I18n.text(TextId.FILE_PICK_UNSUPPORTED),
                    I18n.text(TextId.JSR75_REQUIRED),
                    AlertType.WARNING, chatCanvas, 3500);
        }
    }

    private void openSettings(Displayable back) {
        settingsBack = back;
        settingsForm = new SettingsForm(profile, limits, this);
        display.setCurrent(settingsForm);
    }

    private boolean persistSettings(boolean confirmation, boolean requireModel) {
        ProviderProfile original = profile;
        ProviderProfile edited = original.copy();
        String oldModelsEndpoint = edited.modelsEndpoint;
        settingsForm.copyTo(edited);
        if (!startsWithHttp(edited.endpoint)) {
            showAlert(I18n.text(TextId.CONFIG_ERROR),
                    I18n.text(TextId.CHAT_ENDPOINT_INVALID),
                    AlertType.ERROR, settingsForm, 3000);
            return false;
        }
        if (!startsWithHttp(edited.modelsEndpoint)) {
            showAlert(I18n.text(TextId.CONFIG_ERROR),
                    I18n.text(TextId.MODELS_ENDPOINT_INVALID),
                    AlertType.ERROR, settingsForm, 3000);
            return false;
        }
        if (requireModel && edited.model.length() == 0) {
            showAlert(I18n.text(TextId.CONFIG_ERROR), I18n.text(TextId.MODEL_REQUIRED),
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
            showAlert(I18n.text(TextId.SAVE_FAILED), message(failure),
                    AlertType.ERROR, settingsForm, 3500);
            return false;
        }
        chatCanvas.setProfile(profile);
        if (profileList != null) profileList.refresh();
        if (confirmation) {
            Displayable next = settingsBack == null ? chatCanvas : settingsBack;
            showAlert(I18n.text(TextId.SAVED), I18n.text(TextId.PROFILE_SAVED),
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
            showAlert(I18n.text(TextId.SWITCH_FAILED), message(failure),
                    AlertType.ERROR, profileList, 3500);
            return false;
        }
        profile = target;
        if (conversation != null) {
            conversation.profileId = profile.id;
            conversation.updatedAt = System.currentTimeMillis();
            conversationStore = new ConversationStoreV3(conversation);
            saveConversationIndexQuietly();
        }
        chatCanvas.setProfile(profile);
        if (profileList != null) profileList.refresh();
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
        requestedScreen.setTitle(I18n.text(TextId.FETCHING_MODELS));
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
                        requestedScreen.setTitle(I18n.text(TextId.SELECT_MODEL));
                        requestedScreen.refresh(requestedProfile);
                        String text = I18n.text(TextId.FETCHED_PREFIX)
                                + requestedProfile.cachedModels.size()
                                + I18n.text(TextId.MODELS_SUFFIX);
                        if (truncated) text += I18n.text(TextId.MODEL_LIST_TRUNCATED);
                        if (saveError == null) {
                            showAlert(I18n.text(TextId.MODELS_UPDATED),
                                    text + I18n.text(TextId.SAVED_TO_RMS_SUFFIX),
                                    AlertType.CONFIRMATION, requestedScreen, 3000);
                        } else {
                            showAlert(I18n.text(TextId.MODELS_NOT_SAVED),
                                    text + I18n.text(TextId.MODELS_SESSION_ONLY) + saveError,
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
                        requestedScreen.setTitle(I18n.text(TextId.SELECT_MODEL));
                        showAlert(I18n.text(TextId.FETCH_FAILED), I18n.error(error),
                                AlertType.ERROR, requestedScreen, 4000);
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
            showAlert(I18n.text(TextId.SAVE_FAILED), message(failure),
                    AlertType.ERROR, modelList, 3500);
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
                    showAlert(I18n.text(TextId.IMPORT_FAILED), I18n.error(error),
                            AlertType.ERROR, profileList, 4000);
                }
            });
        } catch (Throwable failure) {
            showAlert(I18n.text(TextId.IMPORT_UNSUPPORTED),
                    I18n.text(TextId.JSR75_REQUIRED),
                    AlertType.WARNING, profileList, 3500);
        }
    }

    private void importConfiguration(final String fileUrl) {
        showProgress(I18n.text(TextId.IMPORTING), I18n.text(TextId.VALIDATING_CONFIG));
        new Thread(new Runnable() {
            public void run() {
                try {
                    ProvisioningPackage pack = newFileService().importFile(fileUrl);
                    final ProfileState imported = ProvisioningMapper.importProfiles(pack, profileState);
                    final SearchConfig importedSearch =
                            ProvisioningMapper.importSearch(pack, searchConfig);
                    final boolean includesSearch = pack.hasSearchConfig();
                    display.callSerially(new Runnable() {
                        public void run() {
                            finishImport(fileUrl, imported, importedSearch, includesSearch);
                        }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert(I18n.text(TextId.IMPORT_FAILED), message(failure),
                                    AlertType.ERROR,
                                    profileList, 5000);
                        }
                    });
                }
            }
        }).start();
    }

    private void finishImport(String fileUrl, ProfileState imported,
            SearchConfig importedSearch, boolean includesSearch) {
        invalidateModelRequest();
        saveConversationQuietly();
        boolean profileSaved = false;
        try {
            profileStore.save(imported);
            profileSaved = true;
            if (includesSearch) searchConfigStore.save(importedSearch);
        } catch (Exception failure) {
            if (profileSaved) {
                try { profileStore.save(profileState); } catch (Exception ignored) { }
            }
            showAlert(I18n.text(TextId.IMPORT_FAILED),
                    I18n.text(TextId.RMS_WRITE_FAILED_PREFIX) + message(failure),
                    AlertType.ERROR,
                    profileList, 5000);
            return;
        }
        profileState = imported;
        if (includesSearch) searchConfig = importedSearch;
        profile = profileState.getActiveProfile();
        if (conversation != null) {
            conversation.profileId = profile.id;
            conversation.updatedAt = System.currentTimeMillis();
            conversationStore = new ConversationStoreV3(conversation);
            saveConversationIndexQuietly();
        }
        chatCanvas.setProfile(profile);
        profileList = new ProfileListScreen(profileState, this);
        importedFileUrl = fileUrl;
        importResultAlert = new Alert(I18n.text(TextId.IMPORT_SUCCEEDED),
                I18n.text(TextId.IMPORT_SUCCEEDED_BODY),
                null, AlertType.CONFIRMATION);
        importResultAlert.setTimeout(Alert.FOREVER);
        importResultAlert.addCommand(deleteImportedCommand);
        importResultAlert.addCommand(keepImportedCommand);
        importResultAlert.setCommandListener(this);
        display.setCurrent(importResultAlert);
    }

    private void exportConfiguration() {
        final ProvisioningPackage pack =
                ProvisioningMapper.exportConfiguration(profileState, searchConfig);
        showProgress(I18n.text(TextId.EXPORTING), I18n.text(TextId.GENERATING_BACKUP));
        new Thread(new Runnable() {
            public void run() {
                try {
                    ProvisioningFileController files = newFileService();
                    final String url = files.defaultExportUrl();
                    files.exportFile(url, pack);
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert(I18n.text(TextId.EXPORT_SUCCEEDED),
                                    url + I18n.text(TextId.SECRET_FILE_WARNING_SUFFIX),
                                    AlertType.CONFIRMATION, profileList, 5000);
                        }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert(I18n.text(TextId.EXPORT_FAILED), message(failure),
                                    AlertType.ERROR,
                                    profileList, 5000);
                        }
                    });
                }
            }
        }).start();
    }

    private void deleteImportedFile(final String fileUrl) {
        showProgress(I18n.text(TextId.DELETING),
                I18n.text(TextId.DELETING_IMPORTED_CONFIG));
        new Thread(new Runnable() {
            public void run() {
                try {
                    newFileService().deleteFile(fileUrl);
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert(I18n.text(TextId.DELETED),
                                    I18n.text(TextId.DELETED_BODY),
                                    AlertType.CONFIRMATION, profileList, 3500);
                        }
                    });
                } catch (final Throwable failure) {
                    display.callSerially(new Runnable() {
                        public void run() {
                            showAlert(I18n.text(TextId.DELETE_FAILED), message(failure),
                                    AlertType.ERROR,
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

    private void send(String text, ImageAttachment attachment, SearchBundle searchBundle) {
        if (!ensureProfileReady()) return;
        if (attachment != null && !profile.multimodal) attachment = null;
        final ChatMessage user = new ChatMessage(ChatMessage.ROLE_USER, text);
        if (searchBundle != null) user.setSearchBundle(searchBundle);
        if (attachment != null) {
            user.setAttachment(attachment);
            try {
                ImageDimensions dimensions = ImageDimensions.parse(attachment.data);
                if (dimensions == null
                        || !dimensions.fitsPixelLimit(limits.maximumImagePixels)) {
                    throw new IOException(I18n.text(TextId.IMAGE_PREVIEW_UNSUITABLE));
                }
                ensurePreviewMemory(attachment.data.length, dimensions.pixelCountOrMaximum());
                Image preview = Image.createImage(attachment.data, 0, attachment.data.length);
                user.setImagePreview(ImageScaler.fit(preview, previewWidth(), 160));
                preview = null;
            } catch (Throwable ignored) {
                user.setImageStatus(I18n.text(TextId.IMAGE_ATTACHED_NO_PREVIEW));
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

        Vector requestHistory = requestHistory();
        final ProviderProfile requestProfile = profile.copy();
        client.send(requestProfile, requestHistory, limits, new ChatListener() {
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
                assistant.setImageStatus(I18n.text(TextId.LOADING_IMAGE));
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
                                    ? I18n.text(TextId.MODEL_NO_TEXT)
                                    : I18n.text(TextId.IMAGE_RESPONSE));
                        } else if (source != null && source.startsWith("data:image/")
                                && assistant.getContent().startsWith(source)) {
                            assistant.replaceContent(I18n.text(TextId.IMAGE_RESPONSE));
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
                            assistant.appendContent(I18n.text(TextId.REQUEST_FAILED_PREFIX)
                                    + I18n.error(error));
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
        message.setImageStatus(I18n.text(TextId.LOADING_IMAGE));
        new ImageLoader(limits).load(source, previewWidth(), 180, new ImageLoadListener() {
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
                        String value = I18n.error(reason);
                        if (value.length() > 80) value = value.substring(0, 80) + "…";
                        message.setImageStatus(
                                I18n.text(TextId.IMAGE_DISPLAY_FAILED_PREFIX) + value);
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
            if (activeAssistant.getContent().length() == 0) {
                activeAssistant.appendContent(I18n.text(TextId.STOPPED));
            }
            activeAssistant = null;
        }
        chatCanvas.setBusy(false);
        chatCanvas.contentChanged();
        saveConversationQuietly();
    }

    private void initializeConversations() {
        conversationState = conversationIndexStore.load();
        if (conversationState.conversations.size() > 0) return;

        boolean migrated = false;
        String activeMigratedId = null;
        int i;
        for (i = 0; i < profileState.profiles.size(); i++) {
            ProviderProfile candidate =
                    (ProviderProfile) profileState.profiles.elementAt(i);
            ProfileConversationStore legacy = new ProfileConversationStore(candidate.id);
            Vector oldMessages = legacy.load();
            if (oldMessages.size() == 0) continue;
            ConversationMeta meta = new ConversationMeta(
                    ConversationIds.next(conversationState), candidate.id);
            updateConversationMetadata(meta, oldMessages);
            conversationState.add(meta);
            try {
                new ConversationStoreV3(meta).save(oldMessages, limits.savedMessages);
                migrated = true;
                if (candidate.id.equals(profileState.activeProfileId)) {
                    activeMigratedId = meta.id;
                }
            } catch (Exception ignored) {
                conversationState.remove(meta.id);
            }
        }
        if (conversationState.conversations.size() == 0) {
            conversationState.add(new ConversationMeta(
                    ConversationIds.next(conversationState), profile.id));
        } else if (activeMigratedId != null) {
            conversationState.activeConversationId = activeMigratedId;
        }
        conversationState.migratedThisLoad = migrated;
        saveConversationIndexQuietly();
    }

    private void loadConversation(ConversationMeta meta) {
        if (meta == null) {
            meta = new ConversationMeta(ConversationIds.next(conversationState), profile.id);
            conversationState.add(meta);
            saveConversationIndexQuietly();
        }
        conversation = meta;
        conversationState.activeConversationId = meta.id;
        ProviderProfile storedProfile = profileState.find(meta.profileId);
        if (storedProfile != null) {
            profile = storedProfile;
            profileState.activeProfileId = storedProfile.id;
        } else {
            meta.profileId = profile.id;
        }
        conversationStore = new ConversationStoreV3(meta);
        messages = conversationStore.load();
        pruneConversation();
    }

    private void pruneConversation() {
        if (messages == null) return;
        int maximumMessages = limits == null ? 32 : limits.activeMessages;
        int maximumChars = limits == null ? 49152 : limits.activeConversationChars;
        while ((messages.size() > maximumMessages
                || conversationCost(messages) > maximumChars) && messages.size() > 2) {
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

    private Vector requestHistory() {
        Vector selected = new Vector();
        int maximum = limits == null ? 49152 : limits.requestContextChars;
        int cost = 0;
        int i;
        for (i = messages.size() - 1; i >= 0; i--) {
            ChatMessage value = (ChatMessage) messages.elementAt(i);
            if (value.pending || value.error) continue;
            int messageCost = value.getCharacterCost() + 64;
            if (selected.size() > 0 && cost + messageCost > maximum) break;
            selected.insertElementAt(value, 0);
            if (Integer.MAX_VALUE - cost < messageCost) cost = Integer.MAX_VALUE;
            else cost += messageCost;
        }
        return selected;
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
        try {
            conversationStore.save(messages, limits == null ? 24 : limits.savedMessages);
            updateConversationMetadata();
            conversationIndexStore.save(conversationState);
        } catch (Exception ignored) { }
    }

    private void saveConversationIndexQuietly() {
        if (conversationState == null) return;
        try { conversationIndexStore.save(conversationState); } catch (Exception ignored) { }
    }

    private void updateConversationMetadata() {
        updateConversationMetadata(conversation, messages);
    }

    private void updateConversationMetadata(ConversationMeta meta, Vector source) {
        if (meta == null || source == null) return;
        String firstUser = "";
        String preview = "";
        int i;
        for (i = 0; i < source.size(); i++) {
            ChatMessage value = (ChatMessage) source.elementAt(i);
            String content = singleLine(value.getContent());
            if (content.length() > 0) preview = content;
            if (firstUser.length() == 0 && ChatMessage.ROLE_USER.equals(value.role)) {
                firstUser = content;
            }
        }
        String title = meta.title == null ? "" : meta.title.trim();
        if ((title.length() == 0 || title.equals(I18n.text(TextId.NEW_CHAT_TITLE)))
                && firstUser.length() > 0) {
            meta.title = truncate(firstUser, ConversationMeta.MAX_TITLE_CHARS);
        }
        meta.preview = truncate(preview, ConversationMeta.MAX_PREVIEW_CHARS);
        meta.messageCount = source.size();
        meta.updatedAt = System.currentTimeMillis();
    }

    private String singleLine(String value) {
        if (value == null) return "";
        StringBuffer result = new StringBuffer();
        int i;
        for (i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(c == '\r' || c == '\n' ? ' ' : c);
        }
        return result.toString().trim();
    }

    private String truncate(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private void saveProfilesQuietly() {
        if (profileState == null) return;
        try { profileStore.save(profileState); } catch (Exception ignored) { }
    }

    private boolean ensureIdle() {
        if (client.isRunning() || searchClient.isRunning()) {
            showAlert(I18n.text(TextId.REQUEST_IN_PROGRESS),
                    I18n.text(TextId.STOP_REQUEST_FIRST),
                    AlertType.WARNING, chatCanvas, 2500);
            return false;
        }
        return true;
    }

    private boolean ensureProfileReady() {
        if (profile != null && profile.isReady()) return true;
        showAlert(I18n.text(TextId.NOT_CONFIGURED),
                I18n.text(TextId.CONFIGURE_ENDPOINT_MODEL),
                AlertType.WARNING, chatCanvas, 3000);
        return false;
    }

    private int previewWidth() {
        int width = (chatCanvas.getWidth() * 82) / 100 - 16;
        return width < 48 ? 48 : width;
    }

    private void ensurePreviewMemory(int compressedBytes, int pixels) throws IOException {
        long free = Runtime.getRuntime().freeMemory();
        long reserve = compressedBytes + pixels * 8L + 262144L;
        if (free > 0 && free < reserve) {
            throw new IOException(I18n.text(TextId.LOW_MEMORY));
        }
    }

    private String startupNotice() {
        StringBuffer text = new StringBuffer();
        if (profileState.migratedThisLoad) {
            text.append(I18n.text(TextId.MIGRATED_V01));
        }
        if (profileState.recoveredFromBackup) {
            text.append(I18n.text(TextId.PROFILE_RECOVERED));
        }
        if (profileState.storageCorrupt) {
            text.append(I18n.text(TextId.PROFILE_STORAGE_CORRUPT)).append('\n');
        }
        if (conversationState.migratedThisLoad) {
            text.append(I18n.text(TextId.CHAT_MIGRATED));
        }
        if (conversationState.recoveredFromBackup
                || conversationStore.didRecoverFromBackup()) {
            text.append(I18n.text(TextId.CHAT_RECOVERED));
        }
        return text.toString().trim();
    }

    private boolean startsWithHttp(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String message(Throwable failure) {
        String value = failure.getMessage();
        return I18n.error(value == null ? failure.toString() : value);
    }

    private void createCommands() {
        sendCommand = new Command(I18n.text(TextId.SEND), Command.OK, 1);
        searchSendCommand = new Command(
                I18n.text(TextId.SEARCH_AND_SEND), Command.SCREEN, 2);
        cancelEditCommand = new Command(I18n.text(TextId.BACK), Command.BACK, 2);
        deleteImportedCommand = new Command(
                I18n.text(TextId.DELETE_CONFIG_PACKAGE), Command.OK, 1);
        keepImportedCommand = new Command(I18n.text(TextId.KEEP_FILE), Command.BACK, 2);
        confirmDeleteConversationCommand = new Command(
                I18n.text(TextId.DELETE_CHAT), Command.OK, 1);
        cancelSearchCommand = new Command(I18n.text(TextId.STOP), Command.STOP, 1);
    }

    private String systemLocale() {
        try {
            return System.getProperty("microedition.locale");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showProgress(String title, String text) {
        Alert alert = new Alert(title, text, null, AlertType.INFO);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    private void showSearchProgress(Displayable back) {
        searchReturnDisplay = back;
        searchProgressAlert = new Alert(I18n.text(TextId.WEB_SEARCH),
                I18n.text(TextId.SEARCHING_WEB), null, AlertType.INFO);
        searchProgressAlert.setTimeout(Alert.FOREVER);
        searchProgressAlert.addCommand(cancelSearchCommand);
        searchProgressAlert.setCommandListener(this);
        display.setCurrent(searchProgressAlert);
    }

    private void clearSearchProgress() {
        searchProgressAlert = null;
        searchReturnDisplay = null;
    }

    private void showAlert(String title, String text, AlertType type,
            Displayable next, int timeout) {
        Alert alert = new Alert(title, text, null, type);
        alert.setTimeout(timeout);
        display.setCurrent(alert, next);
    }
}
