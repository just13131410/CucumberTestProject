package org.example.cucumber.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TestStatusTest {

    @Test
    void getSuccessRate_NullTotalTests_ReturnsZero() {
        TestStatus status = TestStatus.builder()
                .totalTests(null)
                .passedTests(5)
                .build();

        assertEquals(0.0, status.getSuccessRate());
    }

    @Test
    void getSuccessRate_ZeroTotalTests_ReturnsZero() {
        TestStatus status = TestStatus.builder()
                .totalTests(0)
                .passedTests(0)
                .build();

        assertEquals(0.0, status.getSuccessRate());
    }

    @Test
    void getSuccessRate_NullPassedTests_ReturnsZero() {
        TestStatus status = TestStatus.builder()
                .totalTests(10)
                .passedTests(null)
                .build();

        assertEquals(0.0, status.getSuccessRate());
    }

    @Test
    void getSuccessRate_AllPassed_Returns100() {
        TestStatus status = TestStatus.builder()
                .totalTests(10)
                .passedTests(10)
                .build();

        assertEquals(100.0, status.getSuccessRate());
    }

    @Test
    void getSuccessRate_PartialPass_ReturnsCorrectPercentage() {
        TestStatus status = TestStatus.builder()
                .totalTests(10)
                .passedTests(7)
                .build();

        assertEquals(70.0, status.getSuccessRate());
    }

    @Test
    void getSuccessRate_NonDivisible_ReturnsDecimal() {
        TestStatus status = TestStatus.builder()
                .totalTests(3)
                .passedTests(1)
                .build();

        assertEquals(100.0 / 3.0, status.getSuccessRate(), 0.001);
    }

    @Test
    void builder_CreatesObjectWithAllFields() {
        UUID runId = UUID.randomUUID();
        TestStatus status = TestStatus.builder()
                .runId(runId)
                .status("RUNNING")
                .environment("dev")
                .progress(50)
                .build();

        assertEquals(runId, status.getRunId());
        assertEquals("RUNNING", status.getStatus());
        assertEquals("dev", status.getEnvironment());
        assertEquals(50, status.getProgress());
    }

    @Test
    void builder_DefaultsAreNull() {
        TestStatus status = TestStatus.builder().build();

        assertNull(status.getRunId());
        assertNull(status.getStatus());
        assertNull(status.getEnvironment());
        assertNull(status.getProgress());
        assertNull(status.getStartTime());
        assertNull(status.getEndTime());
        assertNull(status.getErrorMessage());
        assertNull(status.getReportUrls());
    }

    // --- duration field is String (mm:ss) ---

    @Test
    void duration_IsStoredAsString() {
        TestStatus status = TestStatus.builder()
                .duration("00:05")
                .build();

        assertEquals("00:05", status.getDuration());
    }

    @Test
    void duration_NullByDefault() {
        TestStatus status = TestStatus.builder().build();

        assertNull(status.getDuration());
    }

    // --- successRate is excluded from JSON serialization ---

    @Test
    void successRate_NotIncludedInJson() throws Exception {
        TestStatus status = TestStatus.builder()
                .runId(UUID.randomUUID())
                .status("COMPLETED")
                .totalTests(10)
                .passedTests(8)
                .build();

        String json = new ObjectMapper().writeValueAsString(status);

        assertFalse(json.contains("successRate"),
                "successRate must not appear in JSON response, but was found in: " + json);
    }

    @Test
    void successRate_MethodStillAccessibleInJava() {
        TestStatus status = TestStatus.builder()
                .totalTests(10)
                .passedTests(8)
                .build();

        // @JsonIgnore only affects serialization; the method must still work in Java
        assertEquals(80.0, status.getSuccessRate());
    }
}
