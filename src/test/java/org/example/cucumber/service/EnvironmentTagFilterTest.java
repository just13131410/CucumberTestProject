package org.example.cucumber.service;

import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;
import org.example.config.KnownEnvironmentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTagFilterTest {

    private static final List<String> CONFIGURED = List.of("dev", "staging", "prod", "performance");

    private static final String FEATURE = """
            @GUI
            Feature: Dashboard

              Scenario: Überall
                Given x

              @env:staging @env:prod
              Scenario: Nur staging und prod
                Given x

              @env:stagign
              Scenario: Tippfehler
                Given x
            """;

    private static EnvironmentTagFilter filter(Map<String, String> features) {
        Map<String, byte[]> sources = new HashMap<>();
        features.forEach((k, v) -> sources.put(k, v.getBytes(StandardCharsets.UTF_8)));
        return new EnvironmentTagFilter(new FeatureTags(() -> sources), () -> CONFIGURED);
    }

    // --- expression ---

    @Test
    void expression_ExcludesAllOtherEnvironmentsSorted() {
        assertEquals("@env:staging or not (@env:dev or @env:performance or @env:prod)",
                EnvironmentTagFilter.expression("staging", CONFIGURED));
    }

    @Test
    void expression_OnlyOneEnvironmentKnown_NoFilter() {
        assertNull(EnvironmentTagFilter.expression("dev", List.of("dev")));
        assertNull(EnvironmentTagFilter.expression("dev", List.of()));
    }

    @Test
    void expressionFor_NoEnvironment_NoFilter() {
        assertNull(filter(Map.of()).expressionFor(null));
        assertNull(filter(Map.of()).expressionFor(" "));
    }

    @Test
    void expressionFor_IncludesEnvironmentsDeclaredOnlyInFeatures() {
        String expr = filter(Map.of("a.feature", FEATURE)).expressionFor("dev");

        assertEquals("@env:dev or not (@env:performance or @env:prod or @env:stagign or @env:staging)", expr);
    }

    @Test
    void expressionFor_UnreadableFeatures_FallsBackToConfiguredEnvironments() {
        assertEquals("@env:dev or not (@env:performance or @env:prod or @env:staging)",
                filter(Map.of()).expressionFor("dev"));
    }

    // --- Ausdruck und selects() entscheiden identisch (echter Cucumber-Parser) ---

    @ParameterizedTest(name = "{0} @ {1} -> {2}")
    @MethodSource("selectionCases")
    void expressionAndSelects_AgreeWithEachOther(Set<String> pickleTags, String environment, boolean expected) {
        EnvironmentTagFilter filter = filter(Map.of("a.feature", FEATURE));
        Expression expression = TagExpressionParser.parse(filter.expressionFor(environment));

        assertEquals(expected, expression.evaluate(List.copyOf(pickleTags)), "Tag-Ausdruck");
        assertEquals(expected, EnvironmentTagFilter.selects(pickleTags, environment), "selects()");
    }

    private static Stream<Arguments> selectionCases() {
        return Stream.of(
                Arguments.of(Set.of("@GUI"), "dev", true),
                Arguments.of(Set.of("@GUI", "@env:staging", "@env:prod"), "staging", true),
                Arguments.of(Set.of("@GUI", "@env:staging", "@env:prod"), "prod", true),
                Arguments.of(Set.of("@GUI", "@env:staging", "@env:prod"), "dev", false),
                // Tippfehler läuft nirgends statt überall
                Arguments.of(Set.of("@env:stagign"), "staging", false),
                Arguments.of(Set.of("@env:stagign"), "dev", false)
        );
    }

    @Test
    void selects_NoEnvironment_SelectsEverything() {
        assertTrue(EnvironmentTagFilter.selects(Set.of("@env:prod"), null));
    }

    @Test
    void environmentsOf_IgnoresOtherAndEmptyEnvTags() {
        assertEquals(Set.of("dev"), EnvironmentTagFilter.environmentsOf(Set.of("@env:dev", "@env:", "@GUI", "@T-1")));
    }

    // --- and ---

    @Test
    void and_CombinesOptionalExpressions() {
        assertNull(EnvironmentTagFilter.and(null, null));
        assertEquals("@a", EnvironmentTagFilter.and("@a", null));
        assertEquals("@b", EnvironmentTagFilter.and(" ", "@b"));
        assertEquals("(@a or @c) and (@b)", EnvironmentTagFilter.and("@a or @c", "@b"));
    }

    @Test
    void and_RequestOrIsNotWeakenedByEnvironmentFilter() {
        String combined = EnvironmentTagFilter.and("@smoke or @regression",
                EnvironmentTagFilter.expression("dev", CONFIGURED));
        Expression expression = TagExpressionParser.parse(combined);

        assertTrue(expression.evaluate(List.of("@regression")));
        assertFalse(expression.evaluate(List.of("@regression", "@env:prod")));
        assertFalse(expression.evaluate(List.of("@other")));
    }

    // --- echte Features: nur konfigurierte Umgebungen als @env:-Tag ---

    @Test
    void classpathFeatures_DeclareOnlyConfiguredEnvironments() {
        Set<String> declared = new FeatureTags().declaredEnvironments();

        assertTrue(KnownEnvironmentValidator.allowedEnvironments().containsAll(declared),
                "Unbekannte @env:-Tags in src/main/resources/features: " + declared);
    }
}
