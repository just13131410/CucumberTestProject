package org.example.cucumber.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request-Model fuer Test-Ausfuehrung
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Test Execution Request")
public class TestExecutionRequest {

    @NotBlank(message = "Environment darf nicht leer sein")
    @Pattern(regexp = "dev|staging|prod|performance",
            message = "Environment muss dev, staging, prod oder performance sein")
    @Schema(description = "Ziel-Umgebung fuer die Tests",
            example = "dev",
            required = true)
    private String environment;

    @NotEmpty(message = "Mindestens ein Tag muss angegeben werden")
    @Schema(description = "Cucumber Tags zum Filtern der Tests",
            example = "[\"@smoke\", \"@critical\"]",
            required = true)
    private List<String> tags;

    @Schema(description = "Spezifische Feature-Dateien (optional)",
            example = "[\"login.feature\", \"checkout.feature\"]")
    private List<String> features;

    @Schema(description = "Anzahl paralleler Test-Threads",
            example = "5",
            defaultValue = "5")
    @Builder.Default
    private Integer parallelCount = 5;

    @Schema(description = "Browser fuer UI-Tests (optional)",
            example = "chrome")
    private String browser;

    @Schema(description = "Browser im Headless-Modus starten (kein sichtbares Fenster)",
            example = "true",
            defaultValue = "true")
    @Builder.Default
    private Boolean headless = true;

    @Schema(description = "Benutzerdefinierte Umgebungsvariablen")
    private Map<String, String> environmentVariables;

    @Schema(description = "Retry-Strategie bei fehlgeschlagenen Tests",
            defaultValue = "true")
    @Builder.Default
    private Boolean retryFailedTests = true;

    @Schema(description = "Maximale Anzahl von Retry-Versuchen",
            example = "2",
            defaultValue = "2")
    @Builder.Default
    private Integer maxRetries = 2;

    @Schema(description = "Timeout fuer gesamte Test-Ausfuehrung in Minuten",
            example = "30",
            defaultValue = "30")
    @Builder.Default
    private Integer timeoutMinutes = 30;

    @Schema(description = "Webhook-URL fuer Benachrichtigungen (optional)")
    private String webhookUrl;

    @Schema(description = "Prioritaet der Test-Ausfuehrung",
            example = "HIGH",
            allowableValues = {"LOW", "NORMAL", "HIGH", "CRITICAL"})
    @Builder.Default
    private String priority = "NORMAL";

    @Schema(description = "Initiator der Test-Ausfuehrung",
            example = "jenkins-pipeline")
    private String initiator;
}
