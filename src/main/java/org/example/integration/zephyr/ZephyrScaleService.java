package org.example.integration.zephyr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.example.integration.jira.JiraClient;
import org.example.integration.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public void uploadRunResults(UUID runId, TestExecutionRequest request, int exitCode, TestStatus status) {
        if (!zephyrEnabled && !jiraEnabled) {
            log.debug("Zephyr and Jira integrations disabled, skipping for runId={}", runId);
            return;
        }

        String projectKey = resolveProjectKey(request);
        if (projectKey == null || projectKey.isBlank()) {
            log.debug("No projectKey available, skipping integration upload for runId={}", runId);
            return;
        }

        try {
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

        addMetadata(status, "zephyrCycleKey", cycleKey);

        List<ZephyrTestExecution> executions = buildExecutions(runId, exitCode);
        if (!executions.isEmpty()) {
            zephyrClient.uploadTestResults(cycleKey, executions);
            addMetadata(status, "zephyrExecutions", executions.stream()
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

    private List<ZephyrTestExecution> buildExecutions(UUID runId, int exitCode) {
        Path cucumberJson = getResultsBasePath(runId).resolve("cucumber-reports").resolve("Cucumber.json");
        if (Files.exists(cucumberJson)) {
            List<ZephyrTestExecution> detailed = parseDetailedExecutions(cucumberJson);
            if (!detailed.isEmpty()) {
                return detailed;
            }
        }
        return List.of(ZephyrTestExecution.builder()
                .testCaseKey(runId.toString().substring(0, 8))
                .statusName(exitCode == 0 ? "Pass" : "Fail")
                .comment("Run: " + runId)
                .build());
    }

    private List<ZephyrTestExecution> parseDetailedExecutions(Path cucumberJson) {
        try {
            List<CucumberFeature> features = objectMapper.readValue(
                    cucumberJson.toFile(), new TypeReference<>() {});
            List<ZephyrTestExecution> executions = new ArrayList<>();
            for (CucumberFeature feature : features) {
                if (feature.getElements() == null) continue;
                for (CucumberElement element : feature.getElements()) {
                    String testCaseKey = extractTestCaseKey(element.getTags());
                    if (testCaseKey == null) continue;
                    boolean allPassed = element.getSteps() != null && element.getSteps().stream()
                            .allMatch(s -> s.getResult() != null && "passed".equals(s.getResult().getStatus()));
                    executions.add(ZephyrTestExecution.builder()
                            .testCaseKey(testCaseKey)
                            .statusName(allPassed ? "Pass" : "Fail")
                            .build());
                }
            }
            return executions;
        } catch (IOException e) {
            log.warn("Failed to parse Cucumber JSON at {}: {}", cucumberJson, e.getMessage());
            return List.of();
        }
    }

    private String extractTestCaseKey(List<CucumberTag> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(t -> t.getName() != null && t.getName().startsWith("@T-"))
                .map(t -> t.getName().substring(1))
                .findFirst()
                .orElse(null);
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
            addMetadata(status, "jiraTicket", issue.getKey());
        }
    }

    private void addMetadata(TestStatus status, String key, Object value) {
        if (status == null) return;
        Map<String, Object> metadata = status.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            status.setMetadata(metadata);
        }
        metadata.put(key, value);
    }

    private Path getResultsBasePath(UUID runId) {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath, runId.toString());
        }
        String sysProp = System.getProperty("test.results.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp, runId.toString());
        }
        return Path.of("test-results", runId.toString());
    }

    // Inner classes for Cucumber JSON parsing

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberFeature {
        @JsonProperty("elements")
        private List<CucumberElement> elements;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberElement {
        @JsonProperty("tags")
        private List<CucumberTag> tags;
        @JsonProperty("steps")
        private List<CucumberStep> steps;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberTag {
        @JsonProperty("name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberStep {
        @JsonProperty("result")
        private CucumberResult result;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberResult {
        @JsonProperty("status")
        private String status;
    }
}
