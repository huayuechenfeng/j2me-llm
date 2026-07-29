package com.chihoko.j2mellm.i18n;

final class TextEn implements TextCatalog {
    public String text(int id) {
        switch (id) {
            case TextId.SEND: return "Send";
            case TextId.BACK: return "Back";
            case TextId.DELETE_CONFIG_PACKAGE: return "Delete package";
            case TextId.KEEP_FILE: return "Keep file";
            case TextId.NO_AVAILABLE_PROFILES: return "No provider profile is available";
            case TextId.STARTUP_CHECK: return "v0.3 data check";
            case TextId.DEFAULT_IMAGE_PROMPT: return "Describe this image.";
            case TextId.SEND_MESSAGE: return "Send message";
            case TextId.IMAGE_PREFIX: return "Image: ";
            case TextId.MULTIMODAL_DISABLED: return "Multimodal is off";
            case TextId.ENABLE_MULTIMODAL_FIRST: return "Enable multimodal input in the current profile first.";
            case TextId.IMAGE_PICK_FAILED: return "Image selection failed";
            case TextId.FILE_PICK_UNSUPPORTED: return "File selection unsupported";
            case TextId.JSR75_REQUIRED: return "This phone requires JSR-75 FileConnection.";
            case TextId.CONFIG_ERROR: return "Configuration error";
            case TextId.CHAT_ENDPOINT_INVALID: return "The chat endpoint must start with http:// or https://.";
            case TextId.MODELS_ENDPOINT_INVALID: return "The models endpoint must start with http:// or https://.";
            case TextId.MODEL_REQUIRED: return "Enter a model name or select one from the model list.";
            case TextId.SAVE_FAILED: return "Save failed";
            case TextId.SAVED: return "Saved";
            case TextId.PROFILE_SAVED: return "The current profile was saved to RMS with a recovery copy.";
            case TextId.SWITCH_FAILED: return "Switch failed";
            case TextId.HISTORY_RECOVERED: return "History recovered";
            case TextId.HISTORY_RECOVERED_BODY: return "The primary history record was damaged. Its RMS recovery copy was loaded.";
            case TextId.FETCHING_MODELS: return "Fetching models…";
            case TextId.SELECT_MODEL: return "Select model";
            case TextId.FETCHED_PREFIX: return "Fetched ";
            case TextId.MODELS_SUFFIX: return " models.";
            case TextId.MODEL_LIST_TRUNCATED: return " The list was limited to 64 items to protect memory.";
            case TextId.MODELS_UPDATED: return "Model list updated";
            case TextId.SAVED_TO_RMS_SUFFIX: return " Saved to RMS.";
            case TextId.MODELS_NOT_SAVED: return "Models fetched but not saved";
            case TextId.MODELS_SESSION_ONLY: return " They remain selectable in this session but may be lost after restart.\n";
            case TextId.FETCH_FAILED: return "Fetch failed";
            case TextId.IMPORT_FAILED: return "Import failed";
            case TextId.IMPORT_UNSUPPORTED: return "Import unsupported";
            case TextId.IMPORTING: return "Importing";
            case TextId.VALIDATING_CONFIG: return "Validating the .j2cfg package…";
            case TextId.RMS_WRITE_FAILED_PREFIX: return "RMS write failed: ";
            case TextId.IMPORT_SUCCEEDED: return "Import complete";
            case TextId.IMPORT_SUCCEEDED_BODY: return "Profiles were saved to RMS. The package contains plaintext keys; delete the transferred file now if it is no longer needed.";
            case TextId.EXPORTING: return "Exporting";
            case TextId.GENERATING_BACKUP: return "Generating a checksummed .j2cfg backup…";
            case TextId.EXPORT_SUCCEEDED: return "Export complete";
            case TextId.SECRET_FILE_WARNING_SUFFIX: return "\nThis file contains plaintext keys. Store it securely.";
            case TextId.EXPORT_FAILED: return "Export failed";
            case TextId.DELETING: return "Deleting";
            case TextId.DELETING_IMPORTED_CONFIG: return "Deleting the imported plaintext configuration package…";
            case TextId.DELETED: return "Deleted";
            case TextId.DELETED_BODY: return "The package was deleted from the file system. Profiles remain stored in RMS.";
            case TextId.DELETE_FAILED: return "Delete failed";
            case TextId.NOT_CONFIGURED: return "Not configured";
            case TextId.CONFIGURE_ENDPOINT_MODEL: return "Configure an endpoint and model name first.";
            case TextId.IMAGE_PREVIEW_UNSUITABLE: return "Image dimensions are unsuitable for a local preview";
            case TextId.IMAGE_ATTACHED_NO_PREVIEW: return "Image attached (preview unavailable or memory is low)";
            case TextId.LOADING_IMAGE: return "Loading image…";
            case TextId.MODEL_NO_TEXT: return "(The model returned no text)";
            case TextId.IMAGE_RESPONSE: return "(Image response)";
            case TextId.REQUEST_FAILED_PREFIX: return "Request failed: ";
            case TextId.UNKNOWN_ERROR: return "Unknown error";
            case TextId.IMAGE_DISPLAY_FAILED_PREFIX: return "Unable to display image: ";
            case TextId.STOPPED: return "(Stopped)";
            case TextId.REQUEST_IN_PROGRESS: return "Request in progress";
            case TextId.STOP_REQUEST_FIRST: return "Stop the current request before switching profiles or settings.";
            case TextId.LOW_MEMORY: return "Not enough free memory";
            case TextId.MIGRATED_V01: return "The v0.1 configuration was migrated to “Custom (legacy configuration)”. The old RMS was retained.\n";
            case TextId.PROFILE_RECOVERED: return "The primary profile record was damaged and repaired from its RMS recovery copy.\n";
            case TextId.PROFILE_STORAGE_CORRUPT: return "Both profile records were unreadable. Safe defaults were loaded; import a .j2cfg backup.";
            case TextId.CHAT_MIGRATED: return "Legacy chat history was migrated.\n";
            case TextId.CHAT_RECOVERED: return "Chat history was loaded from its recovery copy.";
            case TextId.COMPOSE: return "Compose";
            case TextId.IMAGE: return "Image";
            case TextId.PROFILES: return "Profiles";
            case TextId.SETTINGS: return "Settings";
            case TextId.REASONING: return "Reasoning";
            case TextId.CLEAR: return "Clear";
            case TextId.STOP: return "Stop";
            case TextId.EXIT: return "Exit";
            case TextId.LANGUAGE: return "Language";
            case TextId.MORE: return "More";
            case TextId.EMPTY_CHAT: return "Tap Compose to start chatting";
            case TextId.EMPTY_CHAT_WITH_IMAGE: return "Tap Compose or Image to start chatting";
            case TextId.RECEIVING_RESPONSE: return "● Receiving model response";
            case TextId.AUTO: return "Auto";
            case TextId.ON: return "On";
            case TextId.OFF: return "Off";
            case TextId.REQUEST_REASONING_PREFIX: return "Request reasoning: ";
            case TextId.CHAIN_PREFIX: return " · Chain: ";
            case TextId.EXPANDED: return "Shown";
            case TextId.COLLAPSED: return "Hidden";
            case TextId.YOU: return "You";
            case TextId.THINKING: return "Reasoning";
            case TextId.THINKING_COLLAPSED: return "Reasoning · hidden";
            case TextId.MODEL_IMAGE: return "Model image";
            case TextId.THINKING_PENDING: return "Thinking…";
            case TextId.SAVE: return "Save";
            case TextId.THINKING_AUTO: return "Auto (send no control parameter)";
            case TextId.THINKING_ENABLED: return "On";
            case TextId.THINKING_DISABLED: return "Off";
            case TextId.NO_THINKING_PARAMETER: return "Send no reasoning parameter";
            case TextId.KIMI_ALWAYS_THINKING: return "Always reason (Kimi)";
            case TextId.PROFILE_SETTINGS_PREFIX: return "Profile settings - ";
            case TextId.PROFILE_NAME: return "Profile name";
            case TextId.ENDPOINT_CONTROL: return "Endpoint control";
            case TextId.ADVANCED_OVERRIDE_GATEWAY: return "Advanced override (compatible gateway)";
            case TextId.CHAT_ENDPOINT: return "Chat Completions endpoint";
            case TextId.MODELS_ENDPOINT: return "Models endpoint";
            case TextId.API_KEY_OPTIONAL: return "API key (optional)";
            case TextId.MODEL_NAME: return "Model name";
            case TextId.SYSTEM_PROMPT: return "System prompt";
            case TextId.RESPONSE_MODE: return "Response mode";
            case TextId.ENABLE_STREAMING: return "Enable streaming display";
            case TextId.THINKING_MODE: return "Reasoning mode";
            case TextId.REASONING_EFFORT: return "Reasoning effort";
            case TextId.THINKING_PROTOCOL: return "Reasoning parameter format";
            case TextId.MULTIMODAL: return "Multimodal";
            case TextId.ALLOW_IMAGES: return "Allow selecting and sending images";
            case TextId.HISTORY_MESSAGES: return "Recent messages to include";
            case TextId.ALWAYS_THINKING_MODEL: return "Always-reasoning model";
            case TextId.ALWAYS_THINKING_HELP: return "This model always reasons. Saving Off is normalized to On. K3 uses reasoning effort; K2.7 Code sends no switch field.";
            case TextId.ADVANCED_OVERRIDE: return "Advanced override";
            case TextId.CUSTOM_OVERRIDE_HELP: return "Custom profiles always use the endpoints above. A blank models endpoint is derived from the chat endpoint.";
            case TextId.OFFICIAL_OVERRIDE_HELP: return "When unchecked, saving restores official endpoints. Check it only for a trusted compatible gateway.";
            case TextId.MEMORY_HINT: return "Memory";
            case TextId.MEMORY_HINT_BODY: return "Images are off by default. A shorter history reduces memory and network use on older phones.";
            case TextId.SECURITY_HINT: return "Security";
            case TextId.SECURITY_HINT_BODY: return "Java ME has no secure keystore. Import short-lived gateway tokens in an offline package and delete the file after transfer.";
            case TextId.MODELS: return "Models";
            case TextId.IMPORT_CONFIG: return "Import config";
            case TextId.EXPORT_CONFIG: return "Export config";
            case TextId.PROFILE_LIST: return "Provider profiles";
            case TextId.NOT_CONFIGURED_SUFFIX: return "  (not configured)";
            case TextId.FETCH_ONLINE: return "Fetch online";
            case TextId.NO_MODEL_CACHE: return "No cached models. Choose Fetch online, then select a model.";
            case TextId.UP: return "Up";
            case TextId.SELECT_CONFIG: return "Select .j2cfg";
            case TextId.FIRST_256_SUFFIX: return " · first 256";
            case TextId.READ_FILESYSTEM_FAILED_PREFIX: return "Unable to read the file system: ";
            case TextId.DIRECTORY_MISSING: return "Directory does not exist";
            case TextId.OPEN_DIRECTORY_FAILED_PREFIX: return "Unable to open directory: ";
            case TextId.SELECT_IMAGE_LOCATION: return "Select image location";
            case TextId.IMAGE_TOO_LARGE: return "Image exceeds the configured size limit";
            case TextId.IMAGE_DIMENSIONS_UNKNOWN: return "Image dimensions could not be verified; loading was stopped to protect memory";
            case TextId.IMAGE_PIXELS_TOO_LARGE: return "Image exceeds the configured pixel limit";
            case TextId.READ_IMAGE_FAILED_PREFIX: return "Unable to read image: ";
            case TextId.CHOOSE_SMALLER_IMAGE: return "Not enough free memory; choose a smaller image";
            case TextId.IMAGE_READ_INCOMPLETE: return "Image data ended unexpectedly";
            case TextId.IMAGE_DIMENSIONS_UNSAFE: return "Image dimensions could not be verified safely; preview skipped";
            case TextId.IMAGE_PIXELS_PREVIEW_SKIPPED: return "Image exceeds the configured pixel limit; preview skipped";
            case TextId.IMAGE_SOURCE_MISSING: return "Missing image source";
            case TextId.IMAGE_DATA_URL_UNSUPPORTED: return "Unsupported image data URL";
            case TextId.RETURNED_IMAGE_TOO_LARGE: return "Returned image exceeds the configured size limit";
            case TextId.IMAGE_PROTOCOL_UNSUPPORTED: return "Only HTTP, HTTPS, and data URL images are supported";
            case TextId.IMAGE_PREVIEW_LOW_MEMORY: return "Not enough free memory; image preview skipped";
            case TextId.LANGUAGE_TITLE: return "Language / 语言";
            case TextId.APPLY: return "Apply";
            case TextId.SYSTEM_DEFAULT: return "System default / 跟随系统";
            case TextId.SIMPLIFIED_CHINESE: return "简体中文";
            case TextId.ENGLISH: return "English";
            case TextId.LANGUAGE_SAVE_FAILED: return "Unable to save language setting";
            case TextId.CUSTOM: return "Custom";
            case TextId.CUSTOM_LEGACY: return "Custom (legacy configuration)";
            case TextId.CONVERSATIONS: return "Chats / New";
            case TextId.NEW_CHAT: return "New chat";
            case TextId.RENAME_CHAT: return "Rename";
            case TextId.DELETE_CHAT: return "Delete chat";
            case TextId.CHAT_LIST: return "Conversations";
            case TextId.NEW_CHAT_TITLE: return "New chat";
            case TextId.MESSAGE_LIST: return "Messages";
            case TextId.MESSAGE_ACTIONS: return "Message actions";
            case TextId.EDIT_AND_RESEND: return "Edit and resend";
            case TextId.REGENERATE: return "Regenerate";
            case TextId.RETRY: return "Retry";
            case TextId.VIEW_TEXT: return "View text";
            case TextId.DELETE_FOLLOWING: return "This also removes later messages.";
            case TextId.DELETE_CHAT_CONFIRM: return "Delete this conversation?";
            case TextId.NO_MESSAGES: return "No messages";
            case TextId.USER_MESSAGE_PREFIX: return "You: ";
            case TextId.ASSISTANT_MESSAGE_PREFIX: return "AI: ";
            case TextId.WEB_SEARCH: return "Web search";
            case TextId.SEARCH_SETTINGS: return "Search settings";
            case TextId.SEARCH_PROVIDER: return "Search provider";
            case TextId.SEARCH_ENABLED: return "Enable web search";
            case TextId.SEARCH_API_KEY: return "Search API key";
            case TextId.SEARCH_ENDPOINT: return "Search endpoint";
            case TextId.SEARCH_PRESET: return "Search preset";
            case TextId.SEARCH_RESULTS: return "Maximum results";
            case TextId.SEARCH_QUERY: return "Search query";
            case TextId.SEARCHING_WEB: return "Searching the web";
            case TextId.SEARCH_FAILED: return "Search failed";
            case TextId.SEARCH_NO_RESULTS: return "No useful search results";
            case TextId.SEARCH_FREE_NOTICE: return "Free providers need no key, but availability and result quality are not guaranteed.";
            case TextId.TEST_SEARCH: return "Test search";
            case TextId.LIMITS: return "Resource limits";
            case TextId.RESOURCE_MODE: return "Limit mode";
            case TextId.COMPATIBLE: return "Compatible";
            case TextId.RECOMMENDED: return "Recommended";
            case TextId.UNLOCK_LIMITS: return "Unlock custom limits";
            case TextId.UNLOCK_WARNING: return "Higher limits may cause out-of-memory errors or slow requests. The recommended values are safe defaults, not hard device limits.";
            case TextId.ACTIVE_CONTEXT_CHARS: return "Active chat characters";
            case TextId.MAX_ACTIVE_MESSAGES: return "Active messages";
            case TextId.REQUEST_CONTEXT_CHARS: return "Request context characters";
            case TextId.SAVED_MESSAGES: return "Saved messages";
            case TextId.SEARCH_CONTEXT_CHARS: return "Search context characters";
            case TextId.IMAGE_LIMITS: return "Image limits";
            case TextId.IMAGE_MODE: return "Image mode";
            case TextId.IMAGE_BYTES: return "Input image KB";
            case TextId.IMAGE_PIXELS: return "Input image pixels";
            case TextId.IMAGE_RESPONSE_BYTES: return "Returned image KB";
            case TextId.HIGH_PERFORMANCE: return "High-performance phone";
            case TextId.IMAGE_UNLOCK_WARNING: return "Large images can exhaust the Java heap even on capable phones. Decode only after a free-memory check.";
            case TextId.EDIT_MESSAGE: return "Edit message";
            case TextId.SEARCH_AND_SEND: return "Search + send";
            case TextId.SEARCH_SOURCES: return "Web sources";
            case TextId.SEARCH_DISABLED: return "Web search is disabled";
            case TextId.SEARCH_BAD_CONFIG: return "Search provider configuration is incomplete";
            case TextId.SEARCH_SETTINGS_SAVED: return "Search settings saved";
            case TextId.FREE_COMPOSITE: return "Free: DuckDuckGo + Wikipedia";
            case TextId.PUBLIC_SEARXNG: return "Free: public SearXNG";
            case TextId.BRAVE: return "Brave Search";
            case TextId.TAVILY: return "Tavily";
            case TextId.EXA: return "Exa";
            case TextId.CUSTOM_SEARCH: return "Custom JSON API";
            case TextId.REGENERATING: return "Regenerating response";
            case TextId.CONVERSATION_RECOVERED: return "A conversation was recovered from its backup.";
            case TextId.CUSTOM_LIMIT_WARNING: return "Custom mode is unlocked. Increase values gradually and keep a way to restart the app.";
            case TextId.SIZE_KB_HELP: return "Values are in KB; 1024 KB = 1 MB.";
            case TextId.MESSAGE_CONTENT_CHARS: return "Single message characters";
            case TextId.MESSAGE_REASONING_CHARS: return "Reasoning characters";
            case TextId.SEARCH_UNTRUSTED_NOTICE: return "Search results are untrusted text. Review sensitive queries before sending.";
            default: return null;
        }
    }

    public String error(String value) {
        if (value == null) return text(TextId.UNKNOWN_ERROR);
        if ("目录不存在".equals(value)) return text(TextId.DIRECTORY_MISSING);
        if ("图片超过 96 KB，请先压缩".equals(value)) return text(TextId.IMAGE_TOO_LARGE);
        if ("无法识别图片尺寸，为防止内存溢出未载入".equals(value)) {
            return text(TextId.IMAGE_DIMENSIONS_UNKNOWN);
        }
        if ("图片像素超过 65536，请先缩小到约 256×256".equals(value)) {
            return text(TextId.IMAGE_PIXELS_TOO_LARGE);
        }
        if ("可用内存不足，请选择更小的图片".equals(value)) {
            return text(TextId.CHOOSE_SMALLER_IMAGE);
        }
        if ("图片读取不完整".equals(value)) return text(TextId.IMAGE_READ_INCOMPLETE);
        if ("无法安全识别图片尺寸，已跳过预览".equals(value)) {
            return text(TextId.IMAGE_DIMENSIONS_UNSAFE);
        }
        if ("图片像素超过 65536，已跳过预览".equals(value)) {
            return text(TextId.IMAGE_PIXELS_PREVIEW_SKIPPED);
        }
        if ("缺少图片地址".equals(value)) return text(TextId.IMAGE_SOURCE_MISSING);
        if ("不支持的图片 data URL".equals(value)) return text(TextId.IMAGE_DATA_URL_UNSUPPORTED);
        if ("返回图片超过 256 KB".equals(value)) return text(TextId.RETURNED_IMAGE_TOO_LARGE);
        if ("只支持 HTTP、HTTPS 或 data URL 图片".equals(value)) {
            return text(TextId.IMAGE_PROTOCOL_UNSUPPORTED);
        }
        if ("可用内存不足，已跳过图片预览".equals(value)) {
            return text(TextId.IMAGE_PREVIEW_LOW_MEMORY);
        }
        if ("可用内存不足".equals(value)) return text(TextId.LOW_MEMORY);
        if ("请求已取消".equals(value)) return "Request cancelled";
        if ("已有请求正在进行".equals(value)) return "A request is already in progress";
        if ("已有模型列表请求正在进行".equals(value)) return "A model-list request is already in progress";
        if ("模型列表地址为空".equals(value)) return "The models endpoint is empty";
        if ("服务器返回的单行数据过长".equals(value)) return "A server response line is too long";
        if ("服务器响应超过内存安全限制".equals(value)) return "The server response exceeds the memory safety limit";
        if ("服务器返回的不是 JSON 对象".equals(value)) return "The server response is not a JSON object";
        if ("响应中没有 choices".equals(value)) return "The response has no choices";
        if ("响应中没有 message".equals(value)) return "The response has no message";
        if ("模型服务返回错误".equals(value)) return "The model service returned an error";
        if ("错误响应过长".equals(value)) return "The error response is too long";
        if (value.startsWith("模型列表响应超过内存安全限制（")) {
            return "The model-list response exceeds the memory safety limit ("
                    + bytesInside(value) + " bytes)";
        }
        if (value.startsWith("无法读取文件系统：")) {
            return text(TextId.READ_FILESYSTEM_FAILED_PREFIX)
                    + error(value.substring("无法读取文件系统：".length()));
        }
        if (value.startsWith("无法打开目录：")) {
            return text(TextId.OPEN_DIRECTORY_FAILED_PREFIX)
                    + error(value.substring("无法打开目录：".length()));
        }
        if (value.startsWith("无法读取图片：")) {
            return text(TextId.READ_IMAGE_FAILED_PREFIX)
                    + error(value.substring("无法读取图片：".length()));
        }
        if (value.startsWith("图片 HTTP ")) return "Image HTTP " + value.substring("图片 HTTP ".length());

        String parsed = parserError(value);
        if (parsed != null) return parsed;
        String provisioned = provisioningError(value);
        return provisioned == null ? value : provisioned;
    }

    private String provisioningError(String value) {
        if ("配置包为空".equals(value)) return "The configuration package is empty";
        if ("配置内容超过 24 KB".equals(value)) return "Configuration content exceeds 24 KB";
        if ("配置包超过 32 KB".equals(value)) return "The configuration package exceeds 32 KB";
        if ("配置包外层必须是对象".equals(value)) return "The package root must be an object";
        if ("配置包格式不受支持".equals(value)) return "Unsupported package format";
        if ("配置包版本不受支持".equals(value)) return "Unsupported package version";
        if ("配置包编码不受支持".equals(value)) return "Unsupported package encoding";
        if (value.startsWith("配置包校验失败")) return "Package checksum failed; the file may be damaged or modified";
        if ("配置内容必须是对象".equals(value)) return "Configuration content must be an object";
        if ("配置内容缺少 profiles".equals(value)) return "Configuration content has no profiles";
        if ("配置包至少需要一个档案".equals(value)) return "The package requires at least one profile";
        if ("配置包最多允许 8 个档案".equals(value)) return "The package allows at most 8 profiles";
        if ("档案必须是对象".equals(value)) return "A profile must be an object";
        if ("档案为空".equals(value)) return "A profile is empty";
        if ("活动档案不存在".equals(value)) return "The active profile does not exist";
        if ("思考模式值无效".equals(value)) return "Invalid reasoning mode";
        if ("思考协议值无效".equals(value)) return "Invalid reasoning protocol";
        if ("历史消息数必须在 2 到 24 之间".equals(value)) {
            return "History message count must be between 2 and 24";
        }
        if ("配置文件不存在".equals(value)) return "The configuration file does not exist";
        if ("导出位置是目录".equals(value)) return "The export destination is a directory";
        if ("同名备份已存在；为保护旧备份，本次导出已取消".equals(value)) {
            return "A backup with the same name already exists; export was cancelled";
        }
        if ("临时导出文件已存在，请重试".equals(value)) {
            return "The temporary export file already exists; try again";
        }
        if ("临时备份回读校验失败".equals(value)) {
            return "Temporary backup verification failed";
        }
        if ("导出目标刚刚被占用；旧文件保持不变".equals(value)) {
            return "The export destination became occupied; the existing file was preserved";
        }
        if ("备份改名后的回读校验失败".equals(value)) {
            return "Renamed backup verification failed";
        }
        if ("手机没有可写文件系统".equals(value)) {
            return "The phone has no writable file system";
        }
        if ("请选择 .j2cfg 文件".equals(value)) return "Select a .j2cfg file";
        if ("导出文件名无效".equals(value)) return "Invalid export file name";
        if ("Base64 数据非法".equals(value)) return "Invalid Base64 data";
        if ("Base64 数据长度非法".equals(value)) return "Invalid Base64 data length";
        if ("Base64 填充非法".equals(value)) return "Invalid Base64 padding";
        if ("JSON 请求过大".equals(value)) return "The JSON request is too large";
        if (value.startsWith("缺少字段：")) {
            return "Missing field: " + value.substring("缺少字段：".length());
        }
        if (value.startsWith("字段不是整数：")) {
            return "Field is not an integer: " + value.substring("字段不是整数：".length());
        }
        if (value.startsWith("字段不是布尔值：")) {
            return "Field is not a boolean: " + value.substring("字段不是布尔值：".length());
        }
        if (value.startsWith("档案标识重复：")) {
            return "Duplicate profile ID: " + value.substring("档案标识重复：".length());
        }
        if (value.endsWith("不能为空")) {
            return fieldLabel(value.substring(0, value.length() - "不能为空".length()))
                    + " cannot be empty";
        }
        if (value.endsWith("过长")) {
            if (value.startsWith("档案名称")) return "Profile name is too long";
            if (value.startsWith("聊天端点")) return "Chat endpoint is too long";
            if (value.startsWith("模型端点")) return "Models endpoint is too long";
            if (value.startsWith("API 密钥")) return "API key is too long";
            if (value.startsWith("模型名称")) return "Model name is too long";
            if (value.startsWith("系统提示词")) return "System prompt is too long";
            if (value.startsWith("思考强度")) return "Reasoning effort is too long";
            if (value.startsWith("预设标识")) return "Preset ID is too long";
            if (value.startsWith("档案标识")) return "Profile ID is too long";
            if (value.startsWith("活动档案标识")) return "Active profile ID is too long";
        }
        return null;
    }

    private String parserError(String original) {
        String value = original;
        String location = "";
        int marker = value.indexOf("（字节 ");
        if (marker >= 0 && value.endsWith("）")) {
            location = " (byte " + value.substring(marker + "（字节 ".length(),
                    value.length() - 1) + ")";
            value = value.substring(0, marker);
        } else {
            marker = value.indexOf("（位置 ");
            if (marker >= 0 && value.endsWith("）")) {
                location = " (position " + value.substring(marker + "（位置 ".length(),
                        value.length() - 1) + ")";
                value = value.substring(0, marker);
            }
        }

        String translated = null;
        if ("UTF-8 字符不完整".equals(value)) translated = "Incomplete UTF-8 character";
        else if ("UTF-8 连续字节非法".equals(value)) translated = "Invalid UTF-8 continuation byte";
        else if ("UTF-8 字符非法".equals(value)) translated = "Invalid UTF-8 character";
        else if ("UTF-8 起始字节非法".equals(value)) translated = "Invalid UTF-8 leading byte";
        else if ("字符串未结束".equals(value)) translated = "Unterminated string";
        else if ("字符串转义不完整".equals(value)) translated = "Incomplete string escape";
        else if ("字符串转义非法".equals(value) || "未知字符串转义".equals(value)) {
            translated = "Invalid string escape";
        } else if ("字符串包含控制字符".equals(value)) translated = "String contains a control character";
        else if ("Unicode 转义不完整".equals(value)) translated = "Incomplete Unicode escape";
        else if ("Unicode 转义非法".equals(value)) translated = "Invalid Unicode escape";
        else if ("JSON 容器未结束".equals(value)) translated = "Unterminated JSON container";
        else if ("缺少 JSON 根对象".equals(value)) translated = "Missing JSON root object";
        else if ("缺少 JSON 容器".equals(value)) translated = "Missing JSON container";
        else if ("响应中没有 data 数组".equals(value)) translated = "The response has no data array";
        else if ("JSON 根对象后存在多余内容".equals(value)
                || "JSON 尾部存在多余内容".equals(value)) {
            translated = "Unexpected content after the JSON root";
        } else if ("无法识别的 JSON 字符".equals(value)
                || "无法识别的 JSON 值".equals(value)) {
            translated = "Unrecognized JSON value";
        } else if ("模型列表根值必须是对象".equals(value)) {
            translated = "The model-list root must be an object";
        } else if ("模型 ID 过长".equals(value)) translated = "Model ID is too long";
        else if ("JSON 嵌套过深".equals(value)) translated = "JSON nesting is too deep";
        else if ("JSON 结束符多余".equals(value)) translated = "Unexpected JSON closing delimiter";
        else if ("JSON 容器结束符不匹配".equals(value)) {
            translated = "Mismatched JSON closing delimiter";
        } else if ("对象值不完整".equals(value)) translated = "Incomplete object value";
        else if ("数组值不完整".equals(value)) translated = "Incomplete array value";
        else if ("对象键必须是字符串".equals(value)) translated = "Object keys must be strings";
        else if ("数组中不能出现对象键".equals(value)) translated = "An array cannot contain an object key";
        else if ("冒号位置非法".equals(value)) translated = "Invalid colon position";
        else if ("逗号位置非法".equals(value)) translated = "Invalid comma position";
        else if ("JSON 常量或数字非法".equals(value)) translated = "Invalid JSON literal or number";
        else if ("JSON 值位置非法".equals(value)) translated = "Invalid JSON value position";
        else if ("缺少 JSON 值".equals(value)) translated = "Missing JSON value";
        else if ("数字非法".equals(value)) translated = "Invalid number";
        else if ("常量非法".equals(value)) translated = "Invalid literal";
        else if (value.startsWith("需要字符 ")) {
            translated = "Expected character " + value.substring("需要字符 ".length());
        }
        return translated == null ? null : translated + location;
    }

    private String fieldLabel(String value) {
        if ("档案名称".equals(value)) return "Profile name";
        if ("聊天端点".equals(value)) return "Chat endpoint";
        if ("模型端点".equals(value)) return "Models endpoint";
        if ("API 密钥".equals(value)) return "API key";
        if ("模型名称".equals(value)) return "Model name";
        if ("系统提示词".equals(value)) return "System prompt";
        if ("思考强度".equals(value)) return "Reasoning effort";
        if ("预设标识".equals(value)) return "Preset ID";
        if ("档案标识".equals(value)) return "Profile ID";
        if ("活动档案标识".equals(value)) return "Active profile ID";
        return value;
    }

    private String bytesInside(String value) {
        int start = value.indexOf('（');
        int end = value.indexOf(" 字节）");
        if (start < 0 || end <= start) return "?";
        return value.substring(start + 1, end);
    }
}
