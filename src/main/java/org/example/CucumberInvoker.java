package org.example;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import org.example.cucumber.context.TestContext;

import io.cucumber.core.cli.Main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

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

    private CucumberInvoker() {}

    /**
     * @param runId    eindeutige Run-ID (bestimmt die Ausgabeverzeichnisse via TestContext)
     * @param tags     bereits normalisierter Tag-Ausdruck (z.B. "@smoke or @regression") oder null
     * @param features optionaler Feature-Pfad oder null (dann {@code classpath:features})
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

            var argsList = new ArrayList<String>();
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

            if (features != null && !features.isBlank()) {
                argsList.add(features);
            } else {
                argsList.add("classpath:features");
            }

            String[] args = argsList.toArray(new String[0]);
            return Main.run(args, Thread.currentThread().getContextClassLoader());
        } finally {
            TestContext.clear();
        }
    }
}
