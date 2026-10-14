package org.example.config;

import org.example.config.BrowserLaunchPlan.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrowserLaunchPlanTest {

    private static final String CHROME = "/usr/bin/google-chrome";
    private final BrowserConfig headlessWithArgs = new BrowserConfig("", true, "--no-zygote,--disable-gpu");
    private final BrowserConfig plain = new BrowserConfig("", false, "");

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mode");
        System.clearProperty("playwright.mirror.enabled");
        System.clearProperty("playwright.remote.channel");
    }

    // ---- Modus-Auswahl und Priorität ----

    @Test
    void resolve_RemoteWinsOverMirrorAndExecutable() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", plain, true, true, CHROME, "/cache", null);
        assertEquals(Mode.REMOTE, plan.mode());
        assertTrue(plan.isRemote());
        assertNull(plan.executablePath(), "executablePath darf remote nicht gesetzt werden");
        assertEquals(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"), plan.driverEnv());
    }

    @Test
    void resolve_MirrorWinsOverExecutableAndSetsBrowsersPath() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", plain, false, true, CHROME, " /cache ", null);
        assertEquals(Mode.MIRROR, plan.mode());
        assertFalse(plan.isRemote());
        assertNull(plan.executablePath(), "Im Mirror-Modus wird ein externer Browser bewusst ignoriert");
        assertEquals(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1", "PLAYWRIGHT_BROWSERS_PATH", "/cache"),
                plan.driverEnv());
    }

    @Test
    void resolve_MirrorWithoutBrowsersPathOnlySkipsDownload() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", plain, false, true, null, "  ", null);
        assertEquals(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"), plan.driverEnv());
    }

    @Test
    void resolve_ExternalBrowserWhenMirrorDisabledAndExecutableSet() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", plain, false, false, " " + CHROME + " ", null, null);
        assertEquals(Mode.EXTERNAL_BROWSER, plan.mode());
        assertEquals(CHROME, plan.executablePath());
        assertEquals(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"), plan.driverEnv());
    }

    @Test
    void resolve_PlaywrightDefaultWithoutMirrorAndExecutable() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", plain, false, false, "  ", "/cache", null);
        assertEquals(Mode.PLAYWRIGHT_DEFAULT, plan.mode());
        assertNull(plan.executablePath());
        assertTrue(plan.driverEnv().isEmpty(), "Playwright soll seinen Browser selbst laden dürfen");
    }

    // ---- Browser-Normalisierung ----

    @Test
    void resolve_NormalizesBrowserName() {
        assertEquals("chromium", BrowserLaunchPlan.resolve(null, plain, false, false, null, null, null).browserName());
        assertEquals("chromium", BrowserLaunchPlan.resolve(" Chrome ", plain, false, false, null, null, null).browserName());
        assertEquals("firefox", BrowserLaunchPlan.resolve("FIREFOX", plain, false, false, null, null, null).browserName());
        assertEquals("webkit", BrowserLaunchPlan.resolve("webkit", plain, true, false, null, null, null).browserName());
    }

    @Test
    void resolve_UnknownBrowserThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> BrowserLaunchPlan.resolve("edge", plain, true, false, null, null, null));
    }

    // ---- Launch-Optionen aus BrowserConfig und Channel ----

    @Test
    void resolve_TakesHeadlessAndArgsFromBrowserConfig() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", headlessWithArgs, false, true, null, null, null);
        assertTrue(plan.headless());
        assertEquals(List.of("--no-zygote", "--disable-gpu"), plan.args());
    }

    @Test
    void resolve_ChannelOnlyInRemoteMode() {
        assertEquals("chromium",
                BrowserLaunchPlan.resolve("chromium", plain, true, false, null, null, " chromium ").channel());
        assertNull(BrowserLaunchPlan.resolve("chromium", plain, false, true, null, null, "chromium").channel());
        assertNull(BrowserLaunchPlan.resolve("chromium", plain, true, false, null, null, "  ").channel());
    }

    @Test
    void launchOptionsJson_ContainsHeadlessChannelAndArgs() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", headlessWithArgs, true, false, null, null, "chromium");
        assertEquals("{\"headless\":true,\"channel\":\"chromium\",\"args\":[\"--no-zygote\",\"--disable-gpu\"]}",
                plan.launchOptionsJson());
    }

    @Test
    void plan_IsImmutable() {
        BrowserLaunchPlan plan = BrowserLaunchPlan.resolve("chromium", headlessWithArgs, true, false, null, null, null);
        assertThrows(UnsupportedOperationException.class, () -> plan.driverEnv().put("X", "1"));
        assertThrows(UnsupportedOperationException.class, () -> plan.args().add("--x"));
    }

    // ---- fromConfig: Verdrahtung mit der echten Konfiguration ----

    @Test
    void fromConfig_RemoteModeFromConfiguration() {
        assumeTrue(System.getenv("PLAYWRIGHT_MODE") == null && System.getenv("PLAYWRIGHT_REMOTE_CHANNEL") == null);
        System.setProperty("playwright.mode", "remote");
        System.setProperty("playwright.remote.channel", "chromium");

        BrowserLaunchPlan plan = BrowserLaunchPlan.fromConfig("firefox", plain);

        assertEquals(Mode.REMOTE, plan.mode());
        assertEquals("firefox", plan.browserName());
        assertEquals("chromium", plan.channel());
    }

    @Test
    void fromConfig_LocalModeUsesMirrorWhenEnabled() {
        assumeTrue(System.getenv("PLAYWRIGHT_MODE") == null && System.getenv("PLAYWRIGHT_MIRROR_ENABLED") == null);
        System.setProperty("playwright.mode", "local");
        System.setProperty("playwright.mirror.enabled", "true");

        assertEquals(Mode.MIRROR, BrowserLaunchPlan.fromConfig("chromium", plain).mode());
    }
}
