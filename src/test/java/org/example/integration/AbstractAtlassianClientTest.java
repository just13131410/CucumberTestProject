package org.example.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AbstractAtlassianClientTest {

    private static final String CONNECT_KEY = "integration.http.connect-timeout-ms";
    private static final String READ_KEY = "integration.http.read-timeout-ms";

    @AfterEach
    void clearProps() {
        System.clearProperty(CONNECT_KEY);
        System.clearProperty(READ_KEY);
    }

    private static void assumeNoEnvOverride() {
        assumeTrue(System.getenv("INTEGRATION_HTTP_CONNECT_TIMEOUT_MS") == null
                && System.getenv("INTEGRATION_HTTP_READ_TIMEOUT_MS") == null);
    }

    @Test
    void abbreviate_ShortBodyUnchanged_NullBecomesEmpty() {
        assertEquals("{\"error\":\"x\"}", AbstractAtlassianClient.abbreviate("{\"error\":\"x\"}"));
        assertEquals("", AbstractAtlassianClient.abbreviate(null));
    }

    @Test
    void abbreviate_LongBodyCutWithLengthHint() {
        String body = "a".repeat(1000);

        String result = AbstractAtlassianClient.abbreviate(body);

        assertTrue(result.startsWith("a".repeat(AbstractAtlassianClient.MAX_LOGGED_BODY_CHARS)));
        assertTrue(result.endsWith("(1000 Zeichen)"));
        assertTrue(result.length() < 400);
    }

    @Test
    void timeoutFromConfig_DefaultWhenUnsetInvalidOrNonPositive() {
        assumeNoEnvOverride();
        Duration def = Duration.ofSeconds(7);
        assertEquals(def, AbstractAtlassianClient.timeoutFromConfig("integration.http.unset-timeout-ms", def));
        System.setProperty(CONNECT_KEY, "abc");
        assertEquals(def, AbstractAtlassianClient.timeoutFromConfig(CONNECT_KEY, def));
        System.setProperty(CONNECT_KEY, "0");
        assertEquals(def, AbstractAtlassianClient.timeoutFromConfig(CONNECT_KEY, def));
    }

    @Test
    void timeoutFromConfig_ParsesMilliseconds() {
        assumeNoEnvOverride();
        System.setProperty(CONNECT_KEY, " 2500 ");
        assertEquals(Duration.ofMillis(2500), AbstractAtlassianClient.timeoutFromConfig(CONNECT_KEY, Duration.ofSeconds(1)));
    }

    @Test
    void defaultRestTemplate_HasConnectAndReadTimeouts() {
        assumeNoEnvOverride();
        System.setProperty(READ_KEY, "45000");

        RestTemplate template = AbstractAtlassianClient.defaultRestTemplate();

        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) template.getRequestFactory();
        assertEquals(10_000, ((Number) ReflectionTestUtils.getField(factory, "connectTimeout")).intValue());
        assertEquals(45_000, ((Number) ReflectionTestUtils.getField(factory, "readTimeout")).intValue());
    }
}
