package org.example;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CucumberInvokerTest {

    private static final Path REPORTS = Path.of("out", "cucumber-reports");

    @Test
    void buildArgs_WithoutFeatures_RunsAllFeaturesFromClasspath() {
        List<String> args = CucumberInvoker.buildArgs(REPORTS, "@smoke", null);

        assertEquals("classpath:features", args.get(args.size() - 1));
        int tagsIdx = args.indexOf("--tags");
        assertEquals("@smoke", args.get(tagsIdx + 1));
    }

    @Test
    void buildArgs_MultipleFeatures_EachIsOwnArgumentUnderClasspathFeatures() {
        List<String> args = CucumberInvoker.buildArgs(REPORTS, null,
                "GUIProjectTests.feature, api/Todo-API-Test.feature:12");

        assertTrue(args.contains("classpath:features/GUIProjectTests.feature"));
        assertTrue(args.contains("classpath:features/api/Todo-API-Test.feature:12"));
        assertFalse(args.contains("classpath:features"), "Nur die angegebenen Features, nicht alle");
        assertFalse(args.stream().anyMatch(a -> a.contains(",")), "Kein Komma-verbundenes Sammelargument");
    }

    @Test
    void buildArgs_BlankTagsAndBlankFeatureEntries_AreSkipped() {
        List<String> args = CucumberInvoker.buildArgs(REPORTS, "  ", " , ");

        assertFalse(args.contains("--tags"));
        assertEquals("classpath:features", args.get(args.size() - 1));
    }

    @Test
    void buildArgs_ReportPluginsPointToRunDirectory() {
        List<String> args = CucumberInvoker.buildArgs(REPORTS, null, null);

        assertTrue(args.contains("json:" + REPORTS.resolve("Cucumber.json")));
        assertTrue(args.contains("html:" + REPORTS.resolve("Cucumber.html")));
        assertTrue(args.contains("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"));
        assertEquals("org.example", args.get(args.indexOf("--glue") + 1));
    }
}
