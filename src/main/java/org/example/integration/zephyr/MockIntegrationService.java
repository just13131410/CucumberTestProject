package org.example.integration.zephyr;

import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simuliert Zephyr/Jira-Ergebnisse ohne echte API-Calls ({@code integration.mock.enabled=true}).
 * Ausgelagert aus {@link ZephyrScaleService}, da der Mock-Modus fachlich eine eigene Strategie
 * ist (deterministische Fake-Keys statt echter HTTP-Aufrufe).
 */
@Slf4j
@Component
public class MockIntegrationService {

    @Value("${zephyr.base-url:https://jira.yourcompany.com}")
    private String zephyrBaseUrl;

    public void simulateResults(UUID runId, TestStatus status, int exitCode, String projectKey) {
        String shortId   = runId.toString().substring(0, 8).toUpperCase();
        String cycleKey  = "T-R-" + shortId;
        String base      = zephyrBaseUrl.replaceAll("/$", "");
        String zephyrUrl = base + "/secure/Tests.jspa#/testRun/" + cycleKey;

        log.info("[MOCK] Zephyr Test-Run simuliert: key={}, url={}", cycleKey, zephyrUrl);
        StatusMetadataSupport.addMetadata(status, "zephyrCycleKey", cycleKey);
        StatusMetadataSupport.addReportUrl(status, "zephyr-run", zephyrUrl);

        if (exitCode != 0) {
            int ticketNumber = Math.abs(runId.hashCode()) % 9000 + 1000;
            String ticketKey = projectKey + "-" + ticketNumber;
            String ticketUrl = base + "/browse/" + ticketKey;

            log.info("[MOCK] Jira Ticket simuliert: key={}, url={}", ticketKey, ticketUrl);
            if (status != null) {
                status.setJiraTicketKey(ticketKey);
            }
            StatusMetadataSupport.addMetadata(status, "jiraTicket", ticketKey);
            StatusMetadataSupport.addReportUrl(status, "jira-ticket", ticketUrl);
        }
    }
}
