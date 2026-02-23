package org.example.integration.jira;

import lombok.extern.slf4j.Slf4j;
import org.example.integration.model.JiraIssueRequest;
import org.example.integration.model.JiraIssueResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class JiraClient {

    private static final String JIRA_API_BASE = "/rest/api/2";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String authHeader;

    @Autowired
    public JiraClient(
            @Value("${zephyr.base-url:}") String baseUrl,
            @Value("${zephyr.username:}") String username,
            @Value("${zephyr.api-token:}") String apiToken) {
        this(new RestTemplate(), baseUrl, username, apiToken);
    }

    public JiraClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        String credentials = username + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public JiraIssueResponse createIssue(JiraIssueRequest request) {
        String url = baseUrl + JIRA_API_BASE + "/issue";
        try {
            ResponseEntity<JiraIssueResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, buildHeaders()), JiraIssueResponse.class);
            JiraIssueResponse issue = response.getBody();
            if (issue != null) {
                log.info("Jira ticket created: key={}", issue.getKey());
            }
            return issue;
        } catch (HttpStatusCodeException e) {
            log.error("Jira createIssue failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
