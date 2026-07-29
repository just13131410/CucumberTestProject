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
    private static final String FOLDER_TYPE = "TEST_RUN";

    private final ZephyrScaleClient zephyrClient;
    private final JiraClient jiraClient;
    private final CucumberResultReader cucumberResultReader;
    private final MockIntegrationService mockIntegrationService;

    @Value("${zephyr.enabled:false}")
    private boolean zephyrEnabled;

    @Value("${zephyr.default-project-key:}")
    private String defaultProjectKey;

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
        if (zephyrEnabled && !mockEnabled && zephyrApiToken.isBlank()) {
            log.error("zephyr.enabled=true, aber zephyr.api-token ist leer - Zephyr-Uploads werden mit 401 fehlschlagen");
        }
        if (jiraEnabled && !mockEnabled && jiraAssigneeAccountId.isBlank()) {
            log.error("jira.enabled=true, aber jira.default-assignee-account-id ist leer - Jira-Ticket-Erstellung wird fehlschlagen");
        }
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
        Long folderId = getOrCreateFolder(projectKey, request);

        String cycleKey = createCycle(runId, request, projectKey, folderId);
        if (cycleKey == null) {
            log.warn("Failed to create Zephyr test cycle for runId={}", runId);
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

    private Long getOrCreateFolder(String projectKey, TestExecutionRequest request) {
        String folderName = resolveFolderName(request);
        List<ZephyrFolder> folders = zephyrClient.getFolders(projectKey, FOLDER_TYPE);
        Optional<ZephyrFolder> existing = folders.stream()
                .filter(f -> folderName.equals(f.getName()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        ZephyrFolder created = zephyrClient.createFolder(folderName, projectKey, FOLDER_TYPE);
        return created != null ? created.getId() : null;
    }

    String resolveFolderName(TestExecutionRequest request) {
        if (request.getTags() == null) return "Default";
        for (String tag : request.getTags()) {
            String normalized = tag.startsWith("@") ? tag.substring(1) : tag;
            if ("SmokeTest".equalsIgnoreCase(normalized) || "smoke".equalsIgnoreCase(normalized)) {
                return "SmokeTest";
            }
            if ("Frontend".equalsIgnoreCase(normalized)) {
                return "Frontend";
            }
            if ("Backend".equalsIgnoreCase(normalized)) {
                return "Backend";
            }
        }
        return "Default";
    }

    private String createCycle(UUID runId, TestExecutionRequest request, String projectKey, Long folderId) {
        String shortRunId = runId.toString().substring(0, 8);
        String tags = request.getTags() != null ? String.join(" ", request.getTags()) : "";
        String date = LocalDate.now().format(DATE_FMT);
        String cycleName = (date + " " + shortRunId + (tags.isBlank() ? "" : " " + tags)).trim();

        Map<String, Object> body = new HashMap<>();
        body.put("name", cycleName);
        body.put("projectKey", projectKey);
        if (folderId != null) {
            body.put("folderId", folderId);
        }

        ZephyrTestCycle cycle = zephyrClient.createTestCycle(body);
        return cycle != null ? cycle.getKey() : null;
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
