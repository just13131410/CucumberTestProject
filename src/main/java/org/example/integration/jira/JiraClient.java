package org.example.integration.jira;

import lombok.extern.slf4j.Slf4j;
import org.example.integration.AbstractAtlassianClient;
import org.example.integration.model.JiraIssueRequest;
import org.example.integration.model.JiraIssueResponse;
import org.example.utils.ConfigReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class JiraClient extends AbstractAtlassianClient {

    private static final String JIRA_API_BASE = "/rest/api/2";

    // Jira und Zephyr Scale laufen auf derselben Jira-DC-Instanz, daher teilen sich
    // JiraClient und ZephyrScaleClient bewusst dieselben zephyr.*-Auth-Properties
    // statt eigener jira.*-Properties zu duplizieren.
    // zephyr.* Werte bewusst nicht per @Value injiziert, sondern ueber ConfigReader gelesen:
    // Spring's @Value liest keine .env-Datei, ConfigReader unterstuetzt das bereits (dotenv-java).
    @Autowired
    public JiraClient() {
        this(new RestTemplate(),
                ConfigReader.get("zephyr.base-url", "https://jira.yourcompany.com"),
                ConfigReader.get("zephyr.username", ""),
                ConfigReader.get("zephyr.api-token", ""));
    }

    public JiraClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        super(restTemplate, baseUrl, username, apiToken);
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
}
