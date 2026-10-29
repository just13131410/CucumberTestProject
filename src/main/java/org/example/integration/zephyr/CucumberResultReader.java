package org.example.integration.zephyr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.integration.model.ZephyrScriptResult;
import org.example.integration.model.ZephyrTestExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * {@code true}, wenn die Zephyr-Testfaelle zu Scenario Outlines eine Testdaten-Tabelle haben: Zephyr
     * wiederholt dann die Skript-Schritte je Datenzeile, die {@code scriptResults} werden pro
     * Beispielzeile hintereinander gesendet statt zusammengefasst.
     */
    private final boolean outlineRowsAsTestData;

    public CucumberResultReader(@Value("${zephyr.outline-rows-as-test-data:false}") boolean outlineRowsAsTestData) {
        this.outlineRowsAsTestData = outlineRowsAsTestData;
    }

    /**
     * Liefert detaillierte Testausfuehrungen (eine pro {@code @T-XXXX}-Testfall-Key) aus
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

    /**
     * Fasst alle Elemente mit demselben Testfall-Key zu genau einer Ausfuehrung zusammen. Noetig fuer
     * Scenario Outlines: Cucumber schreibt jede Beispielzeile als eigenes Element mit denselben Tags.
     * Einzeln hochgeladen wuerde in Zephyr nur das zuletzt angelegte Ergebnis sichtbar - ein
     * Fehlschlag frueherer Zeilen ginge verloren.
     */
    private List<ZephyrTestExecution> parseDetailedExecutions(Path cucumberJson) {
        try {
            List<CucumberFeature> features = objectMapper.readValue(
                    cucumberJson.toFile(), new TypeReference<>() {});
            Map<String, List<CucumberElement>> elementsByKey = new LinkedHashMap<>();
            for (CucumberFeature feature : features) {
                if (feature.getElements() == null) continue;
                for (CucumberElement element : feature.getElements()) {
                    String testCaseKey = extractTestCaseKey(element.getTags());
                    if (testCaseKey == null) continue;
                    elementsByKey.computeIfAbsent(testCaseKey, k -> new ArrayList<>()).add(element);
                }
            }
            List<ZephyrTestExecution> executions = new ArrayList<>();
            elementsByKey.forEach((testCaseKey, elements) -> executions.add(ZephyrTestExecution.builder()
                    .testCaseKey(testCaseKey)
                    .status(elements.stream().allMatch(CucumberResultReader::isPassed) ? "Pass" : "Fail")
                    .comment(buildComment(elements))
                    .scriptResults(buildScriptResults(elements))
                    .build()));
            return executions;
        } catch (IOException e) {
            log.warn("Failed to parse Cucumber JSON at {}: {}", cucumberJson, e.getMessage());
            return List.of();
        }
    }

    /** Bestanden nur, wenn alle Steps bestanden und kein Before-/After-Hook fehlgeschlagen ist. */
    private static boolean isPassed(CucumberElement element) {
        List<CucumberStep> steps = element.getSteps();
        boolean stepsPassed = steps != null && steps.stream()
                .allMatch(s -> s.getResult() != null && "passed".equals(s.getResult().getStatus()));
        return stepsPassed && failedHooks(element).isEmpty();
    }

    private static List<CucumberHook> failedHooks(CucumberElement element) {
        List<CucumberHook> failed = new ArrayList<>();
        for (List<CucumberHook> hooks : Arrays.asList(element.getBefore(), element.getAfter())) {
            if (hooks == null) continue;
            hooks.stream()
                    .filter(h -> h.getResult() != null && "failed".equals(h.getResult().getStatus()))
                    .forEach(failed::add);
        }
        return failed;
    }

    /**
     * Ein Element: Schritt-Kommentar wie bisher. Mehrere Elemente (Outline-Beispielzeilen): pro Zeile
     * ein Abschnitt mit Ueberschrift "Beispiel n (Zeile x): Status".
     */
    private String buildComment(List<CucumberElement> elements) {
        if (elements.size() == 1) {
            return buildElementComment(elements.get(0));
        }
        StringBuilder comment = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            CucumberElement element = elements.get(i);
            if (i > 0) {
                comment.append("<br><br>");
            }
            comment.append("<b>").append(exampleLabel(i, element))
                    .append(": ").append(isPassed(element) ? "Pass" : "Fail").append("</b>");
            String elementComment = buildElementComment(element);
            if (elementComment != null) {
                comment.append("<br>").append(elementComment);
            }
        }
        return comment.toString();
    }

    private static String exampleLabel(int position, CucumberElement element) {
        return "Beispiel " + (position + 1) + (element.getLine() != null ? " (Zeile " + element.getLine() + ")" : "");
    }

    /**
     * Baut das Zephyr-Comment aus den Cucumber-Steps: bestandene Steps mit Haekchen, der
     * fehlgeschlagene Step mit Kreuz + Fehlermeldung (erste Zeile), uebersprungene Steps markiert,
     * fehlgeschlagene Hooks am Ende. Zeilenumbrueche als {@code <br>}, da das Zephyr-Comment-Feld HTML rendert.
     */
    private String buildElementComment(CucumberElement element) {
        StringBuilder comment = new StringBuilder();
        List<CucumberStep> steps = element.getSteps() != null ? element.getSteps() : List.of();
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
        for (CucumberHook hook : failedHooks(element)) {
            if (comment.length() > 0) {
                comment.append("<br>");
            }
            comment.append("❌ Hook fehlgeschlagen");
            String errorMessage = hook.getResult().getErrorMessage();
            if (errorMessage != null && !errorMessage.isBlank()) {
                comment.append(": ").append(escapeHtml(firstLine(errorMessage)));
            }
        }
        return comment.length() > 0 ? comment.toString() : null;
    }

    /**
     * Baut den strukturierten Schritt-Status ({@code scriptResults}), damit Zephyr nicht nur den
     * Gesamtstatus des Testfalls, sondern auch den Status jedes einzelnen Skript-Schritts zeigt.
     * {@code index} muss zur Position des zugehoerigen Schritts im Zephyr-Testfall-Skript passen
     * (setzt eine 1:1-Zuordnung BDD-Schritt zu Zephyr-Skript-Schritt in derselben Reihenfolge voraus).
     * Bei mehreren Beispielzeilen gilt je Schritt der schlechteste Status (Fail vor Not Executed vor
     * Pass); der Kommentar nennt die erste fehlgeschlagene Zeile.
     */
    private List<ZephyrScriptResult> buildScriptResults(List<CucumberElement> elements) {
        if (outlineRowsAsTestData && elements.size() > 1) {
            return buildTestDataScriptResults(elements);
        }
        int stepCount = elements.stream()
                .mapToInt(e -> e.getSteps() != null ? e.getSteps().size() : 0)
                .max().orElse(0);
        if (stepCount == 0) {
            return null;
        }
        boolean multipleRows = elements.size() > 1;
        List<ZephyrScriptResult> scriptResults = new ArrayList<>();
        for (int i = 0; i < stepCount; i++) {
            String worstStatus = null;
            String comment = null;
            for (int row = 0; row < elements.size(); row++) {
                List<CucumberStep> steps = elements.get(row).getSteps();
                if (steps == null || i >= steps.size()) continue;
                CucumberStep step = steps.get(i);
                String status = mapStepStatus(step.getResult() != null ? step.getResult().getStatus() : null);
                if (worstStatus == null || statusRank(status) > statusRank(worstStatus)) {
                    worstStatus = status;
                }
                if (comment == null && "Fail".equals(status)) {
                    String errorMessage = step.getResult().getErrorMessage();
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        comment = (multipleRows ? "Beispiel " + (row + 1) + ": " : "") + firstLine(errorMessage);
                    }
                }
            }
            scriptResults.add(ZephyrScriptResult.builder()
                    .index(i)
                    .status(worstStatus)
                    .comment(comment)
                    .build());
        }
        return scriptResults;
    }

    /**
     * Schritt-Status fuer Zephyr-Testfaelle mit Testdaten-Tabelle: Zephyr nummeriert die Schritte ueber
     * alle Datenzeilen fortlaufend (Zeile 1: 0..n-1, Zeile 2: n..2n-1, ...). Setzt voraus, dass die
     * Examples-Zeilen in Cucumber und die Datenzeilen in Zephyr dieselbe Reihenfolge haben.
     */
    private List<ZephyrScriptResult> buildTestDataScriptResults(List<CucumberElement> elements) {
        List<ZephyrScriptResult> scriptResults = new ArrayList<>();
        int index = 0;
        for (CucumberElement element : elements) {
            List<CucumberStep> steps = element.getSteps() != null ? element.getSteps() : List.of();
            for (CucumberStep step : steps) {
                String status = mapStepStatus(step.getResult() != null ? step.getResult().getStatus() : null);
                String comment = null;
                if ("Fail".equals(status)) {
                    String errorMessage = step.getResult().getErrorMessage();
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        comment = firstLine(errorMessage);
                    }
                }
                scriptResults.add(ZephyrScriptResult.builder()
                        .index(index++)
                        .status(status)
                        .comment(comment)
                        .build());
            }
        }
        return scriptResults.isEmpty() ? null : scriptResults;
    }

    private static int statusRank(String zephyrStatus) {
        return switch (zephyrStatus) {
            case "Fail" -> 2;
            case "Not Executed" -> 1;
            default -> 0;
        };
    }

    private static String mapStepStatus(String cucumberStatus) {
        if ("passed".equals(cucumberStatus)) {
            return "Pass";
        }
        if ("failed".equals(cucumberStatus)) {
            return "Fail";
        }
        return "Not Executed";
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

    /**
     * Cucumber vererbt Tags in der Reihenfolge Feature, Rule, Szenario/Outline, Examples. Der letzte
     * passende Tag ist daher der spezifischste und gewinnt gegen geerbte Feature-/Outline-Keys.
     */
    private String extractTestCaseKey(List<CucumberTag> tags) {
        if (tags == null) return null;
        return testCaseKeyOf(tags.stream().map(CucumberTag::getName).toList());
    }

    /** Testfall-Key (ohne {@code @}) aus Tag-Namen in Vererbungsreihenfolge; {@code null}, wenn keiner passt. */
    public static String testCaseKeyOf(List<String> tagNames) {
        return tagNames.stream()
                .filter(t -> t != null && TEST_CASE_TAG.matcher(t).matches())
                .map(t -> t.substring(1))
                .reduce((first, second) -> second)
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
        @JsonProperty("line")
        private Integer line;
        @JsonProperty("tags")
        private List<CucumberTag> tags;
        @JsonProperty("steps")
        private List<CucumberStep> steps;
        @JsonProperty("before")
        private List<CucumberHook> before;
        @JsonProperty("after")
        private List<CucumberHook> after;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CucumberHook {
        @JsonProperty("result")
        private CucumberResult result;
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
