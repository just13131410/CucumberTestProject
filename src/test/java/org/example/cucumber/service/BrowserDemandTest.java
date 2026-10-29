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

    private static final String STAGING_ONLY_GUI_FEATURE = """
            Feature: Nur Staging-GUI

              Scenario: API überall
                Given die Todo-API Basis-URL ist gesetzt

              @GUI @env:staging
              Scenario: GUI nur staging
                Given ich öffne die Login-Seite
            """;

    private static BrowserDemand demand(String browserTags) {
        return new BrowserDemand(browserTags, () -> Map.of(
                "GUIProjectTests.feature", GUI_FEATURE.getBytes(StandardCharsets.UTF_8),
                "Todo-API-Test.feature", API_FEATURE.getBytes(StandardCharsets.UTF_8),
                "sub/Mixed.feature", MIXED_FEATURE.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void apiTagOnly_NeedsNoBrowser() {
        assertFalse(demand("@GUI").needsBrowser(List.of("@API-Test"), null, null));
    }

    @Test
    void tagInheritedFromFeature_NeedsBrowser() {
        assertTrue(demand("@GUI").needsBrowser(List.of("@smoketest"), null, null));
    }

    @Test
    void tagsWithoutAtSign_AreNormalized() {
        assertTrue(demand("GUI").needsBrowser(List.of("smoketest"), null, null));
        assertFalse(demand("GUI").needsBrowser(List.of("regression"), null, null));
    }

    @Test
    void tagsAreCaseSensitive_LikeCucumber() {
        // @SmokeTest (API) ist ein anderer Tag als @smoketest (GUI)
        assertFalse(demand("@GUI").needsBrowser(List.of("@SmokeTest"), List.of("Todo-API-Test.feature"), null));
    }

    @Test
    void examplesTag_IsInheritedByPickle() {
        assertTrue(demand("@GUI").needsBrowser(List.of(), List.of("sub/Mixed.feature"), null));
        assertFalse(demand("@GUI").needsBrowser(List.of("@SmokeTest"), List.of("sub/Mixed.feature"), null));
    }

    @Test
    void noTagFilter_AllFeatures_NeedsBrowserWhenAnyGuiScenarioExists() {
        assertTrue(demand("@GUI").needsBrowser(null, null, null));
    }

    @Test
    void featureFilter_OnlyApiFeature_NeedsNoBrowser() {
        assertFalse(demand("@GUI").needsBrowser(List.of(), List.of("Todo-API-Test.feature"), null));
    }

    @Test
    void featureFilterWithLineNumbers_IsResolvedToFile() {
        assertFalse(demand("@GUI").needsBrowser(List.of(), List.of("Todo-API-Test.feature:5:9"), null));
        assertTrue(demand("@GUI").needsBrowser(List.of(), List.of("GUIProjectTests.feature:6"), null));
    }

    @Test
    void unknownFeatureFile_IsTreatedAsBrowserRun() {
        assertTrue(demand("@GUI").needsBrowser(List.of("@API-Test"), List.of("Unbekannt.feature"), null));
    }

    @Test
    void noReadableFeatures_IsTreatedAsBrowserRun() {
        assertTrue(new BrowserDemand("@GUI", Map::of).needsBrowser(List.of("@API-Test"), null, null));
    }

    @Test
    void multipleBrowserTags_AnyMatches() {
        assertTrue(demand("@UI, @API-Test").needsBrowser(List.of("@regression"), null, null));
    }

    @Test
    void noBrowserTagsConfigured_NeverNeedsBrowser() {
        assertFalse(demand(" ").needsBrowser(null, null, null));
    }

    @Test
    void environmentRule_GuiScenariosOnlyForOtherEnvironments_NeedsNoBrowser() {
        BrowserDemand demand = new BrowserDemand("@GUI", () -> Map.of(
                "Staging.feature", STAGING_ONLY_GUI_FEATURE.getBytes(StandardCharsets.UTF_8)));

        assertFalse(demand.needsBrowser(null, null, "dev"));
        assertTrue(demand.needsBrowser(null, null, "staging"));
        assertTrue(demand.needsBrowser(null, null, null));
    }

    @Test
    void classpathFeatures_RealGuiAndApiFeaturesAreClassified() {
        BrowserDemand real = new BrowserDemand("@GUI");

        assertTrue(real.needsBrowser(List.of("@GUI"), null, null));
        assertFalse(real.needsBrowser(List.of("@API-Test"), null, null));
        assertFalse(real.needsBrowser(List.of("@Backend"), null, null));
    }
}
