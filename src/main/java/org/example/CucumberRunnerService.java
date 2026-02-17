package org.example;

import io.cucumber.core.cli.Main;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import org.example.cucumber.context.TestContext;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class CucumberRunnerService {

    public RunResult runByLabel(String label) throws Exception {
        String normalizedLabel = normalizeLabel(label);
        String runId = UUID.randomUUID().toString();
        return executeRun(runId, normalizedLabel, null);
    }

    public RunResult runByLabel(String runId, String label) throws Exception {
        String normalizedLabel = normalizeLabel(label);
        return executeRun(runId, normalizedLabel, null);
    }

    public RunResult run(String runId, String tags, String features) throws Exception {
        String normalizedTags = (tags != null && !tags.isBlank()) ? normalizeLabel(tags) : null;
        return executeRun(runId, normalizedTags, features);
    }

    private RunResult executeRun(String runId, String tags, String features) throws Exception {
        // Initialize per-run context (sets up isolated output directories)
        TestContext.init(runId);
        try {
            Path runRoot = TestContext.getOutputBase();
            Path allureResults = TestContext.getAllureResultsDir();
            Path cucumberReports = TestContext.getCucumberReportsDir();
            Path screenshotsDir = TestContext.getScreenshotsDir();
            Path axeResultDir = TestContext.getAxeResultDir();

            // Create all output directories
            Files.createDirectories(runRoot);
            Files.createDirectories(allureResults);
            Files.createDirectories(cucumberReports);
            Files.createDirectories(screenshotsDir);
            Files.createDirectories(axeResultDir);

            // Reset Allure lifecycle with the correct output directory for this run.
            // The AllureLifecycle is a singleton that caches its writer on first init,
            // so we must replace it before each run to write to the new runId directory.
            System.setProperty("allure.results.directory", allureResults.toString());
            Allure.setLifecycle(new AllureLifecycle(new FileSystemResultsWriter(allureResults)));

            // Build Cucumber CLI arguments
            var argsList = new java.util.ArrayList<String>();
            argsList.add("--glue");
            argsList.add("org.example");
            argsList.add("--plugin");
            argsList.add("pretty");
            argsList.add("--plugin");
            argsList.add("json:" + cucumberReports.resolve("Cucumber.json"));
            argsList.add("--plugin");
            argsList.add("html:" + cucumberReports.resolve("Cucumber.html"));
            argsList.add("--plugin");
            argsList.add("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");

            if (tags != null && !tags.isBlank()) {
                argsList.add("--tags");
                argsList.add(tags);
            }

            // Feature path: specific features or default classpath
            if (features != null && !features.isBlank()) {
                argsList.add(features);
            } else {
                argsList.add("classpath:features");
            }

            String[] args = argsList.toArray(new String[0]);
            int exitCode = Main.run(args, Thread.currentThread().getContextClassLoader());
            return new RunResult(runId, tags, exitCode, runRoot.toString());
        } finally {
            TestContext.clear();
        }
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label darf nicht leer sein");
        }
        String trimmed = label.trim();
        return trimmed.startsWith("@") ? trimmed : "@" + trimmed;
    }

    public record RunResult(String runId, String label, int exitCode, String outputDir) {
    }
}
