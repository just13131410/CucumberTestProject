package org.example;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import io.qameta.allure.model.TestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CucumberRunnerServiceTest {

    private final CucumberRunnerService service = new CucumberRunnerService();

    // Helper to invoke private normalizeLabel via reflection
    private String normalizeLabel(String label) throws Exception {
        Method method = CucumberRunnerService.class.getDeclaredMethod("normalizeLabel", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(service, label);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Test
    void normalizeLabel_Null_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> normalizeLabel(null));
    }

    @Test
    void normalizeLabel_Empty_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> normalizeLabel(""));
    }

    @Test
    void normalizeLabel_Blank_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> normalizeLabel("   "));
    }

    @Test
    void normalizeLabel_WithoutAtPrefix_AddsPrefix() throws Exception {
        assertEquals("@smoke", normalizeLabel("smoke"));
    }

    @Test
    void normalizeLabel_WithAtPrefix_Unchanged() throws Exception {
        assertEquals("@smoke", normalizeLabel("@smoke"));
    }

    @Test
    void normalizeLabel_WithWhitespace_Trims() throws Exception {
        assertEquals("@smoke", normalizeLabel("  smoke  "));
    }

    @Test
    void normalizeLabel_WithAtAndWhitespace_TrimsAndKeepsPrefix() throws Exception {
        assertEquals("@smoke", normalizeLabel("  @smoke  "));
    }

    @Test
    void normalizeLabel_ComplexExpression_AddsPrefix() throws Exception {
        assertEquals("@smoke or @regression", normalizeLabel("smoke or @regression"));
    }

    @Test
    void runByLabel_NullLabel_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.runByLabel(null));
    }

    @Test
    void runByLabel_EmptyLabel_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.runByLabel(""));
    }

    @Test
    void runByLabelWithRunId_NullLabel_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.runByLabel("run-123", null));
    }

    @Test
    void runResult_RecordFields() {
        var result = new CucumberRunnerService.RunResult("run-1", "@smoke", 0, "/output");

        assertEquals("run-1", result.runId());
        assertEquals("@smoke", result.label());
        assertEquals(0, result.exitCode());
        assertEquals("/output", result.outputDir());
    }

    @Test
    void runResult_Equality() {
        var r1 = new CucumberRunnerService.RunResult("run-1", "@smoke", 0, "/out");
        var r2 = new CucumberRunnerService.RunResult("run-1", "@smoke", 0, "/out");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    /**
     * Verifies that resetting the AllureLifecycle before each run directs
     * results to the correct per-run directory. Without the reset, the
     * singleton lifecycle would keep writing to the first run's directory.
     */
    @Test
    void allureLifecycleReset_ConsecutiveRuns_WriteToSeparateDirectories(@TempDir Path tempDir) throws Exception {
        Path run1Dir = tempDir.resolve("run1").resolve("allure-results");
        Path run2Dir = tempDir.resolve("run2").resolve("allure-results");
        Files.createDirectories(run1Dir);
        Files.createDirectories(run2Dir);

        // --- Simulate first run: set lifecycle to run1 ---
        Allure.setLifecycle(new AllureLifecycle(new FileSystemResultsWriter(run1Dir)));

        String tc1Uuid = UUID.randomUUID().toString();
        AllureLifecycle lc1 = Allure.getLifecycle();
        lc1.scheduleTestCase(new TestResult().setUuid(tc1Uuid).setName("Test Run 1"));
        lc1.startTestCase(tc1Uuid);
        lc1.stopTestCase(tc1Uuid);
        lc1.writeTestCase(tc1Uuid);

        // --- Simulate second run: reset lifecycle to run2 ---
        Allure.setLifecycle(new AllureLifecycle(new FileSystemResultsWriter(run2Dir)));

        String tc2Uuid = UUID.randomUUID().toString();
        AllureLifecycle lc2 = Allure.getLifecycle();
        lc2.scheduleTestCase(new TestResult().setUuid(tc2Uuid).setName("Test Run 2"));
        lc2.startTestCase(tc2Uuid);
        lc2.stopTestCase(tc2Uuid);
        lc2.writeTestCase(tc2Uuid);

        // --- Verify: each directory has exactly one result file ---
        try (Stream<Path> run1Files = Files.list(run1Dir);
             Stream<Path> run2Files = Files.list(run2Dir)) {

            long run1Count = run1Files.filter(p -> p.toString().endsWith("-result.json")).count();
            long run2Count = run2Files.filter(p -> p.toString().endsWith("-result.json")).count();

            assertEquals(1, run1Count, "Run 1 directory should contain exactly one result file");
            assertEquals(1, run2Count, "Run 2 directory should contain exactly one result file");
        }
    }

    /**
     * Proves the original bug: without resetting the lifecycle, the second run's
     * results end up in the first run's directory.
     */
    @Test
    void allureLifecycleWithoutReset_SecondRunWritesToFirstDirectory(@TempDir Path tempDir) throws Exception {
        Path run1Dir = tempDir.resolve("run1").resolve("allure-results");
        Path run2Dir = tempDir.resolve("run2").resolve("allure-results");
        Files.createDirectories(run1Dir);
        Files.createDirectories(run2Dir);

        // --- First run: set lifecycle to run1 ---
        Allure.setLifecycle(new AllureLifecycle(new FileSystemResultsWriter(run1Dir)));

        String tc1Uuid = UUID.randomUUID().toString();
        AllureLifecycle lc = Allure.getLifecycle();
        lc.scheduleTestCase(new TestResult().setUuid(tc1Uuid).setName("Test Run 1"));
        lc.startTestCase(tc1Uuid);
        lc.stopTestCase(tc1Uuid);
        lc.writeTestCase(tc1Uuid);

        // --- Second run: only set system property, do NOT reset lifecycle (old behavior) ---
        System.setProperty("allure.results.directory", run2Dir.toString());
        // Reuse the same lifecycle (simulates the bug)

        String tc2Uuid = UUID.randomUUID().toString();
        lc.scheduleTestCase(new TestResult().setUuid(tc2Uuid).setName("Test Run 2"));
        lc.startTestCase(tc2Uuid);
        lc.stopTestCase(tc2Uuid);
        lc.writeTestCase(tc2Uuid);

        // --- Verify: run1 has BOTH results, run2 is empty (the bug) ---
        try (Stream<Path> run1Files = Files.list(run1Dir);
             Stream<Path> run2Files = Files.list(run2Dir)) {

            long run1Count = run1Files.filter(p -> p.toString().endsWith("-result.json")).count();
            long run2Count = run2Files.filter(p -> p.toString().endsWith("-result.json")).count();

            assertEquals(2, run1Count, "Without reset: both results land in run1 directory");
            assertEquals(0, run2Count, "Without reset: run2 directory stays empty");
        }
    }
}
