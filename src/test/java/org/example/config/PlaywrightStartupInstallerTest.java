package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlaywrightStartupInstallerTest {

    /** Ersetzt den echten Download durch einen Recorder. */
    private static class RecordingInstaller extends PlaywrightBrowserInstaller {
        final List<String> installed = new ArrayList<>();

        @Override
        public synchronized void ensureInstalled(String browser) {
            installed.add(browser);
        }
    }

    private final RecordingInstaller installer = new RecordingInstaller();
    private final PlaywrightStartupInstaller startupInstaller = new PlaywrightStartupInstaller(installer);

    /** Env-Vars haben in ConfigReader Vorrang vor System-Properties – dann Tests überspringen. */
    @BeforeEach
    void assumeNoEnvOverride() {
        assumeTrue(System.getenv("PLAYWRIGHT_MODE") == null
                        && System.getenv("PLAYWRIGHT_MIRROR_ENABLED") == null
                        && System.getenv("PLAYWRIGHT_INSTALL_ON_STARTUP") == null
                        && System.getenv("PLAYWRIGHT_BROWSER") == null,
                "PLAYWRIGHT_* ist auf dieser Maschine gesetzt und hat Vorrang vor System.setProperty");
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mode");
        System.clearProperty("playwright.mirror.enabled");
        System.clearProperty("playwright.install.on-startup");
        System.clearProperty("playwright.browser");
        System.clearProperty("browser");
    }

    @Test
    void run_InstallsStartupBrowserWhenMirrorAndOnStartupEnabled() {
        System.setProperty("playwright.mirror.enabled", "true");
        System.setProperty("playwright.install.on-startup", "true");
        System.setProperty("playwright.browser", "firefox");

        startupInstaller.run(null);

        assertEquals(List.of("firefox"), installer.installed);
    }

    @Test
    void run_SystemPropertyBrowserWinsOverConfig() {
        System.setProperty("playwright.mirror.enabled", "true");
        System.setProperty("playwright.install.on-startup", "true");
        System.setProperty("playwright.browser", "firefox");
        System.setProperty("browser", "webkit");

        startupInstaller.run(null);

        assertEquals(List.of("webkit"), installer.installed);
    }

    @Test
    void run_SkipsInRemoteModeEvenWhenOnStartupEnabled() {
        System.setProperty("playwright.mode", "remote");
        System.setProperty("playwright.mirror.enabled", "true");
        System.setProperty("playwright.install.on-startup", "true");

        startupInstaller.run(null);

        assertTrue(installer.installed.isEmpty());
    }

    @Test
    void run_SkipsWhenInstallOnStartupDisabled() {
        System.setProperty("playwright.mirror.enabled", "true");
        System.setProperty("playwright.install.on-startup", "false");

        startupInstaller.run(null);

        assertTrue(installer.installed.isEmpty());
    }

    @Test
    void run_SkipsWhenMirrorDisabled() {
        System.setProperty("playwright.mirror.enabled", "false");
        System.setProperty("playwright.install.on-startup", "true");

        startupInstaller.run(null);

        assertTrue(installer.installed.isEmpty());
    }

    @Test
    void run_FailsFastOnUnknownMode() {
        System.setProperty("playwright.mode", "sidecar");

        assertThrows(IllegalArgumentException.class, () -> startupInstaller.run(null));
        assertTrue(installer.installed.isEmpty());
    }
}
