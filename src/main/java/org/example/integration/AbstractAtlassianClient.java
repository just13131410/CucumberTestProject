package org.example.integration;

import org.example.utils.ConfigReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Gemeinsame Basis fuer HTTP-Clients gegen Atlassian-Produkte (Zephyr Scale, Jira), die
 * beide Basic-Auth gegen dieselbe Jira-DC-Instanz verwenden. Buendelt Auth-Header-Aufbau,
 * RestTemplate-Erzeugung (mit Timeouts) und das Kuerzen von Fehler-Bodies fuer Logs.
 * Endpoint-spezifisches Error-Handling bleibt bewusst in den Subklassen, da sich
 * Rueckgabewerte bei fehlgeschlagenen Requests je Methode unterscheiden (null, leere Liste, void).
 */
public abstract class AbstractAtlassianClient {

    /** Fehler-Bodies werden im Log auf diese Länge gekürzt (können interne Daten enthalten). */
    static final int MAX_LOGGED_BODY_CHARS = 300;

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    protected final RestTemplate restTemplate;
    protected final String baseUrl;
    private final String authHeader;

    protected AbstractAtlassianClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        this.restTemplate = restTemplate;
        // Abschliessende(n) Slash(es) entfernen: baseUrl + "/rest/..." ergaebe sonst einen
        // doppelten Slash ("https://host//rest/..."), den manche Reverse-Proxies/WAFs vor der
        // eigentlichen Jira-Anwendung mit 403 abweisen - unabhaengig von gueltigen Credentials.
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : baseUrl;
        String credentials = username + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * RestTemplate mit Connect- und Read-Timeout. Ohne Timeouts blockiert ein hängender Jira den
     * einzigen Ausführungs-Thread, die Run-Queue läuft voll und jede neue Anfrage endet in 429.
     * Konfigurierbar über {@code integration.http.connect-timeout-ms} / {@code integration.http.read-timeout-ms}.
     */
    protected static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutFromConfig("integration.http.connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT));
        factory.setReadTimeout(timeoutFromConfig("integration.http.read-timeout-ms", DEFAULT_READ_TIMEOUT));
        return new RestTemplate(factory);
    }

    /** Liest einen Timeout in Millisekunden; fehlend, ungültig oder ≤ 0 → Default. */
    static Duration timeoutFromConfig(String key, Duration defaultValue) {
        String value = ConfigReader.get(key, "");
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long ms = Long.parseLong(value.trim());
            return ms > 0 ? Duration.ofMillis(ms) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Kürzt einen Response-Body für das Log auf {@value #MAX_LOGGED_BODY_CHARS} Zeichen. */
    protected static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_LOGGED_BODY_CHARS
                ? body
                : body.substring(0, MAX_LOGGED_BODY_CHARS) + "… (" + body.length() + " Zeichen)";
    }

    protected HttpHeaders buildHeaders() {
        return buildHeaders(MediaType.APPLICATION_JSON);
    }

    /** Ueberladung fuer Requests mit abweichendem Content-Type (z.B. Multipart-Attachment-Upload). */
    protected HttpHeaders buildHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(contentType);
        return headers;
    }
}
