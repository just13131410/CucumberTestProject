package org.example;

import lombok.extern.slf4j.Slf4j;
import org.example.utils.TestResultPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CucumberRunnerService {

    /** Exit-Code, den der Service für einen wegen Timeout hart beendeten Subprozess-Lauf meldet. */
    static final int TIMEOUT_EXIT_CODE = 124;

    /**
     * Ausführungsmodus: {@code subprocess} (Default in Produktion, je Lauf eine eigene JVM →
     * Fault-Isolation, kein Native-/Metaspace-Akkumulieren im Server) oder {@code in-process}
     * (gleiche JVM, historischer Default für lokale Läufe/Tests). Über {@code test.execution.mode}.
     */
    @Value("${test.execution.mode:in-process}")
    private String executionMode = "in-process";

    /** Harte Obergrenze pro Subprozess-Lauf; danach {@code destroyForcibly} (fixt hängende Cancels). */
    @Value("${test.execution.subprocess-timeout-seconds:1800}")
    private long subprocessTimeoutSeconds = 1800;

    /** Zusätzliche JVM-Flags für den Kind-Prozess (z.B. Heap-/Metaspace-Caps), leer = keine. */
    @Value("${test.execution.subprocess-jvm-args:}")
    private String subprocessJvmArgs = "";

    public RunResult runByLabel(String label) throws Exception {
        String normalizedLabel = normalizeLabel(label);
        String runId = UUID.randomUUID().toString();
        return dispatch(runId, normalizedLabel, null);
    }

    public RunResult runByLabel(String runId, String label) throws Exception {
        String normalizedLabel = normalizeLabel(label);
        return dispatch(runId, normalizedLabel, null);
    }

    public RunResult run(String runId, String tags, String features) throws Exception {
        String normalizedTags = (tags != null && !tags.isBlank()) ? normalizeLabel(tags) : null;
        return dispatch(runId, normalizedTags, features);
    }

    /** Wählt Ausführungsmodus und delegiert. */
    private RunResult dispatch(String runId, String tags, String features) throws Exception {
        if ("subprocess".equalsIgnoreCase(executionMode)) {
            return executeInSubprocess(runId, tags, features);
        }
        return executeInProcess(runId, tags, features);
    }

    /** In-Process-Ausführung in der aktuellen JVM (historisches Verhalten). */
    private RunResult executeInProcess(String runId, String tags, String features) throws Exception {
        int exitCode = CucumberInvoker.invoke(runId, tags, features);
        return new RunResult(runId, tags, exitCode, TestResultPaths.forRun(runId).toString());
    }

    /**
     * Out-of-Process-Ausführung: startet {@link CucumberRunnerMain} in einer eigenen JVM mit gleichem
     * Klassenpfad, reicht ergebnisrelevante System-Properties und die komplette Umgebung durch,
     * streamt Ausgaben in {@code <runDir>/runner.log} und erzwingt bei Timeout {@code destroyForcibly}.
     */
    RunResult executeInSubprocess(String runId, String tags, String features) throws Exception {
        Path runDir = TestResultPaths.forRun(runId);
        Files.createDirectories(runDir);
        Path logFile = runDir.resolve("runner.log");

        List<String> cmd = buildSubprocessCommand(runId, tags, features);
        log.info("Starte Cucumber-Subprozess: runId={}, timeout={}s, log={}",
                runId, subprocessTimeoutSeconds, logFile);
        log.debug("Subprozess-Kommando: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        // Umgebung (TEST_RESULTS_PATH, PLAYWRIGHT_*, CONFIG etc.) wird von ProcessBuilder geerbt.

        Process process = pb.start();
        boolean finished = process.waitFor(subprocessTimeoutSeconds, TimeUnit.SECONDS);
        int exitCode;
        if (!finished) {
            log.warn("Subprozess-Timeout ({}s) für runId={} – erzwinge Abbruch", subprocessTimeoutSeconds, runId);
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            exitCode = TIMEOUT_EXIT_CODE;
        } else {
            exitCode = process.exitValue();
        }
        log.info("Cucumber-Subprozess beendet: runId={}, exitCode={}", runId, exitCode);
        return new RunResult(runId, tags, exitCode, runDir.toString());
    }

    /** Baut das {@code java ... CucumberRunnerMain}-Kommando für den Kind-Prozess. */
    List<String> buildSubprocessCommand(String runId, String tags, String features) {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        if (subprocessJvmArgs != null && !subprocessJvmArgs.isBlank()) {
            for (String arg : subprocessJvmArgs.trim().split("\\s+")) {
                cmd.add(arg);
            }
        }
        // Ergebnisrelevante System-Properties an die Kind-JVM weiterreichen.
        forwardProperty(cmd, "test.results.path");
        forwardProperty(cmd, "browser");
        forwardProperty(cmd, "browser.headless");
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(CucumberRunnerMain.class.getName());
        cmd.add(runId);
        cmd.add(tags == null ? "null" : tags);
        cmd.add(features == null ? "null" : features);
        return cmd;
    }

    private void forwardProperty(List<String> cmd, String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            cmd.add("-D" + key + "=" + value);
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
