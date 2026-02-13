package org.example.cucumber.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Scenario Execution Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResult {

    private String scenarioName;
    private String featureName;
    private Integer line;
    private String status;
    private Long durationMs;
    private String errorMessage;
    private String stackTrace;
    private String screenshotPath;
}
