package org.example;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import org.example.cucumber.context.TestContext;
import org.example.hooks.AxeReportHook;

import io.cucumber.core.cli.Main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Führt einen Cucumber-Lauf für einen bereits normalisierten Tag-Ausdruck aus – in-process im
 * aufrufenden Thread. Wird von zwei Stellen genutzt:
 * <ul>
 *   <li>{@link CucumberRunnerService} im In-Process-Modus (gleiche JVM wie der Web-Service),</li>
 *   <li>{@link CucumberRunnerMain} als {@code main} in einer Kind-JVM (Out-of-Process-Modus).</li>
 * </ul>
 * Die Isolierung der Ausgabeverzeichnisse erfolgt über {@link TestContext}. Der prozessweite
 * Allure-Singleton wird bewusst pro Lauf zurückgesetzt; das ist nur bei effektiver Parallelität = 1
 * korrekt – echte Parallelität liefert erst der Out-of-Process-Modus (je Lauf eine eigene JVM).
 */
public final class CucumberInvoker {

    static final String FEATURES_ROOT = "classpath:features";

    private CucumberInvoker() {}

    /**
     * @param runId    eindeutige Run-ID (bestimmt die Ausgabeverzeichnisse via TestContext)
     * @param tags     bereits normalisierter Tag-Ausdruck (z.B. "@smoke or @regression") oder null
     * @param features optionale, kommagetrennte Feature-Pfade relativ zu {@code classpath:features} oder null
     * @return Cucumber-Exit-Code (0 = alle bestanden)
     */
    public static int invoke(String runId, String tags, String features) throws Exception {
        TestContext.init(runId);
        try {
            Path runRoot = TestContext.getOutputBase();
            Path allureResults = TestContext.getAllureResultsDir();
            Path cucumberReports = TestContext.getCucumberReportsDir();
            Path screenshotsDir = TestContext.getScreenshotsDir();
            Path axeResultDir = TestContext.getAxeResultDir();

            Files.createDirectories(runRoot);
            Files.createDirectories(allureResults);
            Files.createDirectories(cucumberReports);
            Files.createDirectories(screenshotsDir);
            Files.createDirectories(axeResultDir);

            // AllureLifecycle-Singleton mit dem korrekten Ausgabeverzeichnis für diesen Lauf neu setzen.
            System.setProperty("allure.results.directory", allureResults.toString());
            Allure.setLifecycle(new AllureLifecycle(new FileSystemResultsWriter(allureResults)));

            List<String> argsList = buildArgs(cucumberReports, tags, features);

            return Main.run(argsList.toArray(new String[0]), Thread.currentThread().getContextClassLoader());
        } finally {
            AxeReportHook.forgetRun(TestContext.getAxeResultDir());
            TestContext.clear();
        }
    }

    /**
     * Baut die Cucumber-CLI-Argumente. {@code features} ist eine kommagetrennte Liste relativer
     * Pfade unterhalb von {@code classpath:features} (per {@code TestExecutionRequest} validiert);
     * jede Datei wird ein eigenes Argument, sonst sucht Cucumber nach einer Datei namens
     * {@code "a.feature,b.feature"}.
     */
    static List<String> buildArgs(Path cucumberReports, String tags, String features) {
        List<String> args = new ArrayList<>();
        args.add("--glue");
        args.add("org.example");
        args.add("--plugin");
        args.add("pretty");
        args.add("--plugin");
        args.add("json:" + cucumberReports.resolve("Cucumber.json"));
        args.add("--plugin");
        args.add("html:" + cucumberReports.resolve("Cucumber.html"));
        args.add("--plugin");
        args.add("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");

        if (tags != null && !tags.isBlank()) {
            args.add("--tags");
            args.add(tags);
        }

        List<String> featurePaths = features == null ? List.of()
                : Arrays.stream(features.split(","))
                        .map(String::trim)
                        .filter(f -> !f.isEmpty())
                        .map(f -> FEATURES_ROOT + "/" + f)
                        .toList();
        if (featurePaths.isEmpty()) {
            args.add(FEATURES_ROOT);
        } else {
            args.addAll(featurePaths);
        }
        return args;
    }
}
