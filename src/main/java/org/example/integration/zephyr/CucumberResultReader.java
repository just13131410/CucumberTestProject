package org.example.integration.zephyr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.integration.model.ZephyrTestExecution;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Liest das Cucumber-JSON-Ergebnis eines Runs und wandelt es in Zephyr-Testausfuehrungen um.
 * Ausgelagert aus {@link ZephyrScaleService}, da Parsing/Mapping fachlich unabhaengig von der
 * Zephyr/Jira-Upload-Orchestrierung ist.
 */
@Slf4j
@Component
public class CucumberResultReader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Liefert detaillierte Testausfuehrungen (eine pro {@code @T-XXXX}-getaggtem Szenario) aus
     * dem Cucumber-JSON-Report, falls vorhanden; sonst einen einzelnen Fallback-Eintrag mit dem
     * Gesamtstatus des Runs.
     */
    public List<ZephyrTestExecution> buildExecutions(UUID runId, int exitCode) {
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
