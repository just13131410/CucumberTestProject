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
import java.util.regex.Pattern;

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
                .status(exitCode == 0 ? "Pass" : "Fail")
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
                    List<CucumberStep> steps = element.getSteps();
                    boolean allPassed = steps != null && steps.stream()
                            .allMatch(s -> s.getResult() != null && "passed".equals(s.getResult().getStatus()));
                    executions.add(ZephyrTestExecution.builder()
                            .testCaseKey(testCaseKey)
                            .status(allPassed ? "Pass" : "Fail")
                            .comment(buildStepComment(steps))
                            .build());
                }
            }
            return executions;
        } catch (IOException e) {
            log.warn("Failed to parse Cucumber JSON at {}: {}", cucumberJson, e.getMessage());
            return List.of();
        }
    }

    /**
     * Baut das Zephyr-Comment aus den Cucumber-Steps: bestandene Steps mit Haekchen, der
     * fehlgeschlagene Step mit Kreuz + Fehlermeldung (erste Zeile), uebersprungene Steps markiert.
     * Zeilenumbrueche als {@code <br>}, da das Zephyr-Comment-Feld HTML rendert.
     */
    private String buildStepComment(List<CucumberStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        StringBuilder comment = new StringBuilder();
        for (CucumberStep step : steps) {
            if (comment.length() > 0) {
                comment.append("<br>");
            }
            String stepText = escapeHtml(
                    (step.getKeyword() != null ? step.getKeyword() : "")
                            + (step.getName() != null ? step.getName() : ""));
            String status = step.getResult() != null ? step.getResult().getStatus() : null;
            if ("passed".equals(status)) {
                comment.append("✅ ").append(stepText);
            } else if ("failed".equals(status)) {
                comment.append("❌ ").append(stepText);
                String errorMessage = step.getResult().getErrorMessage();
                if (errorMessage != null && !errorMessage.isBlank()) {
                    comment.append("<br>&nbsp;&nbsp;&nbsp;Fehler: ").append(escapeHtml(firstLine(errorMessage)));
                }
            } else {
                comment.append("⏭️ ").append(stepText);
            }
        }
        return comment.toString();
    }

    private static String firstLine(String text) {
        int idx = text.indexOf('\n');
        return idx >= 0 ? text.substring(0, idx) : text;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Erkennt sowohl projekt-praefixierte Zephyr-Keys ({@code @PROJ-T53}) als auch die
     * einfache interne Tag-Konvention ohne Projekt-Praefix ({@code @T-3511}).
     */
    private static final Pattern TEST_CASE_TAG = Pattern.compile("^@([A-Za-z][A-Za-z0-9]*-)?T-?\\d+$");

    private String extractTestCaseKey(List<CucumberTag> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(t -> t.getName() != null && TEST_CASE_TAG.matcher(t.getName()).matches())
                .map(t -> t.getName().substring(1))
                .findFirst()
                .orElse(null);
    }

    /** Oeffentlich, damit {@link ZephyrScaleService} darauf aufsetzende Report-Pfade (Attachments) ableiten kann. */
    public Path getResultsBasePath(UUID runId) {
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
        @JsonProperty("keyword")
        private String keyword;
        @JsonProperty("name")
        private String name;
        @JsonProperty("result")
        private CucumberResult result;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberResult {
        @JsonProperty("status")
        private String status;
        @JsonProperty("error_message")
        private String errorMessage;
    }
}
