package org.example.cucumber.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Status-Model fuer laufende Tests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Test Execution Status")
public class TestStatus {

    @Schema(description = "Run ID")
    private UUID runId;

    @Schema(description = "Status",
            allowableValues = {"QUEUED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "TIMEOUT"})
    private String status;

    @Schema(description = "Umgebung")
    private String environment;

    @Schema(description = "Fortschritt in Prozent (0-100)")
    private Integer progress;

    @Schema(description = "Startzeit")
    private LocalDateTime startTime;

    @Schema(description = "Endzeit")
    private LocalDateTime endTime;

    @Schema(description = "Dauer (mm:ss)")
    private String duration;

    @Schema(description = "Anzahl Tests gesamt")
    private Integer totalTests;

    @Schema(description = "Anzahl erfolgreicher Tests")
    private Integer passedTests;

    @Schema(description = "Anzahl fehlgeschlagener Tests")
    private Integer failedTests;

    @Schema(description = "Anzahl uebersprungener Tests")
    private Integer skippedTests;

    @Schema(description = "Aktuelle Phase")
    private String currentPhase;

    @Schema(description = "Aktuelle Feature-Datei")
    private String currentFeature;

    @Schema(description = "Pod Name (falls auf Kubernetes)")
    private String podName;

    @Schema(description = "Node Name (falls auf Kubernetes)")
    private String nodeName;

    @Schema(description = "Fehlermeldung (bei Fehler)")
    private String errorMessage;

    @Schema(description = "Jira Bug-Ticket-Key bei fehlgeschlagenen Runs (z.B. PROJ-42)")
    private String jiraTicketKey;

    @Schema(description = "Report-URLs")
    private Map<String, String> reportUrls;

    @Schema(description = "Metadata")
    private Map<String, Object> metadata;

    @JsonIgnore
    public double getSuccessRate() {
        if (totalTests == null || totalTests == 0) {
            return 0.0;
        }
        return (passedTests != null ? passedTests : 0) * 100.0 / totalTests;
    }
}
