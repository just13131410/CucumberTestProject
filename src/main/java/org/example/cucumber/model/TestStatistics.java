package org.example.cucumber.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Statistik-Model fuer Test-Ausfuehrungen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestStatistics {

    private String environment;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Long totalRuns;
    private Long successfulRuns;
    private Long failedRuns;
    private Long cancelledRuns;
    private Double averageDurationMinutes;
    private Double successRate;
    private Integer totalTestsExecuted;
    private Integer totalTestsPassed;
    private Integer totalTestsFailed;
    private List<String> mostFrequentFailures;
    private Double averageParallelPods;
    private Long peakConcurrentRuns;
}
