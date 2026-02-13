package org.example;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
}
