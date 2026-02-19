package org.example.cucumber.controller;

import org.example.cucumber.model.CombinedReportRequest;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import org.example.cucumber.service.TestExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API Controller für Cucumber Test Execution
 *
 * Dieser Controller ermöglicht es externen Systemen, Cucumber Tests
 * zu triggern, Status abzufragen und Reports zu erhalten.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Validated
@Tag(name = "Test Execution", description = "API für Cucumber Test Ausführung")
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    /**
     * Startet eine neue Test-Ausführung
     *
     * @param request Test Execution Request mit Environment, Tags, etc.
     * @return Test Run ID und initialer Status
     */
    @PostMapping(value = "/execute",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test-Ausführung starten",
            description = "Startet eine neue Cucumber Test-Ausführung mit den angegebenen Parametern")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Test wurde zur Ausführung eingeplant"),
            @ApiResponse(responseCode = "400", description = "Ungültige Request-Parameter"),
            @ApiResponse(responseCode = "429", description = "Zu viele gleichzeitige Test-Läufe"),
            @ApiResponse(responseCode = "500", description = "Interner Serverfehler")
    })
    public ResponseEntity<TestExecutionResponse> executeTests(
            @Valid @RequestBody TestExecutionRequest request) {

        log.info("Received test execution request: environment={}, tags={}, features={}",
                request.getEnvironment(), request.getTags(), request.getFeatures());

        try {
            TestExecutionResponse response = testExecutionService.queueTestExecution(request);

            log.info("Test execution queued successfully: runId={}", response.getRunId());

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to queue test execution", e);
            throw new RuntimeException("Failed to queue test execution", e);
        }
    }

    /**
     * Ruft den Status einer Test-Ausführung ab
     *
     * @param runId Eindeutige Run ID
     * @return Aktueller Status der Test-Ausführung
     */
    @GetMapping(value = "/status/{runId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test-Status abrufen",
            description = "Ruft den aktuellen Status einer Test-Ausführung ab")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status erfolgreich abgerufen"),
            @ApiResponse(responseCode = "404", description = "Test-Ausführung nicht gefunden")
    })
    public ResponseEntity<TestStatus> getTestStatus(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.debug("Fetching status for runId: {}", runId);

        return testExecutionService.getTestStatus(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Ruft alle aktiven Test-Ausführungen ab
     *
     * @return Liste aller aktiven Tests
     */
    @GetMapping(value = "/active",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Aktive Tests abrufen",
            description = "Ruft alle aktuell laufenden Test-Ausführungen ab")
    public ResponseEntity<List<TestStatus>> getActiveTests() {

        log.debug("Fetching all active tests");

        List<TestStatus> activeTests = testExecutionService.getActiveTests();

        return ResponseEntity.ok(activeTests);
    }

    /**
     * Ruft den Cucumber Report einer Test-Ausführung ab
     *
     * @param runId Eindeutige Run ID
     * @return Cucumber JSON Report
     */
    @GetMapping(value = "/report/{runId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test-Report abrufen",
            description = "Ruft den Cucumber JSON Report einer Test-Ausführung ab")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report erfolgreich abgerufen"),
            @ApiResponse(responseCode = "404", description = "Report nicht gefunden"),
            @ApiResponse(responseCode = "425", description = "Test noch nicht abgeschlossen")
    })
    public ResponseEntity<Object> getTestReport(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.debug("Fetching report for runId: {}", runId);

        return testExecutionService.getTestReport(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Generiert einen Allure-Report für eine Test-Ausführung und gibt die URL zurück
     *
     * @param runId Eindeutige Run ID
     * @return URL zum generierten Allure Report
     */
    @PostMapping(value = "/report/{runId}/generate",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Allure-Report generieren",
            description = "Generiert einen Allure-Report für die angegebene Test-Ausführung und gibt die URL zurück")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report erfolgreich generiert"),
            @ApiResponse(responseCode = "404", description = "Test-Ausführung nicht gefunden"),
            @ApiResponse(responseCode = "500", description = "Fehler bei der Report-Generierung")
    })
    public ResponseEntity<Map<String, String>> generateAllureReport(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.info("Generating Allure report for runId: {}", runId);

        return testExecutionService.generateAllureReport(runId)
                .map(url -> ResponseEntity.ok(Map.of(
                        "reportUrl", url,
                        "runId", runId.toString(),
                        "message", "Allure report successfully generated"
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Ruft die Report-URL (Allure) einer Test-Ausführung ab
     *
     * @param runId Eindeutige Run ID
     * @return URL zum Allure Report
     */
    @GetMapping(value = "/report/{runId}/url",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Report-URL abrufen",
            description = "Ruft die URL zum Allure Report ab (falls bereits generiert)")
    public ResponseEntity<String> getReportUrl(
            @PathVariable("runId") UUID runId) {

        log.debug("Fetching report URL for runId: {}", runId);

        return testExecutionService.getReportUrl(runId)
                .map(url -> ResponseEntity.ok("{\"url\": \"" + url + "\"}"))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Bricht eine laufende Test-Ausführung ab
     *
     * @param runId Eindeutige Run ID
     * @return Bestätigung der Abbruch-Anforderung
     */
    @DeleteMapping(value = "/cancel/{runId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test abbrechen",
            description = "Bricht eine laufende Test-Ausführung ab")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test erfolgreich abgebrochen"),
            @ApiResponse(responseCode = "404", description = "Test-Ausführung nicht gefunden"),
            @ApiResponse(responseCode = "409", description = "Test kann nicht abgebrochen werden")
    })
    public ResponseEntity<TestStatus> cancelTestExecution(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.info("Cancelling test execution: runId={}", runId);

        return testExecutionService.cancelTestExecution(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Löscht eine abgeschlossene Test-Ausführung und ihre Reports
     *
     * @param runId Eindeutige Run ID
     * @return Bestätigung der Löschung
     */
    @DeleteMapping(value = "/{runId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test-Ausführung löschen",
            description = "Löscht eine abgeschlossene Test-Ausführung und ihre Reports")
    public ResponseEntity<Void> deleteTestExecution(
            @PathVariable("runId") UUID runId) {

        log.info("Deleting test execution: runId={}", runId);

        boolean deleted = testExecutionService.deleteTestExecution(runId);

        return deleted ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Listet alle verfuegbaren Test-Runs auf dem Dateisystem
     *
     * @return Liste der Run-IDs
     */
    @GetMapping(value = "/runs",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verfuegbare Runs auflisten",
            description = "Listet alle Test-Runs auf, die Allure-Ergebnisse enthalten")
    @ApiResponse(responseCode = "200", description = "Liste erfolgreich abgerufen")
    public ResponseEntity<List<UUID>> listAvailableRuns() {

        log.debug("Listing available test runs");

        List<UUID> runs = testExecutionService.listAvailableRuns();
        return ResponseEntity.ok(runs);
    }

    /**
     * Generiert einen kombinierten Allure-Report ueber mehrere Runs
     *
     * @param request Optionaler Request-Body mit Run-IDs
     * @return URL zum generierten kombinierten Report
     */
    @PostMapping(value = "/combined-report/generate",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Kombinierten Allure-Report generieren",
            description = "Generiert einen Allure-Report ueber mehrere Test-Runs. Ohne Body oder leere runIds = alle Runs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report erfolgreich generiert"),
            @ApiResponse(responseCode = "404", description = "Keine Runs gefunden oder Allure CLI nicht verfuegbar"),
            @ApiResponse(responseCode = "500", description = "Fehler bei der Report-Generierung")
    })
    public ResponseEntity<Map<String, String>> generateCombinedAllureReport(
            @RequestBody(required = false) CombinedReportRequest request) {

        List<UUID> runIds = (request != null) ? request.getRunIds() : null;
        log.info("Generating combined Allure report for runIds: {}", runIds);

        return testExecutionService.generateCombinedAllureReport(runIds)
                .map(url -> ResponseEntity.ok(Map.of(
                        "reportUrl", url,
                        "message", "Combined Allure report successfully generated"
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health Check Endpoint
     */
    @GetMapping(value = "/health",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Service Health Check",
            description = "Prüft die Verfügbarkeit des Test Service")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("{\"status\": \"UP\"}");
    }

    /**
     * Ruft Statistiken über Test-Ausführungen ab
     *
     * @param environment Optional: Filter nach Environment
     * @return Statistiken
     */
    @GetMapping(value = "/statistics",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Test-Statistiken abrufen",
            description = "Ruft Statistiken über Test-Ausführungen ab")
    public ResponseEntity<Object> getStatistics(
            @RequestParam(required = false) String environment) {

        log.debug("Fetching statistics for environment: {}", environment);

        Object statistics = testExecutionService.getStatistics(environment);

        return ResponseEntity.ok(statistics);
    }
}