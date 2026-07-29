package org.example.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestResultPathsTest {

    @Test
    void base_SystemPropertyTakesEffect() {
        System.setProperty("test.results.path", "custom-xyz");
        try {
            assertEquals(Paths.get("custom-xyz"), TestResultPaths.base());
            assertEquals(Paths.get("custom-xyz", "run-1"), TestResultPaths.forRun("run-1"));
        } finally {
            System.clearProperty("test.results.path");
        }
    }

    @Test
    void base_DefaultWhenNoOverride() {
        // Env-Var kann im Test nicht gesetzt werden; nur relevant, wenn sie nicht gesetzt ist.
        assumeTrue(System.getenv("TEST_RESULTS_PATH") == null,
                "TEST_RESULTS_PATH ist gesetzt – Default-Fall nicht prüfbar");
        System.clearProperty("test.results.path");
        assertEquals(Paths.get("test-results"), TestResultPaths.base());
    }

    @Test
    void forRun_AppendsRunIdToBase() {
        System.setProperty("test.results.path", "base-dir");
        try {
            assertEquals(Paths.get("base-dir", "abc-123"), TestResultPaths.forRun("abc-123"));
        } finally {
            System.clearProperty("test.results.path");
        }
    }
}
