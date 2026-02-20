package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrowserConfigTest {

    @AfterEach
    void resetStaticState() {
        new BrowserConfig().setExecutablePath("");
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
}
