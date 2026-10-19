package org.example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holt ein OAuth2-Access-Token für das Backend vom Token-Endpoint des OAuth-Servers. Alle Werte kommen
 * über {@link ConfigReader} und sind damit je Ziel-Umgebung überschreibbar
 * ({@code env.<umgebung>.oauth.client-secret} usw.):
 * <ul>
 *   <li>{@code oauth.token-url} – Token-Endpoint, z.B. {@code https://sso.example.com/realms/x/protocol/openid-connect/token}</li>
 *   <li>{@code oauth.grant-type} – {@code client_credentials} (Default) oder {@code password}</li>
 *   <li>{@code oauth.client-id}, {@code oauth.client-secret} (Secret optional bei Public Clients)</li>
 *   <li>{@code oauth.username}, {@code oauth.password} – nur für {@code password}</li>
 *   <li>{@code oauth.scope} – optional</li>
 * </ul>
 */
public final class OAuthTokenProvider {

    static final String CLIENT_CREDENTIALS = "client_credentials";
    static final String PASSWORD = "password";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private OAuthTokenProvider() {}

    /**
     * @throws IllegalStateException wenn Konfiguration fehlt, der Server keinen 2xx-Status liefert oder
     *                               die Antwort kein {@code access_token} enthält
     */
    public static String fetchAccessToken() {
        String tokenUrl = require("oauth.token-url");
        Map<String, String> form = buildForm(
                ConfigReader.get("oauth.grant-type", CLIENT_CREDENTIALS),
                require("oauth.client-id"),
                ConfigReader.get("oauth.client-secret", null),
                ConfigReader.get("oauth.username", null),
                ConfigReader.get("oauth.password", null),
                ConfigReader.get("oauth.scope", null));
        return requestToken(tokenUrl, form);
    }

    /** Formular für den Token-Request (application/x-www-form-urlencoded). Package-private für Unit-Tests. */
    static Map<String, String> buildForm(String grantType, String clientId, String clientSecret,
                                         String username, String password, String scope) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", grantType);
        form.put("client_id", clientId);
        if (clientSecret != null) {
            form.put("client_secret", clientSecret);
        }
        if (PASSWORD.equals(grantType)) {
            if (username == null || password == null) {
                throw new IllegalStateException("oauth.grant-type=password benötigt oauth.username und oauth.password"
                        + environmentHint());
            }
            form.put("username", username);
            form.put("password", password);
        } else if (!CLIENT_CREDENTIALS.equals(grantType)) {
            throw new IllegalStateException("oauth.grant-type muss client_credentials oder password sein, ist aber '"
                    + grantType + "'");
        }
        if (scope != null) {
            form.put("scope", scope);
        }
        return form;
    }

    static String requestToken(String tokenUrl, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // java.net.http liefert z.B. bei ConnectException keine Message, daher die Exception-Klasse nennen.
            throw new IllegalStateException("OAuth-Token-Endpoint " + tokenUrl + " nicht erreichbar: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Abruf des OAuth-Tokens unterbrochen", e);
        }
        if (response.statusCode() / 100 != 2) {
            // Body nicht ausgeben: Fehlermeldungen von OAuth-Servern können Client-Daten spiegeln.
            throw new IllegalStateException("OAuth-Token-Endpoint " + tokenUrl + " antwortet mit HTTP "
                    + response.statusCode() + environmentHint());
        }
        try {
            JsonNode token = MAPPER.readTree(response.body()).get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new IllegalStateException("Antwort von " + tokenUrl + " enthält kein access_token");
            }
            return token.asText();
        } catch (IOException e) {
            throw new IllegalStateException("Antwort von " + tokenUrl + " ist kein JSON", e);
        }
    }

    private static String require(String key) {
        String value = ConfigReader.get(key, null);
        if (value == null) {
            throw new IllegalStateException(key + " ist nicht konfiguriert" + environmentHint());
        }
        return value;
    }

    private static String environmentHint() {
        String environment = ConfigReader.activeEnvironment();
        return environment == null ? "" : " (Umgebung '" + environment + "', Keys auch als env." + environment + ".oauth.*)";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
