package org.example.integration.zephyr;

import lombok.extern.slf4j.Slf4j;
import org.example.integration.AbstractAtlassianClient;
import org.example.integration.model.ZephyrTestCycle;
import org.example.integration.model.ZephyrTestExecution;
import org.example.integration.model.ZephyrTestRun;
import org.example.utils.ConfigReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ZephyrScaleClient extends AbstractAtlassianClient {

    private static final String ATM_BASE = "/rest/atm/1.0";

    // zephyr.* Werte bewusst nicht per @Value injiziert, sondern ueber ConfigReader gelesen:
    // Spring's @Value liest keine .env-Datei, ConfigReader unterstuetzt das bereits (dotenv-java).
    @Autowired
    public ZephyrScaleClient() {
        this(new RestTemplate(),
                ConfigReader.get("zephyr.base-url", "https://jira.yourcompany.com"),
                ConfigReader.get("zephyr.username", ""),
                ConfigReader.get("zephyr.api-token", ""));
    }

    public ZephyrScaleClient(RestTemplate restTemplate, String baseUrl, String username, String apiToken) {
        super(restTemplate, baseUrl, username, apiToken);
    }

    /** Liest einen bestehenden Testrun (samt enthaltener Testfaelle in {@code items}). */
    public ZephyrTestRun getTestRun(String testRunKey) {
        String url = baseUrl + ATM_BASE + "/testrun/" + testRunKey;
        try {
            ResponseEntity<ZephyrTestRun> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), ZephyrTestRun.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr getTestRun failed: url={}, status={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
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
            log.error("Zephyr createTestCycle failed: url={}, status={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
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
                            e.getTestCaseKey(), e.getStatus()));
        } catch (HttpStatusCodeException e) {
            log.error("Zephyr uploadTestResults failed: url={}, status={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
        }
    }
}
