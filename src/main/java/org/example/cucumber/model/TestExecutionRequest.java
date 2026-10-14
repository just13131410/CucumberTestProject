package org.example.cucumber.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request-Model fuer Test-Ausfuehrung.
 * <p>
 * Alle frei wählbaren Strings, die in Kommandozeilen, URL-Pfade oder Jira-Inhalte fließen, sind
 * per Allowlist-Pattern eingeschränkt. Unbekannte Felder (z.B. die früher reservierten
 * {@code parallelCount}, {@code retryFailedTests}, {@code priority}) werden ignoriert, damit
 * bestehende Clients weiter funktionieren.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Test Execution Request")
public class TestExecutionRequest {

    /** Einfacher Cucumber-Tag ({@code @smoke}, {@code API-Test}); keine Ausdrücke wie {@code and not}. */
    static final String TAG_PATTERN = "^@?[\\w-]+$";

    /** Relativer Pfad unterhalb von {@code classpath:features}, optional mit Zeilennummern. */
    static final String FEATURE_PATTERN = "^(?!.*\\.\\.)[\\w-]+(/[\\w-]+)*\\.feature(:\\d+)*$";

    /** Jira-Projektschlüssel, z.B. {@code PROJ}. */
    static final String PROJECT_KEY_PATTERN = "^[A-Z][A-Z0-9_]{1,9}$";

    /** Zephyr-Testrun-Key, z.B. {@code PROJ-R1} oder {@code PROJ-C15}; schließt Pfad-Injection aus. */
    static final String TEST_RUN_KEY_PATTERN = "^[A-Z][A-Z0-9_]{1,9}-[A-Z]?\\d+$";

    @NotBlank(message = "Environment darf nicht leer sein")
    @Pattern(regexp = "dev|staging|prod|performance",
            message = "Environment muss dev, staging, prod oder performance sein")
    @Schema(description = "Ziel-Umgebung fuer die Tests",
            example = "dev",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String environment;

    @NotEmpty(message = "Mindestens ein Tag muss angegeben werden")
    @Size(max = 20, message = "Maximal 20 Tags")
    @Schema(description = "Cucumber Tags zum Filtern der Tests (ODER-verknüpft, nur einfache Tags)",
            example = "[\"@smoke\", \"@critical\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Pattern(regexp = TAG_PATTERN, message = "Nur einfache Tags wie @smoke erlaubt") String> tags;

    @Size(max = 50, message = "Maximal 50 Feature-Dateien")
    @Schema(description = "Spezifische Feature-Dateien relativ zu features/ (optional)",
            example = "[\"GUIProjectTests.feature\", \"API-Test.feature:12\"]")
    private List<@Pattern(regexp = FEATURE_PATTERN,
            message = "Nur relative .feature-Pfade ohne '..' erlaubt") String> features;

    @Pattern(regexp = "(?i)chromium|chrome|firefox|webkit",
            message = "Browser muss chromium, chrome, firefox oder webkit sein")
    @Schema(description = "Browser fuer UI-Tests (optional)",
            example = "chromium")
    private String browser;

    @Schema(description = "Browser im Headless-Modus starten (kein sichtbares Fenster)",
            example = "true",
            defaultValue = "true")
    @Builder.Default
    private Boolean headless = true;

    @Size(max = 20, message = "Maximal 20 Umgebungsvariablen")
    @Schema(description = "Laufzeit-Overrides; nur Keys aus test.execution.allowed-override-keys "
            + "werden übernommen, alle anderen ignoriert")
    private Map<String, String> environmentVariables;

    @Pattern(regexp = PROJECT_KEY_PATTERN, message = "Ungültiger Jira-Projektschlüssel")
    @Schema(description = "Jira/Zephyr Scale Projekt-Schluessel (optional, z.B. PROJ)",
            example = "PROJ")
    private String projectKey;

    @Pattern(regexp = TEST_RUN_KEY_PATTERN, message = "Ungültiger Zephyr-Testrun-Key")
    @Schema(description = "Zephyr-Testrun-Key, dessen Testfaelle in den neuen Cycle geklont werden "
            + "(optional, ueberschreibt zephyr.template-test-run-key fuer diesen Lauf - z.B. fuer einen "
            + "abweichenden Testfallsatz wie API-Tests)",
            example = "PROJ-C15")
    private String zephyrTemplateTestRunKey;

    @Size(max = 255, message = "Folder-Pfad zu lang")
    @Schema(description = "Zephyr-Folder-Pfad, dem der neue Cycle zugeordnet wird (optional, ueberschreibt "
            + "zephyr.result-folder fuer diesen Lauf)",
            example = "/Testautomation/API")
    private String zephyrResultFolder;
}
