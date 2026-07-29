package org.example.integration.zephyr;

import lombok.extern.slf4j.Slf4j;
import org.example.integration.AbstractAtlassianClient;
import org.example.integration.model.ZephyrFolder;
import org.example.integration.model.ZephyrTestCycle;
import org.example.integration.model.ZephyrTestExecution;
import org.example.utils.ConfigReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ZephyrScaleClient extends AbstractAtlassianClient {

    private static final String ATM_BASE = "/rest/atm/1.0";

    // zephyr.api-token bewusst nicht per @Value injiziert, sondern ueber ConfigReader gelesen:
    // Spring's @Value liest keine .env-Datei, ConfigReader unterstuetzt das bereits (dotenv-java).
    @Autowired
    public ZephyrScaleClient(
            @Value("${zephyr.base-url:}") String baseUrl,
            @Value("${zephyr.username:}") String username) {
        this(new RestTemplate(), baseUrl, username, ConfigReader.get("zephyr.api-token", ""));
    }

    public ZephyrScaleClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        super(restTemplate, baseUrl, username, apiToken);
    }

    public List<ZephyrFolder> getFolders(String projectKey, String folderType) {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + ATM_BASE + "/folder")
                .queryParam("projectKey", projectKey)
                .queryParam("folderType", folderType)
                .toUriString();
        try {
            ResponseEntity<List<ZephyrFolder>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr getFolders failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        }
    }

    public ZephyrFolder createFolder(String name, String projectKey, String folderType) {
        String url = baseUrl + ATM_BASE + "/folder";
        Map<String, String> body = Map.of("name", name, "projectKey", projectKey, "folderType", folderType);
        try {
            ResponseEntity<ZephyrFolder> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, buildHeaders()), ZephyrFolder.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr createFolder failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    public ZephyrTestCycle createTestCycle(Map<String, Object> cycleBody) {
        String url = baseUrl + ATM_BASE + "/testrun";
        try {
            ResponseEntity<ZephyrTestCycle> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(cycleBody, buildHeaders()), ZephyrTestCycle.class);
            ZephyrTestCycle cycle = response.getBody();
            if (cycle != null) {
                log.info("Zephyr Cycle created: key={}, id={}", cycle.getKey(), cycle.getId());
            }
            return cycle;
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr createTestCycle failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    public void uploadTestResults(String cycleKey, List<ZephyrTestExecution> executions) {
        String url = baseUrl + ATM_BASE + "/testrun/" + cycleKey + "/testresults";
        try {
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(executions, buildHeaders()), Void.class);
            executions.forEach(e ->
                    log.info("Zephyr Execution uploaded: testCaseKey={}, status={}",
                            e.getTestCaseKey(), e.getStatusName()));
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr uploadTestResults failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        }
    }
}
