package com.chihoko.j2mellm.i18n;

import com.chihoko.j2mellm.model.ProviderPresets;
import com.chihoko.j2mellm.model.ProviderProfile;

public final class I18nSelfTest {
    public static void main(String[] args) {
        testResolution();
        testCatalogCompleteness();
        testErrors();
        testProfileNames();
        System.out.println("I18nSelfTest passed");
    }

    private static void testResolution() {
        equal(I18n.EN, I18n.resolveLanguage(I18n.AUTO, null), "auto null");
        equal(I18n.EN, I18n.resolveLanguage(I18n.AUTO, "en-US"), "auto English");
        equal(I18n.ZH, I18n.resolveLanguage(I18n.AUTO, "zh"), "auto zh");
        equal(I18n.ZH, I18n.resolveLanguage(I18n.AUTO, "zh-CN"), "auto zh-CN");
        equal(I18n.ZH, I18n.resolveLanguage(I18n.AUTO, "ZH_Hans"), "auto ZH_Hans");
        equal(I18n.EN, I18n.resolveLanguage(I18n.EN, "zh-CN"), "forced English");
        equal(I18n.ZH, I18n.resolveLanguage(I18n.ZH, "en-US"), "forced Chinese");

        I18n.initialize(99, "zh-CN");
        equal(I18n.AUTO, I18n.getPreference(), "invalid preference normalized");
        equal(I18n.ZH, I18n.getLanguage(), "normalized auto locale");
    }

    private static void testCatalogCompleteness() {
        assertCatalog(I18n.ZH);
        assertCatalog(I18n.EN);
    }

    private static void assertCatalog(int language) {
        I18n.initialize(language, null);
        int i;
        for (i = 0; i < TextId.COUNT; i++) {
            String value = I18n.text(i);
            if (value == null || value.length() == 0 || "?".equals(value)) {
                throw new AssertionError("Missing text " + i + " for language " + language);
            }
        }
    }

    private static void testErrors() {
        I18n.initialize(I18n.EN, null);
        equal("Directory does not exist", I18n.error("目录不存在"), "known error");
        equal("The models endpoint is empty", I18n.error("模型列表地址为空"),
                "backend error");
        equal("Unterminated string (byte 17)", I18n.error("字符串未结束（字节 17）"),
                "located parser error");
        equal("The configuration file does not exist", I18n.error("配置文件不存在"),
                "file error");
        equal("Server detail", I18n.error("Server detail"), "server detail");
        equal("Unknown error", I18n.error(null), "null error");

        I18n.initialize(I18n.ZH, null);
        equal("目录不存在", I18n.error("目录不存在"), "Chinese error");
    }

    private static void testProfileNames() {
        ProviderProfile custom = ProviderPresets.create(ProviderPresets.CUSTOM);
        I18n.initialize(I18n.EN, null);
        equal("Custom", I18n.profileName(custom), "default custom");

        custom.name = "自定义（旧配置）";
        equal("Custom (legacy configuration)", I18n.profileName(custom), "legacy custom");

        custom.name = "My gateway";
        equal("My gateway", I18n.profileName(custom), "user name");

        I18n.initialize(I18n.ZH, null);
        custom.name = "Custom";
        equal("自定义", I18n.profileName(custom), "English default in Chinese UI");
    }

    private static void equal(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
