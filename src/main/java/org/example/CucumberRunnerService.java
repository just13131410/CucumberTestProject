package org.example;

import io.cucumber.core.cli.Main;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class CucumberRunnerService {

    public RunResult runByLabel(String label) throws Exception {
        String normalizedLabel = normalizeLabel(label);
        String runId = String.valueOf(Instant.now().toEpochMilli());
        Path runRoot = Path.of("target", "runs", runId);
        Path allureResults = runRoot.resolve("allure-results");

        Files.createDirectories(runRoot);
        Files.createDirectories(allureResults);
        System.setProperty("allure.results.directory", allureResults.toString());

        String[] args = {
                "--glue", "org.example",
                "--plugin", "pretty",
                "--plugin", "json:" + runRoot.resolve("Cucumber.json"),
                "--plugin", "html:" + runRoot.resolve("Cucumber.html"),
                "--plugin", "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
                "--tags", normalizedLabel,
                "classpath:features"
        };

        int exitCode = Main.run(args, Thread.currentThread().getContextClassLoader());
        return new RunResult(runId, normalizedLabel, exitCode, runRoot.toString());
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
