package org.example.cucumber.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response-Model fuer Test-Ausfuehrung
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Test Execution Response")
public class TestExecutionResponse {

    @Schema(description = "Eindeutige Run ID",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID runId;

    @Schema(description = "Aktueller Status",
            example = "QUEUED")
    private String status;

    @Schema(description = "Umgebung",
            example = "dev")
    private String environment;

    @Schema(description = "Nachricht",
            example = "Test execution queued successfully")
    private String message;

    @Schema(description = "Zeitstempel der Erstellung")
    private LocalDateTime timestamp;

    @Schema(description = "Geschaetzte Startzeit")
    private LocalDateTime estimatedStartTime;

    @Schema(description = "Position in der Warteschlange")
    private Integer queuePosition;

    @Schema(description = "URL zum Status-Endpoint")
    private String statusUrl;

    @Schema(description = "Cucumber Tags")
    private String tags;
}
