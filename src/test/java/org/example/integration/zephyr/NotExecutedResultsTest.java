package org.example.integration.zephyr;

import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.service.FeatureTags;
import org.example.integration.model.ZephyrTestExecution;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotExecutedResultsTest {

    private static final String GUI_FEATURE = """
            @GUI
            Feature: Dashboard

              @T-1 @smoke
              Scenario: Überall
                Given x

              @T-2 @env:staging @env:prod
              Scenario: Nur staging und prod
                Given x

              @T-3 @regression
              Scenario: Nur Regression
                Given x

              @T-4 @env:prod
              Scenario Outline: Outline <n>
                Given x

                Examples:
                  | n |
                  | 1 |

                @env:dev
                Examples:
                  | n |
                  | 2 |
            """;

    private static final String API_FEATURE = """
            @API-Test @T-10
            Feature: API

              @PROJ-T11
              Scenario: Spezifischer Key schlägt Feature-Key
                Given x
            """;

    private static NotExecutedResults results(String notExecutedStatus, String envExcludedStatus) {
        FeatureTags tags = new FeatureTags(() -> Map.of(
                "GUI.feature", GUI_FEATURE.getBytes(StandardCharsets.UTF_8),
                "API.feature", API_FEATURE.getBytes(StandardCharsets.UTF_8)));
        return new NotExecutedResults(tags, notExecutedStatus, envExcludedStatus);
    }

    private static NotExecutedResults results() {
        return results("Not Executed", "Not Applicable");
    }

    private static TestExecutionRequest request(String environment, List<String> tags, List<String> features) {
        TestExecutionRequest request = new TestExecutionRequest();
        request.setEnvironment(environment);
        request.setTags(tags);
        request.setFeatures(features);
        return request;
    }

    private static ZephyrTestExecution only(List<ZephyrTestExecution> executions) {
        assertEquals(1, executions.size(), executions.toString());
        return executions.get(0);
    }

    @Test
    void allPlannedKeysReported_NothingToAdd() {
        assertTrue(results().build(List.of("T-1"), Set.of("T-1"), request("dev", null, null)).isEmpty());
    }

    @Test
    void envExcluded_UsesEnvStatusAndNamesAllowedEnvironments() {
        ZephyrTestExecution e = only(results().build(List.of("T-1", "T-2"), Set.of("T-1"),
                request("dev", null, null)));

        assertEquals("T-2", e.getTestCaseKey());
        assertEquals("Not Applicable", e.getStatus());
        assertEquals("Nicht ausgeführt: Nicht für Umgebung <b>dev</b> freigegeben (nur: prod, staging)",
                e.getComment());
        assertNull(e.getScriptResults());
    }

    @Test
    void notMatchingRequestTags_UsesNotExecutedStatusAndNamesFilter() {
        ZephyrTestExecution e = only(results().build(List.of("T-3"), Set.of(),
                request("dev", List.of("@smoke", "@Backend"), null)));

        assertEquals("Not Executed", e.getStatus());
        assertEquals("Nicht ausgeführt: Nicht im Lauf ausgewählt (Tags: @smoke @Backend)", e.getComment());
    }

    @Test
    void otherFeatureFileSelected_IsNotSelected() {
        ZephyrTestExecution e = only(results().build(List.of("T-1"), Set.of(),
                request("dev", null, List.of("API.feature"))));

        assertEquals("Nicht ausgeführt: Nicht im Lauf ausgewählt (Features: API.feature)", e.getComment());
    }

    @Test
    void fileSelectedWithLineNumbers_IsNotSelected() {
        ZephyrTestExecution e = only(results().build(List.of("T-3"), Set.of(),
                request("dev", null, List.of("GUI.feature:5"))));

        assertEquals("Nicht ausgeführt: Nicht im Lauf ausgewählt (Features: GUI.feature:5)", e.getComment());
    }

    @Test
    void selectedButNoResult_PointsToAbortedRun() {
        ZephyrTestExecution e = only(results().build(List.of("T-1"), Set.of(),
                request("dev", List.of("smoke"), null)));

        assertEquals("Not Executed", e.getStatus());
        assertTrue(e.getComment().contains("war ausgewählt, hat aber kein Ergebnis"), e.getComment());
    }

    @Test
    void noScenarioForKey_SaysSo() {
        ZephyrTestExecution e = only(results().build(List.of("PROJ-T99"), Set.of(), request("dev", null, null)));

        assertEquals("Not Executed", e.getStatus());
        assertEquals("Nicht ausgeführt: Kein automatisiertes Szenario mit Tag @PROJ-T99 vorhanden", e.getComment());
    }

    @Test
    void outlineWithRowForTargetEnvironment_IsNotEnvExcluded() {
        // Zeile 2 ist für dev freigegeben → gilt als ausgewählt, nicht als Umgebungs-Ausschluss
        ZephyrTestExecution e = only(results().build(List.of("T-4"), Set.of(), request("dev", null, null)));

        assertTrue(e.getComment().contains("war ausgewählt"), e.getComment());
    }

    @Test
    void outlineAllRowsForOtherEnvironments_ListsUnionOfEnvironments() {
        ZephyrTestExecution e = only(results().build(List.of("T-4"), Set.of(), request("staging", null, null)));

        assertEquals("Nicht ausgeführt: Nicht für Umgebung <b>staging</b> freigegeben (nur: dev, prod)",
                e.getComment());
    }

    @Test
    void lastTestCaseTagWins_FeatureKeyNotMappedToScenario() {
        List<ZephyrTestExecution> executions = results().build(List.of("PROJ-T11", "T-10"), Set.of(),
                request("dev", List.of("@other"), null));

        assertEquals("PROJ-T11", executions.get(0).getTestCaseKey());
        assertTrue(executions.get(0).getComment().contains("Nicht im Lauf ausgewählt"));
        assertTrue(executions.get(1).getComment().contains("Kein automatisiertes Szenario mit Tag @T-10"));
    }

    @Test
    void duplicatePlannedKeys_YieldOneResultEach() {
        assertEquals(1, results().build(List.of("T-2", "T-2"), Set.of(), request("dev", null, null)).size());
    }

    @Test
    void blankStatuses_FallBackToNotExecuted() {
        ZephyrTestExecution e = only(results(" ", null).build(List.of("T-2"), Set.of(), request("dev", null, null)));

        assertEquals("Not Executed", e.getStatus());
    }
}
