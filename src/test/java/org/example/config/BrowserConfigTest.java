package org.example.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrowserConfigTest {

    @Test
    void getExecutablePath_DefaultsToEmptyString() {
        BrowserConfig config = new BrowserConfig("", true, "");
        assertEquals("", config.getExecutablePath());
    }

    @Test
    void getExecutablePath_StoresPathFromConstructor() {
        String path = "/usr/bin/google-chrome-stable";
        BrowserConfig config = new BrowserConfig(path, true, "");
        assertEquals(path, config.getExecutablePath());
    }

    @Test
    void getExecutablePath_WithWindowsPath_StoresCorrectly() {
        String winPath = "C:/Program Files/Mozilla Firefox/firefox.exe";
        BrowserConfig config = new BrowserConfig(winPath, true, "");
        assertEquals(winPath, config.getExecutablePath());
    }

    @Test
    void getExecutablePath_WithBlankValue_StoresBlank() {
        BrowserConfig config = new BrowserConfig("  ", true, "");
        assertEquals("  ", config.getExecutablePath());
    }

    @Test
    void isHeadless_DefaultsToTrue() {
        BrowserConfig config = new BrowserConfig("", true, "");
        assertTrue(config.isHeadless());
    }

    @Test
    void isHeadless_StoresValueFromConstructor() {
        BrowserConfig config = new BrowserConfig("", false, "");
        assertFalse(config.isHeadless());
    }

    @Test
    void getExtraArgs_DefaultsToEmptyList() {
        BrowserConfig config = new BrowserConfig("", true, "");
        assertTrue(config.getExtraArgs().isEmpty());
    }

    @Test
    void getExtraArgs_WithBlankValue_KeepsListEmpty() {
        BrowserConfig config = new BrowserConfig("", true, "  ");
        assertTrue(config.getExtraArgs().isEmpty());
    }

    @Test
    void getExtraArgs_WithNull_KeepsListEmpty() {
        BrowserConfig config = new BrowserConfig("", true, null);
        assertTrue(config.getExtraArgs().isEmpty());
    }

    @Test
    void getExtraArgs_SplitsCommaSeparatedValues() {
        BrowserConfig config = new BrowserConfig("", true, "--no-sandbox,--disable-gpu");
        assertEquals(List.of("--no-sandbox", "--disable-gpu"), config.getExtraArgs());
    }

    @Test
    void getExtraArgs_WithSingleValue_StoresSingleElement() {
        BrowserConfig config = new BrowserConfig("", true, "--no-sandbox");
        assertEquals(List.of("--no-sandbox"), config.getExtraArgs());
    }
}
