package org.example.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlaywrightRemoteConfigTest {

    /** Env-Vars haben in ConfigReader Vorrang vor System-Properties – dann Tests überspringen. */
    @BeforeEach
    void assumeNoEnvOverride() {
        assumeTrue(System.getenv("PLAYWRIGHT_MODE") == null
                        && System.getenv("PLAYWRIGHT_REMOTE_ENDPOINT") == null
                        && System.getenv("PLAYWRIGHT_REMOTE_CONNECT_TIMEOUT_MS") == null
                        && System.getenv("PLAYWRIGHT_REMOTE_CHANNEL") == null,
                "PLAYWRIGHT_REMOTE_* ist auf dieser Maschine gesetzt und hat Vorrang vor System.setProperty");
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("playwright.mode");
        System.clearProperty("playwright.remote.endpoint");
        System.clearProperty("playwright.remote.connect-timeout-ms");
        System.clearProperty("playwright.remote.channel");
    }

    @Test
    void mode_LocalByDefault() {
        assertEquals(PlaywrightRemoteConfig.MODE_LOCAL, PlaywrightRemoteConfig.mode());
        assertFalse(PlaywrightRemoteConfig.isRemoteEnabled());
    }

    @Test
    void mode_BlankFallsBackToLocal() {
        System.setProperty("playwright.mode", "  ");
        assertEquals(PlaywrightRemoteConfig.MODE_LOCAL, PlaywrightRemoteConfig.mode());
    }

    @Test
    void mode_RemoteIsTrimmedAndCaseInsensitive() {
        System.setProperty("playwright.mode", " Remote ");
        assertEquals(PlaywrightRemoteConfig.MODE_REMOTE, PlaywrightRemoteConfig.mode());
        assertTrue(PlaywrightRemoteConfig.isRemoteEnabled());
    }

    @Test
    void mode_UnknownValueThrows() {
        System.setProperty("playwright.mode", "sidecar");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, PlaywrightRemoteConfig::mode);
        assertTrue(ex.getMessage().contains("sidecar"));
    }

    @Test
    void endpoint_SetEndpointAloneDoesNotEnableRemote() {
        System.setProperty("playwright.remote.endpoint", "ws://playwright:3000/");
        assertFalse(PlaywrightRemoteConfig.isRemoteEnabled());
    }

    @Test
    void endpoint_DefaultsToSidecarWhenUnsetOrBlank() {
        assertEquals(PlaywrightRemoteConfig.DEFAULT_ENDPOINT, PlaywrightRemoteConfig.endpoint());
        System.setProperty("playwright.remote.endpoint", "   ");
        assertEquals(PlaywrightRemoteConfig.DEFAULT_ENDPOINT, PlaywrightRemoteConfig.endpoint());
    }

    @Test
    void endpoint_TrimmedWhenSet() {
        System.setProperty("playwright.remote.endpoint", " ws://playwright-server:3000/ ");
        assertEquals("ws://playwright-server:3000/", PlaywrightRemoteConfig.endpoint());
    }

    @Test
    void connectTimeout_DefaultWhenUnset() {
        assertEquals(PlaywrightRemoteConfig.DEFAULT_CONNECT_TIMEOUT_MS, PlaywrightRemoteConfig.connectTimeoutMs());
    }

    @Test
    void connectTimeout_ParsesConfiguredValue() {
        System.setProperty("playwright.remote.connect-timeout-ms", "60000");
        assertEquals(60_000, PlaywrightRemoteConfig.connectTimeoutMs());
    }

    @Test
    void connectTimeout_FallsBackOnInvalidOrNonPositiveValue() {
        System.setProperty("playwright.remote.connect-timeout-ms", "abc");
        assertEquals(PlaywrightRemoteConfig.DEFAULT_CONNECT_TIMEOUT_MS, PlaywrightRemoteConfig.connectTimeoutMs());
        System.setProperty("playwright.remote.connect-timeout-ms", "-5");
        assertEquals(PlaywrightRemoteConfig.DEFAULT_CONNECT_TIMEOUT_MS, PlaywrightRemoteConfig.connectTimeoutMs());
    }

    @Test
    void channel_NullByDefaultAndTrimmedWhenSet() {
        assertNull(PlaywrightRemoteConfig.channel());
        System.setProperty("playwright.remote.channel", " chromium ");
        assertEquals("chromium", PlaywrightRemoteConfig.channel());
    }

    @Test
    void launchOptionsJson_OnlyHeadlessWhenNoChannelAndNoArgs() {
        assertEquals("{\"headless\":true}", PlaywrightRemoteConfig.launchOptionsJson(true, null, List.of()));
        assertEquals("{\"headless\":false}", PlaywrightRemoteConfig.launchOptionsJson(false, " ", null));
    }

    @Test
    void launchOptionsJson_IncludesChannel() {
        assertEquals("{\"headless\":true,\"channel\":\"chromium\"}",
                PlaywrightRemoteConfig.launchOptionsJson(true, "chromium", List.of()));
    }

    @Test
    void launchOptionsJson_IncludesArgsAndEscapes() {
        String json = PlaywrightRemoteConfig.launchOptionsJson(true, null,
                List.of("--unsafely-treat-insecure-origin-as-secure=http://angular-gui:8080", "--x=\"q\""));
        assertEquals("{\"headless\":true,\"args\":[\"--unsafely-treat-insecure-origin-as-secure=http://angular-gui:8080\",\"--x=\\\"q\\\"\"]}", json);
    }
}
