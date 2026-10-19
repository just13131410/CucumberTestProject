package org.example.utils;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OAuthTokenProviderTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"access_token\":\"tok-123\",\"token_type\":\"Bearer\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        for (String key : new String[]{"oauth.token-url", "oauth.client-id", "oauth.client-secret",
                "env.oauthenv.oauth.client-secret", ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY}) {
            System.clearProperty(key);
        }
    }

    private String tokenUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    @Test
    void buildForm_ClientCredentials_ContainsClientDataAndScope() {
        Map<String, String> form = OAuthTokenProvider.buildForm("client_credentials", "be-client", "s3cret",
                null, null, "openid");

        assertEquals(Map.of("grant_type", "client_credentials", "client_id", "be-client",
                "client_secret", "s3cret", "scope", "openid"), form);
    }

    @Test
    void buildForm_PasswordGrant_ContainsUserCredentials() {
        Map<String, String> form = OAuthTokenProvider.buildForm("password", "be-client", null,
                "lea", "geheim", null);

        assertEquals("lea", form.get("username"));
        assertEquals("geheim", form.get("password"));
        assertFalse(form.containsKey("client_secret"));
    }

    @Test
    void buildForm_PasswordGrantWithoutUser_Throws() {
        assertThrows(IllegalStateException.class,
                () -> OAuthTokenProvider.buildForm("password", "be-client", null, null, "x", null));
    }

    @Test
    void buildForm_UnknownGrantType_Throws() {
        assertThrows(IllegalStateException.class,
                () -> OAuthTokenProvider.buildForm("implicit", "be-client", null, null, null, null));
    }

    @Test
    void requestToken_Success_ReturnsAccessTokenAndSendsUrlEncodedForm() {
        String token = OAuthTokenProvider.requestToken(tokenUrl(),
                OAuthTokenProvider.buildForm("client_credentials", "be client", "a&b", null, null, null));

        assertEquals("tok-123", token);
        assertEquals("grant_type=client_credentials&client_id=be+client&client_secret=a%26b", receivedBody.get());
    }

    @Test
    void requestToken_ErrorStatus_ThrowsWithoutResponseBody() {
        status = 401;
        responseBody = "{\"error\":\"invalid_client\",\"client_secret\":\"geleakt\"}";

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OAuthTokenProvider.requestToken(tokenUrl(), Map.of("grant_type", "client_credentials")));

        assertTrue(e.getMessage().contains("HTTP 401"), e.getMessage());
        assertFalse(e.getMessage().contains("geleakt"));
    }

    @Test
    void requestToken_ResponseWithoutAccessToken_Throws() {
        responseBody = "{\"token_type\":\"Bearer\"}";

        assertThrows(IllegalStateException.class,
                () -> OAuthTokenProvider.requestToken(tokenUrl(), Map.of("grant_type", "client_credentials")));
    }

    @Test
    void fetchAccessToken_UsesEnvironmentSpecificSecret() {
        System.setProperty("oauth.token-url", tokenUrl());
        System.setProperty("oauth.client-id", "be-client");
        System.setProperty("oauth.client-secret", "dev-secret");
        System.setProperty("env.oauthenv.oauth.client-secret", "feature-secret");
        System.setProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, "oauthenv");

        assertEquals("tok-123", OAuthTokenProvider.fetchAccessToken());
        assertTrue(receivedBody.get().contains("client_secret=feature-secret"), receivedBody.get());
    }

    @Test
    void requestToken_ServerUnreachable_MessageNamesExceptionType() {
        String url = tokenUrl();
        server.stop(0);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OAuthTokenProvider.requestToken(url, Map.of("grant_type", "client_credentials")));

        assertTrue(e.getMessage().contains("nicht erreichbar: java.net."), e.getMessage());
    }

    @Test
    void fetchAccessToken_TokenUrlMissing_ThrowsWithKeyName() {
        IllegalStateException e = assertThrows(IllegalStateException.class, OAuthTokenProvider::fetchAccessToken);

        assertTrue(e.getMessage().contains("oauth.token-url"), e.getMessage());
    }
}
