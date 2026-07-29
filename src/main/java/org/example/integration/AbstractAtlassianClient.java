package org.example.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gemeinsame Basis fuer HTTP-Clients gegen Atlassian-Produkte (Zephyr Scale, Jira), die
 * beide Basic-Auth gegen dieselbe Jira-DC-Instanz verwenden. Buendelt Auth-Header-Aufbau
 * und RestTemplate-Zugriff. Endpoint-spezifisches Error-Handling bleibt bewusst in den
 * Subklassen, da sich Rueckgabewerte bei fehlgeschlagenen Requests je Methode unterscheiden
 * (null, leere Liste, void).
 */
public abstract class AbstractAtlassianClient {

    protected final RestTemplate restTemplate;
    protected final String baseUrl;
    private final String authHeader;

    protected AbstractAtlassianClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        String credentials = username + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    protected HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
