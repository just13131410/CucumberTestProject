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
    void builderDefaults_HeadlessTrue() {
        TestExecutionRequest request = TestExecutionRequest.builder()
                .environment("dev")
                .tags(List.of("@smoke"))
                .build();

        assertTrue(request.getHeadless());
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
        assertNull(request.getProjectKey());
        assertNull(request.getZephyrTemplateTestRunKey());
        assertNull(request.getZephyrResultFolder());
    }

    // --- Allowlist-Validierung (S5/S6) ---

    private TestExecutionRequest.TestExecutionRequestBuilder valid() {
        return TestExecutionRequest.builder().environment("dev").tags(List.of("@smoke"));
    }

    private boolean violates(TestExecutionRequest request, String propertyPrefix) {
        return validator.validate(request).stream()
                .anyMatch(v -> v.getPropertyPath().toString().startsWith(propertyPrefix));
    }

    @Test
    void tags_SimpleTagsValid_ExpressionsAndSpecialCharsRejected() {
        assertFalse(violates(valid().tags(List.of("@smoke", "API-Test", "@T-1234")).build(), "tags"));
        assertTrue(violates(valid().tags(List.of("@a and not @b")).build(), "tags"));
        assertTrue(violates(valid().tags(List.of("@x) or (@y")).build(), "tags"));
    }

    @Test
    void features_RelativeFeaturePathsValid() {
        assertFalse(violates(valid().features(List.of(
                "GUIProjectTests.feature", "api/Todo-API-Test.feature", "API-Test.feature:12:20")).build(), "features"));
    }

    @Test
    void features_TraversalAbsolutePathsAndOptionsRejected() {
        for (String bad : List.of("../secret.feature", "/etc/passwd", "--plugin", "classpath:features/x.feature",
                "C:/x.feature", "x.txt", "a/../b.feature")) {
            assertTrue(violates(valid().features(List.of(bad)).build(), "features"), bad);
        }
    }

    @Test
    void browser_OnlyKnownBrowsers() {
        assertFalse(violates(valid().browser("Firefox").build(), "browser"));
        assertTrue(violates(valid().browser("/bin/sh").build(), "browser"));
    }

    @Test
    void zephyrTemplateTestRunKey_PathInjectionRejected() {
        assertFalse(violates(valid().zephyrTemplateTestRunKey("PROJ-C15").build(), "zephyrTemplateTestRunKey"));
        assertFalse(violates(valid().zephyrTemplateTestRunKey("QA-R1").build(), "zephyrTemplateTestRunKey"));
        assertTrue(violates(valid().zephyrTemplateTestRunKey("../../api/2/myself").build(), "zephyrTemplateTestRunKey"));
        assertTrue(violates(valid().zephyrTemplateTestRunKey("PROJ-C1?x=1").build(), "zephyrTemplateTestRunKey"));
    }

    @Test
    void projectKey_OnlyJiraStyleKeys() {
        assertFalse(violates(valid().projectKey("PROJ").build(), "projectKey"));
        assertTrue(violates(valid().projectKey("proj").build(), "projectKey"));
        assertTrue(violates(valid().projectKey("PROJ/../X").build(), "projectKey"));
    }

    @Test
    void environmentVariables_SizeLimited() {
        java.util.Map<String, String> many = new java.util.HashMap<>();
        for (int i = 0; i < 21; i++) many.put("k" + i, "v");
        assertTrue(violates(valid().environmentVariables(many).build(), "environmentVariables"));
    }

    @Test
    void json_UnknownLegacyFieldsAreIgnored() throws Exception {
        String json = "{\"environment\":\"dev\",\"tags\":[\"@smoke\"],\"parallelCount\":5,\"priority\":\"HIGH\"}";
        TestExecutionRequest request = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, TestExecutionRequest.class);
        assertEquals("dev", request.getEnvironment());
    }
}
