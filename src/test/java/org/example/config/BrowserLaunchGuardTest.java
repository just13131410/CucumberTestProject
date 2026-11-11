package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrowserLaunchGuardTest {

    private static final String MISSING_EXE =
            "Executable doesn't exist at C:\\pw\\chromium_headless_shell-1208\\chrome-headless-shell.exe";

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mirror.enabled");
        System.clearProperty("playwright.mode");
    }

    /** Installer, der den echten Download-Subprozess durch einen Zähler ersetzt. */
    private static class RecordingInstaller extends PlaywrightBrowserInstaller {
        final AtomicInteger installs = new AtomicInteger();
        int exitCode = 0;

        @Override
        protected int runInstall(String browser, String downloadHost, String chromiumDownloadHost,
                                 String browsersPath) {
            installs.incrementAndGet();
            return exitCode;
        }
    }

    private final RecordingInstaller installer = new RecordingInstaller();
    private final BrowserLaunchGuard guard = new BrowserLaunchGuard(installer, new BrowserConfig("", true, ""));
    private final AtomicInteger launches = new AtomicInteger();
    private final AtomicInteger cleanups = new AtomicInteger();

    private void mirrorEnabled(boolean enabled) {
        assumeTrue(System.getenv("PLAYWRIGHT_MODE") == null);
        assumeTrue(System.getenv("PLAYWRIGHT_MIRROR_ENABLED") == null);
        System.setProperty("playwright.mirror.enabled", String.valueOf(enabled));
    }

    @Test
    void launch_ReturnsResult_InstallsOnce() {
        mirrorEnabled(true);
        assertEquals("page", guard.launch("chromium", () -> "page", cleanups::incrementAndGet));
        assertEquals(1, installer.installs.get());
        assertEquals(0, cleanups.get());
    }

    @Test
    void launch_MissingExecutable_CleansUpReinstallsAndRetries() {
        mirrorEnabled(true);
        String result = guard.launch("chromium", () -> {
            if (launches.incrementAndGet() == 1) {
                throw new RuntimeException(MISSING_EXE);
            }
            return "page";
        }, cleanups::incrementAndGet);

        assertEquals("page", result);
        assertEquals(2, launches.get());
        assertEquals(1, cleanups.get());
        // 1x ensureInstalled + 1x erzwungene Neuinstallation am Memo vorbei
        assertEquals(2, installer.installs.get());
    }

    @Test
    void launch_StillMissing_ThrowsHintWithPath() {
        mirrorEnabled(true);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.launch("chromium", () -> {
                    launches.incrementAndGet();
                    throw new RuntimeException(MISSING_EXE);
                }, cleanups::incrementAndGet));

        assertTrue(ex.getMessage().contains("INSTALLATION_COMPLETE"));
        assertTrue(ex.getMessage().contains("chromium_headless_shell-1208"));
        assertEquals(2, launches.get());
        assertEquals(2, installer.installs.get());
    }

    @Test
    void launch_ReinstallFails_ThrowsWithExitCode() {
        mirrorEnabled(true);
        guard.launch("chromium", () -> "page", () -> { });   // Memo setzen
        installer.exitCode = 1;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.launch("chromium", () -> {
                    throw new RuntimeException(MISSING_EXE);
                }, cleanups::incrementAndGet));
        assertTrue(ex.getMessage().contains("Exit-Code 1"));
    }

    @Test
    void launch_OtherError_RethrownWithoutRetry() {
        mirrorEnabled(true);
        IllegalStateException boom = new IllegalStateException("boom");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.launch("chromium", () -> {
                    launches.incrementAndGet();
                    throw boom;
                }, cleanups::incrementAndGet));

        assertSame(boom, ex);
        assertEquals(1, launches.get());
        assertEquals(0, cleanups.get());
        assertEquals(1, installer.installs.get());
    }

    @Test
    void launch_MirrorDisabled_NoInstallNoRetry() {
        mirrorEnabled(false);
        RuntimeException missing = new RuntimeException(MISSING_EXE);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> guard.launch("chromium", () -> {
                    launches.incrementAndGet();
                    throw missing;
                }, cleanups::incrementAndGet));

        assertSame(missing, ex);
        assertEquals(1, launches.get());
        assertEquals(0, cleanups.get());
        assertEquals(0, installer.installs.get());
    }

    @Test
    void isMissingExecutable_SearchesCauseChain() {
        assertTrue(BrowserLaunchGuard.isMissingExecutable(
                new RuntimeException("wrap", new RuntimeException("Executable doesn't exist at x"))));
        assertFalse(BrowserLaunchGuard.isMissingExecutable(new RuntimeException("other")));
        assertFalse(BrowserLaunchGuard.isMissingExecutable(new RuntimeException((String) null)));
        assertFalse(BrowserLaunchGuard.isMissingExecutable(null));
    }

    @Test
    void describe_MirrorWithDefaultCache() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", new BrowserConfig("", false, ""),
                false, true, null, null, null);
        assertEquals("browser=chromium, mode=MIRROR, headless=false, browsersPath=<OS-Standard>, "
                + "executablePath=-, channel=-", BrowserLaunchGuard.describe(plan));
    }

    @Test
    void describe_ExternalBrowserAndCustomCache() {
        BrowserLaunchPlan external = BrowserLaunchPlan.resolve("chrome", new BrowserConfig("", true, ""),
                false, false, "C:/chrome.exe", null, null);
        assertTrue(BrowserLaunchGuard.describe(external).contains("mode=EXTERNAL_BROWSER"));
        assertTrue(BrowserLaunchGuard.describe(external).contains("executablePath=C:/chrome.exe"));

        BrowserLaunchPlan cached = BrowserLaunchPlan.resolve("chromium", new BrowserConfig("", true, ""),
                false, true, null, "/ms-playwright", null);
        assertTrue(BrowserLaunchGuard.describe(cached).contains("browsersPath=/ms-playwright"));
    }
}
