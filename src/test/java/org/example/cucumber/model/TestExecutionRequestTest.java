package org.example.cucumber.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestExecutionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validRequest_NoViolations() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(List.of("@smoke"))
                .build();

        Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(),
                "Expected no violations but got: " + violations);
    }

    @Test
    void nullEnvironment_ViolatesNotBlank() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment(null)
                .tags(List.of("@smoke"))
                .build();

        Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("environment")));
    }

    @Test
    void invalidEnvironment_ViolatesPattern() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("invalid-env")
                .tags(List.of("@smoke"))
                .build();

        Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("environment")));
    }

    @Test
    void allValidEnvironments_NoViolations() {
        for (String env : List.of("dev", "staging", "prod", "performance")) {
            TestExecutionRequest request = TestExecutionRequest.builder()
                    .environment(env)
                    .tags(List.of("@smoke"))
                    .build();

            Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

            assertTrue(violations.isEmpty(),
                    "Expected no violations for environment '" + env + "' but got: " + violations);
        }
    }

    @Test
    void emptyTags_ViolatesNotEmpty() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(List.of())
                .build();

        Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("tags")));
    }

    @Test
    void nullTags_ViolatesNotEmpty() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(null)
                .build();

        Set<ConstraintViolation<TestExecutionRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("tags")));
    }

    @Test
    void builderDefaults_AreCorrect() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(List.of("@smoke"))
                .build();

        assertEquals(5, request.getParallelCount());
        assertTrue(request.getRetryFailedTests());
        assertEquals(2, request.getMaxRetries());
        assertEquals(30, request.getTimeoutMinutes());
        assertEquals("NORMAL", request.getPriority());
    }

    @Test
    void optionalFields_DefaultToNull() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(List.of("@smoke"))
                .build();

        assertNull(request.getFeatures());
        assertNull(request.getBrowser());
        assertNull(request.getEnvironmentVariables());
        assertNull(request.getWebhookUrl());
        assertNull(request.getInitiator());
    }

    @Test
    void builder_OverridesDefaults() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("prod")
                .tags(List.of("@critical"))
                .parallelCount(10)
                .retryFailedTests(false)
                .maxRetries(5)
                .timeoutMinutes(60)
                .priority("CRITICAL")
                .build();

        assertEquals(10, request.getParallelCount());
        assertFalse(request.getRetryFailedTests());
        assertEquals(5, request.getMaxRetries());
        assertEquals(60, request.getTimeoutMinutes());
        assertEquals("CRITICAL", request.getPriority());
    }
}
