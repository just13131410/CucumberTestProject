package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlaywrightMirrorConfigTest {

    /**
     * ConfigReader prüft Env-Var vor System-Property (siehe ConfigReader.get()). Ist
     * PLAYWRIGHT_DOWNLOAD_HOST auf der Maschine gesetzt (z.B. dauerhaft per setx für den lokalen
     * Playwright-Mirror), gewinnt sie immer gegen die hier per System.setProperty gesetzten
     * Test-Werte. Die betroffenen Tests prüfen gezielt den Fallback ohne Env-Var und werden dann
     * übersprungen statt falsch fehlzuschlagen.
     */
    private static void assumeNoDownloadHostEnvVar() {
        assumeTrue(System.getenv("PLAYWRIGHT_DOWNLOAD_HOST") == null,
                "PLAYWRIGHT_DOWNLOAD_HOST ist auf dieser Maschine gesetzt und hat Vorrang vor System.setProperty");
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mirror.enabled");
        System.clearProperty("playwright.install.on-startup");
        System.clearProperty("playwright.download.host");
        System.clearProperty("playwright.chromium.download.host");
        System.clearProperty("playwright.browsers.path");
        System.clearProperty("playwright.browser");
        System.clearProperty("browser");
    }

    @Test
    void normalizeBrowser_MapsChromeAndBlankToChromium() {
        assertEquals("chromium", PlaywrightMirrorConfig.normalizeBrowser(null));
        assertEquals("chromium", PlaywrightMirrorConfig.normalizeBrowser(""));
        assertEquals("chromium", PlaywrightMirrorConfig.normalizeBrowser("  "));
        assertEquals("chromium", PlaywrightMirrorConfig.normalizeBrowser("chromium"));
        assertEquals("chromium", PlaywrightMirrorConfig.normalizeBrowser("Chrome"));
    }

    @Test
    void normalizeBrowser_KeepsFirefoxAndWebkit() {
        assertEquals("firefox", PlaywrightMirrorConfig.normalizeBrowser("Firefox"));
        assertEquals("webkit", PlaywrightMirrorConfig.normalizeBrowser("WebKit"));
    }

    @Test
    void normalizeBrowser_RejectsUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> PlaywrightMirrorConfig.normalizeBrowser("opera"));
    }

    @Test
    void isMirrorEnabled_HonorsSystemProperty() {
        System.setProperty("playwright.mirror.enabled", "false");
        assertFalse(PlaywrightMirrorConfig.isMirrorEnabled());
        System.setProperty("playwright.mirror.enabled", "true");
        assertTrue(PlaywrightMirrorConfig.isMirrorEnabled());
    }

    @Test
    void isInstallOnStartup_HonorsSystemProperty() {
        System.setProperty("playwright.install.on-startup", "true");
        assertTrue(PlaywrightMirrorConfig.isInstallOnStartup());
        System.setProperty("playwright.install.on-startup", "false");
        assertFalse(PlaywrightMirrorConfig.isInstallOnStartup());
    }

    @Test
    void downloadHost_HonorsSystemProperty() {
        assumeNoDownloadHostEnvVar();
        System.setProperty("playwright.download.host", "http://custom-mirror:9999");
        assertEquals("http://custom-mirror:9999", PlaywrightMirrorConfig.downloadHost());
    }

    @Test
    void chromiumDownloadHost_DerivedFromBaseWithCftPrefix() {
        assumeNoDownloadHostEnvVar();
        System.setProperty("playwright.download.host", "http://custom-mirror:9999");
        assertEquals("http://custom-mirror:9999/builds/cft",
                PlaywrightMirrorConfig.chromiumDownloadHost());
    }

    @Test
    void chromiumDownloadHost_StripsTrailingSlashOnBase() {
        assumeNoDownloadHostEnvVar();
        System.setProperty("playwright.download.host", "http://custom-mirror:9999/");
        assertEquals("http://custom-mirror:9999/builds/cft",
                PlaywrightMirrorConfig.chromiumDownloadHost());
    }

    @Test
    void chromiumDownloadHost_HonorsExplicitOverride() {
        System.setProperty("playwright.chromium.download.host", "http://cft-mirror:1234/x");
        assertEquals("http://cft-mirror:1234/x", PlaywrightMirrorConfig.chromiumDownloadHost());
    }

    @Test
    void browsersPath_BlankResolvesToNull() {
        System.setProperty("playwright.browsers.path", "   ");
        assertNull(PlaywrightMirrorConfig.browsersPath());
    }

    @Test
    void browsersPath_TrimsConfiguredValue() {
        System.setProperty("playwright.browsers.path", "  /ms-playwright ");
        assertEquals("/ms-playwright", PlaywrightMirrorConfig.browsersPath());
    }

    @Test
    void startupBrowser_PrefersBrowserSystemProperty() {
        System.setProperty("browser", "firefox");
        assertEquals("firefox", PlaywrightMirrorConfig.startupBrowser());
    }

    @Test
    void startupBrowser_FallsBackToPlaywrightBrowserProperty() {
        System.setProperty("playwright.browser", "webkit");
        assertEquals("webkit", PlaywrightMirrorConfig.startupBrowser());
    }
}
