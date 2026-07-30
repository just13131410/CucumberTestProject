package org.example.integration.zephyr;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.example.integration.jira.JiraClient;
import org.example.integration.model.*;
import org.example.utils.ConfigReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZephyrScaleService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ZephyrScaleClient zephyrClient;
    private final JiraClient jiraClient;
    private final CucumberResultReader cucumberResultReader;
    private final MockIntegrationService mockIntegrationService;

    @Value("${zephyr.enabled:false}")
    private boolean zephyrEnabled;

    @Value("${zephyr.default-project-key:}")
    private String defaultProjectKey;

    /** Key des Testruns, dessen Testfaelle in jeden neu angelegten Testrun geklont werden. */
    @Value("${zephyr.template-test-run-key:}")
    private String templateTestRunKey;

    /** Zephyr-Folder-Pfad (String, z.B. "/Testautomation/Smoketest"), dem der neue Testrun zugeordnet wird. */
    @Value("${zephyr.result-folder:}")
    private String resultFolder;

    @Value("${jira.enabled:false}")
    private boolean jiraEnabled;

    @Value("${jira.default-assignee-account-id:}")
    private String jiraAssigneeAccountId;

    @Value("${jira.issue-type:Bug}")
    private String jiraIssueType;

    @Value("${integration.mock.enabled:false}")
    private boolean mockEnabled;

    // Bewusst nicht per @Value injiziert, sondern ueber ConfigReader gelesen: Spring's @Value
    // liest keine .env-Datei, ConfigReader unterstuetzt das bereits (dotenv-java).
    private final String zephyrApiToken = ConfigReader.get("zephyr.api-token", "");

    @PostConstruct
    private void validateConfig() {
        logResolvedConfig();
        if (zephyrEnabled && !mockEnabled && zephyrApiToken.isBlank()) {
            log.error("zephyr.enabled=true, aber zephyr.api-token ist leer - Zephyr-Uploads werden mit 401 fehlschlagen");
        }
        if (jiraEnabled && !mockEnabled && jiraAssigneeAccountId.isBlank()) {
            log.error("jira.enabled=true, aber jira.default-assignee-account-id ist leer - Jira-Ticket-Erstellung wird fehlschlagen");
        }
    }

    /**
     * Loggt beim Start, welche Zephyr-URL und welche Credentials tatsaechlich verwendet werden
     * und aus welcher Quelle (Umgebungsvariable/.env/Secret-Datei/config.properties/Default) sie
     * stammen - der Token-Wert selbst wird nie geloggt, nur seine Laenge als Vorhanden-Nachweis.
     */
    private void logResolvedConfig() {
        var baseUrl = ConfigReader.getWithSource("zephyr.base-url", "https://jira.yourcompany.com");
        var username = ConfigReader.getWithSource("zephyr.username", "");
        var token = ConfigReader.getWithSource("zephyr.api-token", "");

        log.info("Zephyr Scale URL: {} (Quelle: {})", baseUrl.value(), baseUrl.source());
        log.info("Zephyr Scale Credentials: username='{}' (Quelle: {}), api-token={} (Quelle: {})",
                username.value().isBlank() ? "<nicht gesetzt>" : username.value(), username.source(),
                token.value().isBlank() ? "<nicht gesetzt>" : "***gesetzt, " + token.value().length() + " Zeichen***",
                token.source());
    }

    public void uploadRunResults(UUID runId, TestExecutionRequest request, int exitCode, TestStatus status) {
        if (!zephyrEnabled && !jiraEnabled && !mockEnabled) {
            log.debug("Alle Integrationen deaktiviert, überspringe runId={}", runId);
            return;
        }

        String projectKey = resolveProjectKey(request);
        if (projectKey == null || projectKey.isBlank()) {
            log.debug("No projectKey available, skipping integration upload for runId={}", runId);
            return;
        }

        try {
            if (mockEnabled) {
                mockIntegrationService.simulateResults(runId, status, exitCode, projectKey);
                return;
            }

            if (zephyrEnabled) {
                uploadToZephyr(runId, request, exitCode, status, projectKey);
            }

            if (jiraEnabled && exitCode != 0) {
                createJiraTicket(runId, request, projectKey, status);
            }
        } catch (Exception e) {
            log.error("Integration upload failed for runId={}: {}", runId, e.getMessage(), e);
        }
    }

    private void uploadToZephyr(UUID runId, TestExecutionRequest request, int exitCode,
                                TestStatus status, String projectKey) {
        String cycleKey = createCycle(runId, request, projectKey);
        if (cycleKey == null) {
            log.warn("Failed to create Zephyr test run for runId={}", runId);
            return;
        }

        StatusMetadataSupport.addMetadata(status, "zephyrCycleKey", cycleKey);

        List<ZephyrTestExecution> executions = cucumberResultReader.buildExecutions(runId, exitCode);
        if (!executions.isEmpty()) {
            zephyrClient.uploadTestResults(cycleKey, executions);
            StatusMetadataSupport.addMetadata(status, "zephyrExecutions", executions.stream()
                    .map(ZephyrTestExecution::getTestCaseKey)
                    .collect(Collectors.toList()));
        }
    }

    private String resolveProjectKey(TestExecutionRequest request) {
        if (request.getProjectKey() != null && !request.getProjectKey().isBlank()) {
            return request.getProjectKey();
        }
        return defaultProjectKey;
    }

    private String createCycle(UUID runId, TestExecutionRequest request, String projectKey) {
        String shortRunId = runId.toString().substring(0, 8);
        String tags = request.getTags() != null ? String.join(" ", request.getTags()) : "";
        String date = LocalDate.now().format(DATE_FMT);
        String cycleName = (date + " " + shortRunId + (tags.isBlank() ? "" : " " + tags)).trim();

        Map<String, Object> body = new HashMap<>();
        body.put("name", cycleName);
        body.put("projectKey", projectKey);
        if (resultFolder != null && !resultFolder.isBlank()) {
            body.put("folder", resultFolder);
        }
        List<Map<String, String>> clonedItems = cloneTestCaseItems();
        if (!clonedItems.isEmpty()) {
            body.put("items", clonedItems);
        }

        ZephyrTestCycle cycle = zephyrClient.createTestCycle(body);
        return cycle != null ? cycle.getKey() : null;
    }

    /**
     * Liest die Testfaelle des konfigurierten Template-Testruns ({@code zephyr.template-test-run-key})
     * und baut daraus die "items"-Liste fuer POST /testrun, damit der neu angelegte Testrun
     * dieselben Testfaelle enthaelt ("klonen"). Ohne konfigurierten Template-Key oder falls der
     * Template-Testrun nicht lesbar ist, wird der neue Testrun ohne vorbelegte Testfaelle angelegt.
     */
    private List<Map<String, String>> cloneTestCaseItems() {
        if (templateTestRunKey == null || templateTestRunKey.isBlank()) {
            return List.of();
        }
        ZephyrTestRun template = zephyrClient.getTestRun(templateTestRunKey);
        if (template == null || template.getItems() == null) {
            log.warn("Template-Testrun '{}' nicht gefunden oder enthaelt keine Testfaelle", templateTestRunKey);
            return List.of();
        }
        return template.getItems().stream()
                .map(ZephyrTestRunItem::getTestCaseKey)
                .filter(Objects::nonNull)
                .map(key -> Map.of("testCaseKey", key))
                .collect(Collectors.toList());
    }

    private void createJiraTicket(UUID runId, TestExecutionRequest request,
                                  String projectKey, TestStatus status) {
        String tags = request.getTags() != null ? String.join(", ", request.getTags()) : "";
        String shortRunId = runId.toString().substring(0, 8);
        String summary = "Test Automation Failure: " + shortRunId + " [" + tags + "]";
        String description = "Test run *" + shortRunId + "* failed.\n\n"
                + "Allure Report: /reports/" + runId + "/allure-report/index.html\n\n"
                + (status != null && status.getErrorMessage() != null
                        ? "Error: " + status.getErrorMessage() : "");

        JiraIssueRequest jiraRequest = JiraIssueRequest.builder()
                .fields(JiraIssueRequest.Fields.builder()
                        .project(Map.of("key", projectKey))
                        .summary(summary)
                        .description(description)
                        .issuetype(Map.of("name", jiraIssueType))
                        .assignee(Map.of("name", jiraAssigneeAccountId))
                        .build())
                .build();

        JiraIssueResponse issue = jiraClient.createIssue(jiraRequest);
        if (issue != null) {
            if (status != null) {
                status.setJiraTicketKey(issue.getKey());
            }
            StatusMetadataSupport.addMetadata(status, "jiraTicket", issue.getKey());
        }
    }
}
