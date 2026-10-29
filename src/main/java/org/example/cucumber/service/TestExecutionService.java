package org.example.cucumber.service;

import org.example.CucumberRunnerService;
import org.example.cucumber.context.TestContext;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.RunState;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import org.example.integration.zephyr.ZephyrScaleService;
import org.example.utils.ConfigReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestriert Test-Ausführungen: Queueing, Admission Control, Ausführung über
 * {@link CucumberRunnerService}, Status-Fortschritt und Anstoß von Report-Generierung und
 * Zephyr/Jira-Integration nach Abschluss. Status-Buchhaltung ist in {@link RunStatusRegistry}
 * ausgelagert, Allure-Report-Erzeugung in {@link AllureReportService} - diese Klasse bleibt
 * reiner Orchestrator.
 */
@Slf4j
@Service
public class TestExecutionService {

    /**
     * Max. gleichzeitig laufende Runs ohne Browser. In-Process immer 1: die Runs teilen eine JVM;
     * >1 gleichzeitige Runs sprengen auf kleinen Pods den Native-Speicher (OOMKill) UND korrumpieren
     * sich über den prozessweiten Allure-Singleton gegenseitig. Erst mit Out-of-Process-Isolation
     * ({@code test.execution.mode=subprocess}) wirkt {@code test.execution.max-concurrent-runs}.
     */
    private final int maxConcurrentRuns;
    /**
     * Max. gleichzeitig laufende Runs mit Browser ({@link BrowserDemand}), eigener Pool neben
     * {@link #maxConcurrentRuns}. Nur im Subprozess-Modus, {@code test.execution.max-concurrent-browser-runs}.
     */
    private final int maxConcurrentBrowserRuns;
    /** Begrenzte Warteschlange für echte Backpressure. {@code test.execution.max-queue-size}. */
    private final int maxQueueSize;
    /** Subprozess-Modus: jeder Run in eigener JVM, Run-Properties nur als {@code -D} an die Kind-JVM. */
    private final boolean isolated;

    /** Blockiert die Menge der verbotenen System-Property-Präfixe aus Request-Env-Variablen. */
    private static final List<String> BLOCKED_PROP_PREFIXES =
            List.of("java.", "sun.", "os.", "user.", "spring.", "management.", "server.");

    /** Gherkin-Schlüsselwörter für Szenarien (englisch + deutsch), nicht für Examples-Tabellen. */
    private static final Pattern SCENARIO_LINE = Pattern.compile(
            "^\\s*(Scenario|Scenario Outline|Scenario Template|Example|Szenario|Szenariogrundriss|Beispiel):");

    /** Lazy berechnet: die Feature-Dateien ändern sich zur Laufzeit nicht. */
    private volatile Integer scenarioCount;

    private final CucumberRunnerService cucumberRunnerService;
    private final ZephyrScaleService zephyrScaleService;
    private final RunPersistenceService persistence;
    private final RunStatusRegistry statusRegistry;
    private final AllureReportService allureReportService;
    private final BrowserDemand browserDemand;
    private final EnvironmentTagFilter environmentTagFilter;
    private final ThreadPoolExecutor executor;
    /** In-Process identisch mit {@link #executor} (alles seriell), sonst eigener Pool. */
    private final ThreadPoolExecutor browserExecutor;
    private final Map<UUID, Future<?>> runningFutures = new ConcurrentHashMap<>();

    /** Spring-Konstruktor: Concurrency und Queue-Größe über Properties konfigurierbar. */
    @Autowired
    public TestExecutionService(CucumberRunnerService cucumberRunnerService,
                                ZephyrScaleService zephyrScaleService,
                                RunPersistenceService persistence,
                                RunStatusRegistry statusRegistry,
                                AllureReportService allureReportService,
                                @Value("${test.execution.max-concurrent-runs:1}") int maxConcurrentRuns,
                                @Value("${test.execution.max-queue-size:20}") int maxQueueSize,
                                @Value("${test.execution.mode:in-process}") String executionMode,
                                @Value("${test.execution.max-concurrent-browser-runs:2}") int maxConcurrentBrowserRuns,
                                @Value("${test.execution.browser-tags:@GUI}") String browserTags) {
        this.cucumberRunnerService = cucumberRunnerService;
        this.zephyrScaleService = zephyrScaleService;
        this.persistence = persistence;
        this.statusRegistry = statusRegistry;
        this.allureReportService = allureReportService;
        FeatureTags featureTags = new FeatureTags();
        this.browserDemand = new BrowserDemand(browserTags, featureTags);
        this.environmentTagFilter = new EnvironmentTagFilter(featureTags);
        this.isolated = "subprocess".equalsIgnoreCase(executionMode);
        if (!isolated && (maxConcurrentRuns > 1 || maxConcurrentBrowserRuns > 1)) {
            log.error("test.execution.max-concurrent-runs={} / max-concurrent-browser-runs={} ignoriert und "
                    + "auf 1 geklemmt: applyRunProperties()/restoreRunProperties() mutieren prozessweite "
                    + "System-Properties (Browser/Headless/Env-Variablen/Umgebung) - bei >1 gleichzeitigen "
                    + "In-Process-Runs wuerden sich diese Properties gegenseitig ueberschreiben. Erst mit "
                    + "test.execution.mode=subprocess ist >1 daten-korrekt.",
                    maxConcurrentRuns, maxConcurrentBrowserRuns);
        }
        this.maxConcurrentRuns = isolated ? Math.max(1, maxConcurrentRuns) : 1;
        this.maxConcurrentBrowserRuns = isolated ? Math.max(1, maxConcurrentBrowserRuns) : 1;
        this.maxQueueSize = Math.max(1, maxQueueSize);
        this.executor = newPool(this.maxConcurrentRuns, "test-executor-");
        this.browserExecutor = isolated ? newPool(this.maxConcurrentBrowserRuns, "test-executor-browser-") : executor;
        log.info("Ausführung: mode={}, max-concurrent-runs={}, max-concurrent-browser-runs={}",
                isolated ? "subprocess" : "in-process", this.maxConcurrentRuns, this.maxConcurrentBrowserRuns);
    }

    /** In-Process-Konstruktor (Unit-Tests): Pool-Größe wird auf 1 geklemmt. */
    public TestExecutionService(CucumberRunnerService cucumberRunnerService,
                                ZephyrScaleService zephyrScaleService,
                                RunPersistenceService persistence,
                                RunStatusRegistry statusRegistry,
                                AllureReportService allureReportService,
                                int maxConcurrentRuns,
                                int maxQueueSize) {
        this(cucumberRunnerService, zephyrScaleService, persistence, statusRegistry, allureReportService,
                maxConcurrentRuns, maxQueueSize, "in-process", 1, "@GUI");
    }

    /** Backward-compatible Konstruktor (Unit-Tests): Defaults, N=1. */
    public TestExecutionService(CucumberRunnerService cucumberRunnerService,
                                ZephyrScaleService zephyrScaleService) {
        this(cucumberRunnerService, zephyrScaleService, new RunPersistenceService(),
                new RunStatusRegistry(24, 500), new AllureReportService(), 1, 20);
    }

    /** Begrenzte Queue → submit() wirft RejectedExecutionException bei Überlast → echtes 429. */
    private ThreadPoolExecutor newPool(int size, String threadPrefix) {
        return new ThreadPoolExecutor(
                size, size,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxQueueSize),
                r -> {
                    Thread t = new Thread(r);
                    t.setName(threadPrefix + t.getId());
                    t.setDaemon(true);
                    return t;
                });
    }

    public TestExecutionResponse queueTestExecution(TestExecutionRequest request) {
        // Opportunistische Eviction alter Run-Status, damit statusMap nicht unbegrenzt wächst.
        statusRegistry.evictOldStatuses();

        UUID runId = UUID.randomUUID();
        // Request-Tags UND @env:-Regel der Ziel-Umgebung (Szenarien nur für andere Umgebungen fallen weg).
        String tagsExpression = EnvironmentTagFilter.and(
                buildTagsExpression(request.getTags()),
                environmentTagFilter.expressionFor(request.getEnvironment()));
        String features = request.getFeatures() != null
                ? String.join(",", request.getFeatures())
                : null;

        // Initial status: QUEUED
        TestStatus status = TestStatus.builder()
                .runId(runId)
                .status(RunState.QUEUED)
                .environment(request.getEnvironment())
                .progress(0)
                .build();
        statusRegistry.put(runId, status);

        // Browser-Läufe in eigenen, kleineren Pool (nur im Subprozess-Modus getrennt).
        boolean browserRun = isolated && browserDemand.needsBrowser(
                request.getTags(), request.getFeatures(), request.getEnvironment());
        ThreadPoolExecutor pool = browserRun ? browserExecutor : executor;
        int poolSize = browserRun ? maxConcurrentBrowserRuns : maxConcurrentRuns;

        // Admission Control: bei erschöpfter Kapazität (laufend + begrenzte Queue) sofort ablehnen.
        Future<?> future;
        try {
            future = pool.submit(() -> executeTest(runId, tagsExpression, features, request));
        } catch (RejectedExecutionException e) {
            statusRegistry.remove(runId);
            int capacity = poolSize + maxQueueSize;
            log.warn("Kapazität erschöpft, Run abgelehnt (browser={}, max-concurrent={}, max-queue={})",
                    browserRun, poolSize, maxQueueSize);
            throw new CapacityExceededException(
                    "Zu viele gleichzeitige/wartende Test-Läufe (Kapazität " + capacity + ")");
        }
        runningFutures.put(runId, future);

        log.info("Test execution queued: runId={}, tags={}, environment={}, browser={}",
                runId, tagsExpression, request.getEnvironment(), browserRun);

        return TestExecutionResponse.builder()
                .runId(runId)
                .status(RunState.QUEUED)
                .environment(request.getEnvironment())
                .message("Test execution queued successfully")
                .timestamp(LocalDateTime.now())
                .tags(tagsExpression)
                .statusUrl("/api/v1/test/status/" + runId)
                .build();
    }

    private void executeTest(UUID runId, String tags, String features, TestExecutionRequest request) {
        try {
            // false = Status fehlt oder ist bereits final (z.B. vor dem Start abgebrochen)
            TestStatus running = statusRegistry.get(runId);
            if (running == null || !statusRegistry.updateStatus(runId, RunState.RUNNING, null)) {
                log.warn("Lauf runId={} übersprungen: Status fehlt oder ist bereits abgeschlossen", runId);
                return;
            }
            running.setStartTime(LocalDateTime.now());
            running.setCurrentPhase("EXECUTING");

            // In-Process: run-spezifische System-Properties setzen, vorherige Werte für sauberes Restore
            // merken. Im Subprozess-Modus gehen sie nur als -D an die Kind-JVM - parallele Runs dürfen
            // die prozessweiten Properties des Servers nicht gegenseitig überschreiben.
            Map<String, String> runProperties = runProperties(request);
            Map<String, String> previousProps = isolated ? Map.of() : applyRunProperties(runProperties);

            // Count expected scenarios for progress tracking (best-effort, tag-unaware)
            int totalScenarios = scenarioCount();
            Path allureResultsPath = allureReportService.getResultsPath(runId).resolve("allure-results");
            ScheduledExecutorService progressTracker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "progress-tracker-" + runId.toString().substring(0, 8));
                t.setDaemon(true);
                return t;
            });
            progressTracker.scheduleAtFixedRate(() -> {
                try {
                    if (!Files.exists(allureResultsPath)) return;
                    long completed;
                    try (Stream<Path> files = Files.list(allureResultsPath)) {
                        completed = files.filter(p -> p.getFileName().toString().endsWith("-result.json")).count();
                    }
                    TestStatus s = statusRegistry.get(runId);
                    if (s != null) {
                        int pct = totalScenarios > 0
                                ? (int) Math.min(95, completed * 100 / totalScenarios)
                                : (int) Math.min(95, completed * 5);
                        s.setProgress(pct);
                    }
                } catch (IOException ignored) {}
            }, 1, 2, TimeUnit.SECONDS);

            CucumberRunnerService.RunResult result;
            try {
                result = cucumberRunnerService.run(runId.toString(), tags, features, runProperties);
            } finally {
                progressTracker.shutdownNow();
                // Properties nicht in Folge-Runs durchsickern lassen.
                restoreRunProperties(previousProps);
            }

            // Nicht erneut aus der Registry holen: ein paralleles Delete/Evict ergäbe sonst eine NPE.
            TestStatus status = running;
            status.setEndTime(LocalDateTime.now());
            Duration elapsed = Duration.between(status.getStartTime(), status.getEndTime());
            status.setDuration(String.format("%02d:%02d", elapsed.toMinutes(), elapsed.toSecondsPart()));
            status.setProgress(100);
            status.setCurrentPhase("COMPLETED");

            // Build report URLs – einheitlich als /reports/** Direktpfade.
            // ConcurrentHashMap: die Allure-URL wird später vom reportExecutor-Thread ergänzt.
            Map<String, String> reportUrls = new ConcurrentHashMap<>();
            reportUrls.put("cucumber-report", "/reports/" + runId + "/cucumber-reports/Cucumber.html");

            // Accessibility report only for runs that include Frontend/UI tests
            boolean isBackendOnly = request.getTags() != null && !request.getTags().isEmpty()
                    && request.getTags().stream().allMatch(t -> {
                        String tag = t.startsWith("@") ? t.substring(1) : t;
                        return tag.equalsIgnoreCase("Backend") || tag.equalsIgnoreCase("API-Test");
                    });
            if (!isBackendOnly) {
                reportUrls.put("accessibility", "/reports/" + runId + "/axe-result/index.html");
            }

            status.setReportUrls(reportUrls);

            if (result.exitCode() == 0) {
                statusRegistry.updateStatus(runId, RunState.COMPLETED, null);
            } else {
                statusRegistry.updateStatus(runId, RunState.FAILED, "Tests finished with exit code: " + result.exitCode());
            }

            // Write executor.json for Allure (enables executor widget and trends in combined reports)
            allureReportService.writeExecutorJson(runId, request);

            // Terminal-Status persistieren (übersteht Pod-Restarts; Reconcile beim Start liest ihn).
            persistence.saveStatus(runId, status);

            // Integration über eine Outbox: vor dem Upload festhalten, nach Erfolg löschen → ein
            // Restart mitten in der Integration führt zu Replay (at-least-once) statt Datenverlust.
            persistence.writeOutbox(runId, request, result.exitCode());
            zephyrScaleService.uploadRunResults(runId, request, result.exitCode(), status);
            persistence.deleteOutbox(runId);
            persistence.saveStatus(runId, status); // Status inkl. Zephyr/Jira-Metadaten aktualisieren

            // Allure-Report NICHT auf dem Ausführungs-Thread generieren (Freemarker + Asset-Kopien
            // sind CPU-/Native-lastig) → in dedizierten Single-Thread-Executor auslagern.
            final TestStatus finalStatus = status;
            allureReportService.generateAllureReportAsync(runId, url -> {
                Map<String, String> urls = finalStatus.getReportUrls();
                if (urls != null) urls.put("allure", url);
                persistence.saveStatus(runId, finalStatus);
            });

            log.info("Test execution finished: runId={}, exitCode={}", runId, result.exitCode());

        } catch (Exception e) {
            log.error("Test execution error: runId={}", runId, e);
            statusRegistry.updateStatus(runId, RunState.FAILED, e.getMessage());
            TestStatus st = statusRegistry.get(runId);
            if (st != null) {
                st.setEndTime(LocalDateTime.now());
                persistence.saveStatus(runId, st);
            }
        } finally {
            runningFutures.remove(runId);
        }
    }

    /**
     * Run-spezifische Properties eines Requests: gefilterte Env-Variablen ({@link #filterOverrides})
     * plus Browser, Headless und Ziel-Umgebung ({@link ConfigReader#ACTIVE_ENVIRONMENT_PROPERTY}).
     */
    static Map<String, String> runProperties(TestExecutionRequest request) {
        Map<String, String> toSet = filterOverrides(request.getEnvironmentVariables());
        if (request.getBrowser() != null) {
            toSet.put("browser", request.getBrowser());
        }
        if (request.getHeadless() != null) {
            toSet.put("browser.headless", request.getHeadless().toString());
        }
        if (request.getEnvironment() != null) {
            toSet.put(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, request.getEnvironment());
        }
        return toSet;
    }

    /**
     * Setzt die run-spezifischen Properties als System-Properties und liefert einen Snapshot der
     * vorherigen Werte für {@link #restoreRunProperties}. Verhindert, dass Properties eines Laufs in
     * Folge-Läufe durchsickern (globaler Zustand).
     *
     * @return Map key → vorheriger Wert (null-Wert bedeutet: Property war vorher nicht gesetzt)
     */
    private Map<String, String> applyRunProperties(Map<String, String> toSet) {
        Map<String, String> previous = new HashMap<>();
        toSet.forEach((k, v) -> {
            previous.put(k, System.getProperty(k)); // kann null sein
            System.setProperty(k, v);
        });
        return previous;
    }

    /**
     * Übernimmt alle Request-Env-Variablen außer JVM-/Framework-Properties
     * ({@link #BLOCKED_PROP_PREFIXES}, ohne Beachtung der Groß-/Kleinschreibung); verworfene Keys
     * werden protokolliert. Null-Keys/-Werte werden ignoriert.
     */
    static Map<String, String> filterOverrides(Map<String, String> requested) {
        Map<String, String> result = new LinkedHashMap<>();
        if (requested == null) return result;
        requested.forEach((k, v) -> {
            if (k == null || v == null) return;
            String lower = k.toLowerCase();
            if (BLOCKED_PROP_PREFIXES.stream().anyMatch(lower::startsWith)) {
                log.warn("Ignoriere nicht erlaubte System-Property aus Request: {}", k);
                return;
            }
            result.put(k, v);
        });
        return result;
    }

    /** Stellt die vor dem Lauf gesetzten System-Properties wieder her (null = löschen). */
    private void restoreRunProperties(Map<String, String> previous) {
        if (previous == null) return;
        previous.forEach((k, old) -> {
            if (old == null) {
                System.clearProperty(k);
            } else {
                System.setProperty(k, old);
            }
        });
    }

    /**
     * Beim App-Start: persistierte Run-Status wieder in den Speicher laden (Historie überlebt
     * Restart), nicht abgeschlossene Läufe als {@code INTERRUPTED} markieren und offene
     * Integrations-Outbox-Einträge nachholen (at-least-once Zephyr/Jira-Upload).
     * Ausgelöst von {@link StartupReconciler}.
     */
    public void reconcileAfterRestart() {
        try {
            for (TestStatus s : persistence.loadAllStatuses()) {
                if (s == null || s.getRunId() == null) continue;
                if (!RunStatusRegistry.isTerminal(s.getStatus())) {
                    s.setStatus(RunState.INTERRUPTED);
                    s.setErrorMessage("Service wurde während des Laufs neu gestartet");
                    if (s.getEndTime() == null) s.setEndTime(LocalDateTime.now());
                    persistence.saveStatus(s.getRunId(), s);
                }
                statusRegistry.putIfAbsent(s.getRunId(), s);
            }
            for (RunPersistenceService.OutboxEntry e : persistence.loadOutboxes()) {
                try {
                    UUID rid = UUID.fromString(e.runId());
                    TestStatus st = statusRegistry.get(rid);
                    zephyrScaleService.uploadRunResults(rid, e.request(), e.exitCode(), st);
                    persistence.deleteOutbox(rid);
                    if (st != null) persistence.saveStatus(rid, st);
                    log.info("Integrations-Outbox nachgeholt: runId={}", rid);
                } catch (Exception ex) {
                    log.warn("Outbox-Replay fehlgeschlagen für {}: {}", e.runId(), ex.getMessage());
                }
            }
            statusRegistry.evictOldStatuses();
        } catch (Exception e) {
            log.warn("Reconcile beim Start übersprungen: {}", e.getMessage());
        }
    }

    public Optional<TestStatus> getTestStatus(UUID runId) {
        return statusRegistry.find(runId);
    }

    public List<TestStatus> getActiveTests() {
        return statusRegistry.getActiveTests();
    }

    public Optional<Path> getTestReport(UUID runId) {
        return allureReportService.getTestReport(runId);
    }

    public Optional<String> generateAllureReport(UUID runId) {
        return allureReportService.generateAllureReport(runId);
    }

    public Optional<String> getReportUrl(UUID runId) {
        return allureReportService.getReportUrl(runId);
    }

    public Optional<TestStatus> cancelTestExecution(UUID runId) {
        Future<?> future = runningFutures.get(runId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            statusRegistry.updateStatus(runId, RunState.CANCELLED, "Cancelled by user");
            statusRegistry.find(runId).ifPresent(s -> s.setEndTime(LocalDateTime.now()));
            runningFutures.remove(runId);
            log.info("Test execution cancelled: runId={}", runId);
            return statusRegistry.find(runId);
        }
        return Optional.empty();
    }

    public boolean deleteTestExecution(UUID runId) {
        TestStatus status = statusRegistry.get(runId);
        if (status == null) return false;

        // Don't delete running tests
        if (status.getStatus() != null && status.getStatus().isActive()) {
            return false;
        }

        statusRegistry.remove(runId);

        // Clean up files
        Path resultsPath = allureReportService.getResultsPath(runId);
        if (Files.exists(resultsPath)) {
            try {
                allureReportService.deleteDirectory(resultsPath);
            } catch (IOException e) {
                log.warn("Failed to delete results for runId={}", runId, e);
            }
        }
        return true;
    }

    public Object getStatistics(String environment) {
        Map<String, Object> stats = statusRegistry.getStatistics(environment);
        stats.put("maxConcurrentRuns", maxConcurrentRuns);
        stats.put("maxConcurrentBrowserRuns", maxConcurrentBrowserRuns);
        stats.put("maxQueueSize", maxQueueSize);
        return stats;
    }

    public List<UUID> listAvailableRuns() {
        return allureReportService.listAvailableRuns();
    }

    public Optional<String> generateCombinedAllureReport(List<UUID> runIds) {
        return allureReportService.generateCombinedAllureReport(runIds);
    }

    private int countScenariosInFeatures() {
        try {
            java.net.URL featuresUrl = getClass().getClassLoader().getResource("features");
            if (featuresUrl == null) return 0;
            Path featuresDir = Path.of(featuresUrl.toURI());
            try (Stream<Path> walk = Files.walk(featuresDir)) {
                return (int) walk
                        .filter(p -> p.toString().endsWith(".feature"))
                        .flatMap(p -> {
                            try { return Files.lines(p); }
                            catch (IOException e) { return Stream.empty(); }
                        })
                        .filter(TestExecutionService::isScenarioLine)
                        .count();
            }
        } catch (Exception e) {
            log.debug("Could not count scenarios in feature files: {}", e.getMessage());
            return 0;
        }
    }

    private int scenarioCount() {
        Integer count = scenarioCount;
        if (count == null) {
            count = countScenariosInFeatures();
            scenarioCount = count;
        }
        return count;
    }

    static boolean isScenarioLine(String line) {
        return SCENARIO_LINE.matcher(line).find();
    }

    private String buildTagsExpression(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(t -> t.startsWith("@") ? t : "@" + t)
                .collect(Collectors.joining(" or "));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        browserExecutor.shutdownNow();
        allureReportService.shutdown();
    }
}
