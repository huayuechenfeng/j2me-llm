package com.chihoko.j2mellm.i18n;

import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

/** Small MIDP-2.0-compatible language selector with no optional i18n API dependency. */
public final class I18n {
    public static final int AUTO = 0;
    public static final int ZH = 1;
    public static final int EN = 2;

    private static int preference = AUTO;
    private static int language = EN;
    private static TextCatalog catalog;

    private I18n() { }

    public static void initialize(int selectedPreference, String locale) {
        preference = normalizePreference(selectedPreference);
        language = resolveLanguage(preference, locale);
        catalog = language == ZH ? (TextCatalog) new TextZh() : (TextCatalog) new TextEn();
    }

    public static int resolveLanguage(int selectedPreference, String locale) {
        int normalized = normalizePreference(selectedPreference);
        if (normalized == ZH || normalized == EN) return normalized;
        if (locale != null) {
            String lower = locale.toLowerCase();
            if (lower.equals("zh") || lower.startsWith("zh-") || lower.startsWith("zh_")) {
                return ZH;
            }
        }
        return EN;
    }

    public static int getPreference() {
        return preference;
    }

    public static int getLanguage() {
        ensureCatalog();
        return language;
    }

    public static String text(int id) {
        ensureCatalog();
        String value = catalog.text(id);
        return value == null ? "?" : value;
    }

    /** Localizes known application errors while preserving server-provided details. */
    public static String error(String value) {
        if (value == null) return text(TextId.UNKNOWN_ERROR);
        ensureCatalog();
        return catalog.error(value);
    }

    /** Built-in default names are UI labels; user-supplied names remain untouched. */
    public static String profileName(ProviderProfile profile) {
        if (profile == null) return "J2ME LLM";
        String name = profile.name == null ? "" : profile.name.trim();
        if (ProviderPresets.CUSTOM.equals(profile.presetId)) {
            if (name.length() == 0 || "自定义".equals(name) || "Custom".equals(name)) {
                return text(TextId.CUSTOM);
            }
            if ("自定义（旧配置）".equals(name) || "Custom (legacy configuration)".equals(name)) {
                return text(TextId.CUSTOM_LEGACY);
            }
        }
        return profile.displayName();
    }

    private static int normalizePreference(int value) {
        return value == ZH || value == EN ? value : AUTO;
    }

    private static void ensureCatalog() {
        if (catalog == null) initialize(AUTO, null);
    }
}
