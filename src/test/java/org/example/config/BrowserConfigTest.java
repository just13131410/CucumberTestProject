package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// BrowserConfig hält statischen Zustand; parallele Methoden würden sich gegenseitig stören.
@Execution(ExecutionMode.SAME_THREAD)
class BrowserConfigTest {

    @AfterEach
    void resetStaticState() {
        BrowserConfig config = new BrowserConfig();
        config.setExecutablePath("");
        config.setHeadless(true);
        config.setExtraArgs("");
    }

    @Test
    void getExecutablePath_DefaultsToEmptyString() {
        new BrowserConfig().setExecutablePath("");
        assertEquals("", BrowserConfig.getExecutablePath());
    }

    @Test
    void setExecutablePath_StoresPathStatically() {
        String path = "/usr/bin/google-chrome-stable";
        new BrowserConfig().setExecutablePath(path);
        assertEquals(path, BrowserConfig.getExecutablePath());
    }

    @Test
    void setExecutablePath_OverwritesPreviousValue() {
        new BrowserConfig().setExecutablePath("/old/path/firefox");
        new BrowserConfig().setExecutablePath("/new/path/chrome");
        assertEquals("/new/path/chrome", BrowserConfig.getExecutablePath());
    }

    @Test
    void setExecutablePath_WithWindowsPath_StoresCorrectly() {
        String winPath = "C:/Program Files/Mozilla Firefox/firefox.exe";
        new BrowserConfig().setExecutablePath(winPath);
        assertEquals(winPath, BrowserConfig.getExecutablePath());
    }

    @Test
    void setExecutablePath_WithBlankValue_StoresBlank() {
        new BrowserConfig().setExecutablePath("  ");
        assertEquals("  ", BrowserConfig.getExecutablePath());
    }

    @Test
    void isHeadless_DefaultsToTrue() {
        assertTrue(BrowserConfig.isHeadless());
    }

    @Test
    void setHeadless_StoresValueStatically() {
        new BrowserConfig().setHeadless(false);
        assertFalse(BrowserConfig.isHeadless());
    }

    @Test
    void getExtraArgs_DefaultsToEmptyList() {
        assertTrue(BrowserConfig.getExtraArgs().isEmpty());
    }

    @Test
    void setExtraArgs_WithBlankValue_KeepsListEmpty() {
        new BrowserConfig().setExtraArgs("  ");
        assertTrue(BrowserConfig.getExtraArgs().isEmpty());
    }

    @Test
    void setExtraArgs_WithNull_KeepsListEmpty() {
        new BrowserConfig().setExtraArgs(null);
        assertTrue(BrowserConfig.getExtraArgs().isEmpty());
    }

    @Test
    void setExtraArgs_SplitsCommaSeparatedValues() {
        new BrowserConfig().setExtraArgs("--no-sandbox,--disable-gpu");
        assertEquals(List.of("--no-sandbox", "--disable-gpu"), BrowserConfig.getExtraArgs());
    }

    @Test
    void setExtraArgs_WithSingleValue_StoresSingleElement() {
        new BrowserConfig().setExtraArgs("--no-sandbox");
        assertEquals(List.of("--no-sandbox"), BrowserConfig.getExtraArgs());
    }
}
