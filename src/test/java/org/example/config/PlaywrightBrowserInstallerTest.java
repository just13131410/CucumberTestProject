package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlaywrightBrowserInstallerTest {

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mirror.enabled");
    }

    /** Test-Installer, der den echten Download-Subprozess durch einen Recorder ersetzt. */
    private static class RecordingInstaller extends PlaywrightBrowserInstaller {
        final AtomicInteger calls = new AtomicInteger();
        String lastBrowser;
        String lastHost;
        String lastChromiumHost;
        String lastPath;
        int exitCode = 0;

        @Override
        protected int runInstall(String browser, String downloadHost, String chromiumDownloadHost,
                                 String browsersPath) {
            calls.incrementAndGet();
            this.lastBrowser = browser;
            this.lastHost = downloadHost;
            this.lastChromiumHost = chromiumDownloadHost;
            this.lastPath = browsersPath;
            return exitCode;
        }
    }

    @Test
    void installArguments_AreInstallAndBrowser() {
        assertEquals(List.of("install", "chromium"),
                PlaywrightBrowserInstaller.installArguments("chromium"));
    }

    @Test
    void buildDriverEnv_IncludesHostsAndPath() {
        Map<String, String> env = PlaywrightBrowserInstaller.buildDriverEnv(
                "http://playwright-mirror:10123",
                "http://playwright-mirror:10123/builds/cft",
                "/ms-playwright");
        assertEquals("http://playwright-mirror:10123", env.get("PLAYWRIGHT_DOWNLOAD_HOST"));
        assertEquals("http://playwright-mirror:10123/builds/cft",
                env.get("PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST"));
        assertEquals("/ms-playwright", env.get("PLAYWRIGHT_BROWSERS_PATH"));
    }

    @Test
    void buildDriverEnv_OmitsBlankPath() {
        Map<String, String> env = PlaywrightBrowserInstaller.buildDriverEnv(
                "http://playwright-mirror:10123",
                "http://playwright-mirror:10123/builds/cft",
                null);
        assertTrue(env.containsKey("PLAYWRIGHT_DOWNLOAD_HOST"));
        assertTrue(env.containsKey("PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST"));
        assertFalse(env.containsKey("PLAYWRIGHT_BROWSERS_PATH"));
    }

    @Test
    void ensureInstalled_NoOpWhenMirrorDisabled() {
        System.setProperty("playwright.mirror.enabled", "false");
        RecordingInstaller installer = new RecordingInstaller();
        installer.ensureInstalled("chromium");
        assertEquals(0, installer.calls.get());
    }

    @Test
    void ensureInstalled_InstallsOnceAndMemoizes() {
        System.setProperty("playwright.mirror.enabled", "true");
        RecordingInstaller installer = new RecordingInstaller();
        installer.ensureInstalled("chromium");
        installer.ensureInstalled("chromium");
        assertEquals(1, installer.calls.get());
        assertEquals("chromium", installer.lastBrowser);
    }

    @Test
    void ensureInstalled_NormalizesChromeToChromium() {
        System.setProperty("playwright.mirror.enabled", "true");
        RecordingInstaller installer = new RecordingInstaller();
        installer.ensureInstalled("chrome");
        assertEquals("chromium", installer.lastBrowser);
    }

    @Test
    void ensureInstalled_ThrowsOnNonZeroExit() {
        System.setProperty("playwright.mirror.enabled", "true");
        RecordingInstaller installer = new RecordingInstaller();
        installer.exitCode = 1;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> installer.ensureInstalled("chromium"));
        assertTrue(ex.getMessage().contains("chromium"));
    }

    @Test
    void ensureInstalled_RetriesAfterFailure() {
        System.setProperty("playwright.mirror.enabled", "true");
        RecordingInstaller installer = new RecordingInstaller();
        installer.exitCode = 1;
        assertThrows(IllegalStateException.class, () -> installer.ensureInstalled("chromium"));
        // Nach einem Fehlschlag darf kein Memo gesetzt sein → erneuter Versuch möglich.
        installer.exitCode = 0;
        installer.ensureInstalled("chromium");
        assertEquals(2, installer.calls.get());
    }
}
