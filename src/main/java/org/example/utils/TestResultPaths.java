package org.example.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Zentrale Auflösung des Basisverzeichnisses für Test-Ergebnisse.
 * Reihenfolge: {@code TEST_RESULTS_PATH} (Env) → {@code test.results.path} (System-Property) →
 * {@code test-results/} (Default, relativ zum Arbeitsverzeichnis, außerhalb von {@code target/}).
 *
 * <p>Vereinheitlicht die zuvor in mehreren Klassen duplizierte Logik (TestContext,
 * TestExecutionService, ZephyrScaleService, CucumberRunnerService).
 */
public final class TestResultPaths {

    private TestResultPaths() {}

    /** Basisverzeichnis aller Runs. */
    public static Path base() {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath);
        }
        String sysProp = System.getProperty("test.results.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }
        return Paths.get("test-results");
    }

    /** Verzeichnis eines konkreten Runs: {@code <base>/<runId>}. */
    public static Path forRun(String runId) {
        return base().resolve(runId);
    }
}
