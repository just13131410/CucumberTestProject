package org.example.cucumber.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrowserDemandTest {

    private static final String GUI_FEATURE = """
            # language: de
            @GUI
            Funktionalität: Dashboard

              @smoketest
              Szenario: Login
                Gegeben sei ich öffne die Login-Seite

              Szenario: Filter
                Gegeben sei ich öffne die Login-Seite
            """;

    private static final String API_FEATURE = """
            @API-Test
            Feature: Todo API

              @SmokeTest
              Scenario: Login
                Given die Todo-API Basis-URL ist gesetzt

              @regression
              Scenario: Todos
                Given die Todo-API Basis-URL ist gesetzt
            """;

    private static final String MIXED_FEATURE = """
            Feature: Gemischt

              @SmokeTest
              Scenario: API
                Given die Todo-API Basis-URL ist gesetzt

              Scenario Outline: GUI <n>
                Given die Todo-API Basis-URL ist gesetzt

                @GUI
                Examples:
                  | n |
                  | 1 |
            """;

    private static BrowserDemand demand(String browserTags) {
        return new BrowserDemand(browserTags, () -> Map.of(
                "GUIProjectTests.feature", GUI_FEATURE.getBytes(StandardCharsets.UTF_8),
                "Todo-API-Test.feature", API_FEATURE.getBytes(StandardCharsets.UTF_8),
                "sub/Mixed.feature", MIXED_FEATURE.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void apiTagOnly_NeedsNoBrowser() {
        assertFalse(demand("@GUI").needsBrowser(List.of("@API-Test"), null));
    }

    @Test
    void tagInheritedFromFeature_NeedsBrowser() {
        assertTrue(demand("@GUI").needsBrowser(List.of("@smoketest"), null));
    }

    @Test
    void tagsWithoutAtSign_AreNormalized() {
        assertTrue(demand("GUI").needsBrowser(List.of("smoketest"), null));
        assertFalse(demand("GUI").needsBrowser(List.of("regression"), null));
    }

    @Test
    void tagsAreCaseSensitive_LikeCucumber() {
        // @SmokeTest (API) ist ein anderer Tag als @smoketest (GUI)
        assertFalse(demand("@GUI").needsBrowser(List.of("@SmokeTest"), List.of("Todo-API-Test.feature")));
    }

    @Test
    void examplesTag_IsInheritedByPickle() {
        assertTrue(demand("@GUI").needsBrowser(List.of(), List.of("sub/Mixed.feature")));
        assertFalse(demand("@GUI").needsBrowser(List.of("@SmokeTest"), List.of("sub/Mixed.feature")));
    }

    @Test
    void noTagFilter_AllFeatures_NeedsBrowserWhenAnyGuiScenarioExists() {
        assertTrue(demand("@GUI").needsBrowser(null, null));
    }

    @Test
    void featureFilter_OnlyApiFeature_NeedsNoBrowser() {
        assertFalse(demand("@GUI").needsBrowser(List.of(), List.of("Todo-API-Test.feature")));
    }

    @Test
    void featureFilterWithLineNumbers_IsResolvedToFile() {
        assertFalse(demand("@GUI").needsBrowser(List.of(), List.of("Todo-API-Test.feature:5:9")));
        assertTrue(demand("@GUI").needsBrowser(List.of(), List.of("GUIProjectTests.feature:6")));
    }

    @Test
    void unknownFeatureFile_IsTreatedAsBrowserRun() {
        assertTrue(demand("@GUI").needsBrowser(List.of("@API-Test"), List.of("Unbekannt.feature")));
    }

    @Test
    void noReadableFeatures_IsTreatedAsBrowserRun() {
        assertTrue(new BrowserDemand("@GUI", Map::of).needsBrowser(List.of("@API-Test"), null));
    }

    @Test
    void multipleBrowserTags_AnyMatches() {
        assertTrue(demand("@UI, @API-Test").needsBrowser(List.of("@regression"), null));
    }

    @Test
    void noBrowserTagsConfigured_NeverNeedsBrowser() {
        assertFalse(demand(" ").needsBrowser(null, null));
    }

    @Test
    void classpathFeatures_RealGuiAndApiFeaturesAreClassified() {
        BrowserDemand real = new BrowserDemand("@GUI");

        assertTrue(real.needsBrowser(List.of("@GUI"), null));
        assertFalse(real.needsBrowser(List.of("@API-Test"), null));
        assertFalse(real.needsBrowser(List.of("@Backend"), null));
    }
}
