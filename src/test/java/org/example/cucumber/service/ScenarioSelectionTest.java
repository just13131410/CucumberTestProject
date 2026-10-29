package org.example.cucumber.service;

import org.example.cucumber.service.ScenarioSelection.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioSelectionTest {

    @Test
    void noFilter_SelectsEverything() {
        assertEquals(Outcome.SELECTED, new ScenarioSelection(null, null, null)
                .classify("a.feature", List.of("@env:prod")));
    }

    @Test
    void tags_AreOrCombinedAndNormalized() {
        ScenarioSelection selection = new ScenarioSelection(List.of("smoke", "@regression"), null, "dev");

        assertEquals(Outcome.SELECTED, selection.classify("a.feature", List.of("@regression")));
        assertEquals(Outcome.NOT_SELECTED, selection.classify("a.feature", List.of("@other")));
    }

    @Test
    void features_RestrictFilesAndIgnoreLineNumbers() {
        ScenarioSelection selection = new ScenarioSelection(null, List.of("a.feature:12"), "dev");

        assertEquals(Outcome.SELECTED, selection.classify("a.feature", List.of()));
        assertEquals(Outcome.NOT_SELECTED, selection.classify("b.feature", List.of()));
        assertTrue(selection.isLineFiltered("a.feature"));
        assertFalse(new ScenarioSelection(null, List.of("a.feature"), "dev").isLineFiltered("a.feature"));
    }

    @Test
    void envRule_TakesPrecedenceOverNonMatchingTags() {
        ScenarioSelection selection = new ScenarioSelection(List.of("@smoke"), null, "dev");

        assertEquals(Outcome.ENV_EXCLUDED, selection.classify("a.feature", List.of("@regression", "@env:prod")));
        assertEquals(Outcome.SELECTED, selection.classify("a.feature", List.of("@smoke", "@env:dev")));
    }
}
