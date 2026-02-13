package org.example.cucumber.context;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Thread-safe context for isolating test run outputs.
 * Each concurrent test run gets its own runId and directory structure.
 * Uses InheritableThreadLocal so child threads (e.g. Playwright) inherit the context.
 */
public final class TestContext {

    private TestContext() {}

    private static final InheritableThreadLocal<String> RUN_ID = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<Path> OUTPUT_BASE = new InheritableThreadLocal<>();

    /**
     * Resolves the base path for test results.
     * On OpenShift: /app/test-results (set via TEST_RESULTS_PATH env var)
     * Locally: test-results (relative to project root, not inside target/)
     */
    private static Path resolveBasePath() {
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

    public static void init(String runId) {
        RUN_ID.set(runId);
        OUTPUT_BASE.set(resolveBasePath().resolve(runId));
    }

    public static void clear() {
        RUN_ID.remove();
        OUTPUT_BASE.remove();
    }

    public static String getRunId() {
        String id = RUN_ID.get();
        if (id == null) {
            throw new IllegalStateException("TestContext not initialized -- call TestContext.init(runId) first");
        }
        return id;
    }

    public static Path getOutputBase() {
        Path p = OUTPUT_BASE.get();
        if (p == null) {
            throw new IllegalStateException("TestContext not initialized -- call TestContext.init(runId) first");
        }
        return p;
    }

    public static boolean isInitialized() {
        return RUN_ID.get() != null;
    }

    public static Path getScreenshotsDir() {
        return getOutputBase().resolve("screenshots");
    }

    public static Path getAxeResultDir() {
        return getOutputBase().resolve("axe-result");
    }

    public static Path getAllureResultsDir() {
        return getOutputBase().resolve("allure-results");
    }

    public static Path getCucumberReportsDir() {
        return getOutputBase().resolve("cucumber-reports");
    }
}
