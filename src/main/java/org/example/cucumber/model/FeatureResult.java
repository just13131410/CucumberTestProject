package org.example.cucumber.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Feature Execution Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureResult {

    private String featureName;
    private String featurePath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private Integer totalScenarios;
    private Integer passedScenarios;
    private Integer failedScenarios;
    private Integer skippedScenarios;
    private String status;
    private String errorMessage;
}
