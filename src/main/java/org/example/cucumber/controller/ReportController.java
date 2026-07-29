package org.example.cucumber.controller;

import org.example.cucumber.model.CombinedReportRequest;
import org.example.cucumber.service.TestExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API Controller für Allure/Cucumber Test-Reports (Einzel- und kombinierte Runs).
 * Ausgelagert aus {@link TestExecutionController}, um Ausführungssteuerung und
 * Report-Erzeugung als getrennte Verantwortlichkeiten zu halten.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Tag(name = "Test Reports", description = "API für Cucumber/Allure Test-Reports")
public class ReportController {

    private static final String RUN_NOT_FOUND = "Test-Ausführung nicht gefunden";

    private final TestExecutionService testExecutionService;
    private final ReportUrlResolver urlResolver;

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
            @ApiResponse(responseCode = "404", description = RUN_NOT_FOUND),
            @ApiResponse(responseCode = "500", description = "Fehler bei der Report-Generierung")
    })
    public ResponseEntity<Map<String, String>> generateAllureReport(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.info("Generating Allure report for runId: {}", runId);

        return testExecutionService.generateAllureReport(runId)
                .map(url -> ResponseEntity.ok(Map.of(
                        "reportUrl", urlResolver.toAbsoluteUrl(url),
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL erfolgreich abgerufen"),
            @ApiResponse(responseCode = "404", description = "Report noch nicht generiert")
    })
    public ResponseEntity<Map<String, String>> getReportUrl(
            @Parameter(description = "Test Run ID", required = true)
            @PathVariable("runId") UUID runId) {

        log.debug("Fetching report URL for runId: {}", runId);

        return testExecutionService.getReportUrl(runId)
                .map(url -> ResponseEntity.ok(Map.of("reportUrl", urlResolver.toAbsoluteUrl(url))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Generiert einen kombinierten Allure-Report ueber mehrere Runs
     *
     * @param request Optionaler Request-Body mit Run-IDs
     * @return URL zum generierten kombinierten Report
     */
    @PostMapping(value = "/report/combined/generate",
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
                        "reportUrl", urlResolver.toAbsoluteUrl(url),
                        "message", "Combined Allure report successfully generated"
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
