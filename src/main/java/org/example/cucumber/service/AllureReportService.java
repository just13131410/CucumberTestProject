package org.example.cucumber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.ConfigurationBuilder;
import io.qameta.allure.ReportGenerator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.utils.RunName;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Erzeugt Allure-Reports (Einzel-Run und kombiniert über mehrere Runs) und kapselt die
 * Auflösung der Ergebnis-Verzeichnisse. Ausgelagert aus {@link TestExecutionService}, dessen
 * {@code executeTest()} zuvor Report-Generierung, Status-Verwaltung und Ausführungssteuerung
 * in einer Methode bündelte.
 */
@Slf4j
@Component
public class AllureReportService {

    /** executor.json-{@code reportName} vor der Vereinheitlichung: {@code Run <id> [<env>] <tag>, <tag>}. */
    private static final Pattern LEGACY_REPORT_NAME = Pattern.compile("Run \\S+ \\[([^]]*)] ?(.*)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Ein Lock pro Run für {@link #generateAllureReport}; wird nach Gebrauch wieder entfernt. */
    private final ConcurrentHashMap<UUID, ReentrantLock> runLocks = new ConcurrentHashMap<>();
    private final ReentrantLock combinedLock = new ReentrantLock();
    /** Dedizierter Single-Thread-Executor für die CPU-/Native-lastige Allure-Report-Generierung. */
    private final ExecutorService reportExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "allure-report-generator");
        t.setDaemon(true);
        return t;
    });

    public Path getBaseResultsPath() {
        String envPath = System.getenv("TEST_RESULTS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }
        String sysProp = System.getProperty("test.results.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Path.of(sysProp);
        }
        return Path.of("test-results");
    }

    public Path getResultsPath(UUID runId) {
        return getBaseResultsPath().resolve(runId.toString());
    }

    public void deleteDirectory(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
        }
    }

    /**
     * Pfad zum Cucumber-JSON eines Runs. Der Controller streamt die Datei, statt sie (bei großen
     * Läufen mehrere MB) komplett in einen String zu laden.
     */
    public Optional<Path> getTestReport(UUID runId) {
        Path reportPath = getResultsPath(runId).resolve("cucumber-reports").resolve("Cucumber.json");
        return Files.isRegularFile(reportPath) ? Optional.of(reportPath) : Optional.empty();
    }

    /**
     * Erzeugt den Allure-Report eines Runs. Pro Run serialisiert: der asynchrone Aufruf nach dem
     * Lauf und manuelle {@code /generate}-Aufrufe schreiben sonst gleichzeitig in dasselbe Verzeichnis.
     */
    public Optional<String> generateAllureReport(UUID runId) {
        ReentrantLock lock = runLocks.computeIfAbsent(runId, id -> new ReentrantLock());
        lock.lock();
        try {
            return doGenerateAllureReport(runId);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                runLocks.remove(runId, lock);
            }
        }
    }

    private Optional<String> doGenerateAllureReport(UUID runId) {
        Path allureResultsDir = getResultsPath(runId).resolve("allure-results");
        Path allureReportDir = getResultsPath(runId).resolve("allure-report");

        if (!Files.exists(allureResultsDir)) {
            log.warn("Allure results directory not found for runId: {}", runId);
            return Optional.empty();
        }

        try {
            Files.createDirectories(allureReportDir);
            // Copy history from previous report (enables trends)
            copyHistory(allureReportDir, allureResultsDir);

            generateWithJavaApi(allureReportDir, List.of(allureResultsDir));

            Path indexHtml = allureReportDir.resolve("index.html");
            if (Files.exists(indexHtml)) {
                String reportUrl = "/reports/" + runId + "/allure-report/index.html";
                log.info("Allure report generated successfully for runId: {}", runId);
                return Optional.of(reportUrl);
            } else {
                log.error("Report generation completed but index.html not found at: {}", indexHtml.toAbsolutePath());
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error generating Allure report for runId: {}", runId, e);
            return Optional.empty();
        }
    }

    /** Generiert den Allure-Report asynchron auf einem dedizierten Thread und ruft {@code onSuccess} mit der Report-URL auf. */
    public void generateAllureReportAsync(UUID runId, Consumer<String> onSuccess) {
        reportExecutor.submit(() -> generateAllureReport(runId).ifPresent(onSuccess));
    }

    private void generateWithJavaApi(Path outputDir, List<Path> resultDirs) throws IOException {
        var config = new ConfigurationBuilder().useDefault().build();
        ReportGenerator generator = new ReportGenerator(config);
        generator.generate(outputDir, resultDirs);
    }

    /**
     * Allure gruppiert Suites intern in einer vom Dateisystem abhängigen, nicht chronologischen
     * Reihenfolge. Hier wird nach dem Zeitstempel des Runs ({@code suiteTimestamps}: Suite-Name aus
     * {@link #copyAndEnrichResults} → buildOrder) absteigend sortiert, damit im Overview-Widget und auf
     * der Suites-Seite der neueste Run zuerst erscheint. Der Name allein reicht nicht: er enthält nur
     * das Datum, Runs eines Tages stünden sonst nach Umgebung statt nach Uhrzeit. Unbekannte Suites
     * kommen ans Ende, untereinander absteigend nach Namen.
     */
    private void sortSuitesNewestFirst(Path reportDir, Map<String, Long> suiteTimestamps) {
        Comparator<JsonNode> newestFirst = Comparator
                .comparingLong((JsonNode n) -> suiteTimestamps.getOrDefault(n.path("name").asText(""), Long.MIN_VALUE))
                .thenComparing(n -> n.path("name").asText(""))
                .reversed();
        sortJsonArrayField(reportDir.resolve("widgets").resolve("suites.json"), "items", newestFirst);
        sortJsonArrayField(reportDir.resolve("data").resolve("suites.json"), "children", newestFirst);
    }

    private void sortJsonArrayField(Path jsonFile, String arrayField, Comparator<JsonNode> order) {
        if (!Files.exists(jsonFile)) {
            return;
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonFile.toFile());
            JsonNode arrayNode = root.get(arrayField);
            if (!(arrayNode instanceof ArrayNode array)) {
                return;
            }
            List<JsonNode> items = new ArrayList<>();
            array.forEach(items::add);
            items.sort(order);
            ArrayNode sorted = objectMapper.createArrayNode();
            sorted.addAll(items);
            root.set(arrayField, sorted);
            objectMapper.writeValue(jsonFile.toFile(), root);
        } catch (IOException e) {
            log.warn("Failed to sort suites JSON {}: {}", jsonFile, e.getMessage());
        }
    }

    public Optional<String> getReportUrl(UUID runId) {
        Path allureReportDir = getResultsPath(runId).resolve("allure-report");
        if (Files.exists(allureReportDir) && Files.exists(allureReportDir.resolve("index.html"))) {
            return Optional.of("/reports/" + runId + "/allure-report/index.html");
        }
        return Optional.empty();
    }

    public List<UUID> listAvailableRuns() {
        Path basePath = getBaseResultsPath();
        if (!Files.exists(basePath)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(basePath)) {
            return dirs
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("allure-results")))
                    .map(dir -> {
                        try {
                            return UUID.fromString(dir.getFileName().toString());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list available runs", e);
            return List.of();
        }
    }

    /**
     * Erzeugt den kombinierten Report über mehrere Runs. Nur eine Erzeugung gleichzeitig: sie kopiert
     * alle Ergebnisse und ist CPU-/IO-lastig, parallele Aufrufe würden zudem dasselbe Zielverzeichnis
     * beschreiben. Ein zweiter Aufruf während einer laufenden Erzeugung wird mit
     * {@link CapacityExceededException} (HTTP 429) abgelehnt.
     */
    public Optional<String> generateCombinedAllureReport(List<UUID> runIds) {
        if (!combinedLock.tryLock()) {
            throw new CapacityExceededException("Kombinierter Report wird bereits erzeugt, bitte später erneut versuchen");
        }
        try {
            return doGenerateCombinedAllureReport(runIds);
        } finally {
            combinedLock.unlock();
        }
    }

    private Optional<String> doGenerateCombinedAllureReport(List<UUID> runIds) {
        List<UUID> effectiveRunIds = (runIds == null || runIds.isEmpty())
                ? listAvailableRuns()
                : runIds;

        if (effectiveRunIds.isEmpty()) {
            log.warn("No runs available for combined report");
            return Optional.empty();
        }

        // Filter to runs that actually have allure-results
        List<UUID> validRunIds = effectiveRunIds.stream()
                .filter(id -> Files.exists(getResultsPath(id).resolve("allure-results")))
                .collect(Collectors.toList());

        if (validRunIds.isEmpty()) {
            log.warn("No allure-results directories found for the specified runs");
            return Optional.empty();
        }

        Path tempDir = null;
        try {
            Path combinedReportDir = getBaseResultsPath().resolve("combined").resolve("allure-report");
            Files.createDirectories(combinedReportDir);

            // Read timestamps and sort runs ascending by time (oldest = buildOrder 1, newest = N)
            // so that Allure trend chart is chronologically correct (left=old, right=new)
            record RunEntry(UUID id, long timestamp) {}
            List<RunEntry> sortedRuns = validRunIds.stream()
                    .map(id -> new RunEntry(id,
                            readTimestampFromExecutorJson(getResultsPath(id).resolve("allure-results"))))
                    .sorted(Comparator.comparingLong(RunEntry::timestamp))
                    .collect(Collectors.toList());

            // Create temp directory with enriched copies of all allure-results
            tempDir = Files.createTempDirectory("allure-combined-");
            Map<String, Long> suiteTimestamps = new HashMap<>();
            for (int i = 0; i < sortedRuns.size(); i++) {
                RunEntry run = sortedRuns.get(i);
                Path sourceDir = getResultsPath(run.id()).resolve("allure-results");
                Path targetDir = tempDir.resolve(run.id().toString());
                String suiteLabel = copyAndEnrichResults(sourceDir, targetDir, run.id(), i + 1, run.timestamp());
                suiteTimestamps.put(suiteLabel, run.timestamp());
            }

            // Copy history from previous combined report to newest run's temp dir (enables trends)
            UUID newestId = sortedRuns.getLast().id();
            copyHistory(combinedReportDir, tempDir.resolve(newestId.toString()));

            // Generate report via Java API (kein CLI-Subprocess nötig)
            final Path resolvedTempDir = tempDir;
            List<Path> resultDirs = sortedRuns.stream()
                    .map(run -> resolvedTempDir.resolve(run.id().toString()))
                    .collect(Collectors.toList());
            generateWithJavaApi(combinedReportDir, resultDirs);
            sortSuitesNewestFirst(combinedReportDir, suiteTimestamps);

            Path indexHtml = combinedReportDir.resolve("index.html");
            if (Files.exists(indexHtml)) {
                String reportUrl = "/reports/combined/allure-report/index.html";
                log.info("Combined Allure report generated successfully from {} runs at URL: {}",
                        validRunIds.size(), reportUrl);
                return Optional.of(reportUrl);
            } else {
                log.error("Combined report generation completed but index.html not found at: {}", indexHtml.toAbsolutePath());
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error generating combined Allure report", e);
            return Optional.empty();
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try {
                    deleteDirectory(tempDir);
                } catch (IOException e) {
                    log.warn("Failed to clean up temp directory: {}", tempDir, e);
                }
            }
        }
    }

    /** Kopiert die Ergebnisse eines Runs angereichert nach {@code targetDir} und liefert den Suite-Namen des Runs. */
    private String copyAndEnrichResults(Path sourceDir, Path targetDir, UUID runId,
                                        long buildOrder, long runTimestamp) throws IOException {
        Files.createDirectories(targetDir);
        String runLabel = RunName.shortRunId(runId);
        String suiteLabel = readRunName(sourceDir, runId, runTimestamp);

        try (var files = Files.list(sourceDir)) {
            files.forEach(source -> {
                try {
                    Path target = targetDir.resolve(source.getFileName());
                    String fileName = source.getFileName().toString();

                    if (fileName.endsWith("-result.json")) {
                        enrichResultFile(source, target, runId, runLabel, suiteLabel);
                    } else {
                        // Copy other files as-is (attachments, etc.)
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.warn("Failed to copy/enrich file: {}", source, e);
                }
            });
        }

        // Write executor.json for this run
        Files.writeString(targetDir.resolve("executor.json"),
                buildExecutorJson(suiteLabel, buildOrder, null, runId.toString()));
        return suiteLabel;
    }

    /** Adds a parentSuite/tag label with runId + tags and makes historyId unique per run. */
    private void enrichResultFile(Path source, Path target, UUID runId, String runLabel, String suiteLabel)
            throws IOException {
        ObjectNode result = (ObjectNode) objectMapper.readTree(source.toFile());

        ArrayNode labels = objectMapper.createArrayNode();
        labels.addObject().put("name", "parentSuite").put("value", suiteLabel);
        labels.addObject().put("name", "tag").put("value", "run-" + runLabel);
        if (result.get("labels") instanceof ArrayNode existingLabels) {
            labels.addAll(existingLabels);
        }
        result.set("labels", labels);

        if (result.has("historyId")) {
            result.put("historyId", result.get("historyId").asText() + "-" + runId);
        }

        objectMapper.writeValue(target.toFile(), result);
    }

    /**
     * Liefert den {@link RunName} eines Runs aus dessen executor.json ({@code reportName}). Runs von vor
     * der Vereinheitlichung haben dort noch {@code Run <id> [<env>] <tag>, <tag>}; der Name wird dann aus
     * Umgebung, Tags und dem Zeitstempel des Runs neu aufgebaut. Ohne executor.json bleibt nur die runId.
     */
    private String readRunName(Path allureResultsDir, UUID runId, long runTimestamp) {
        LocalDate date = runTimestamp > 0
                ? LocalDate.ofInstant(Instant.ofEpochMilli(runTimestamp), ZoneId.systemDefault())
                : null;
        Path executorFile = allureResultsDir.resolve("executor.json");
        if (!Files.exists(executorFile)) {
            return RunName.of(date, runId, null, null);
        }
        try {
            String reportName = objectMapper.readTree(executorFile.toFile()).path("reportName").asText("").trim();
            Matcher legacy = LEGACY_REPORT_NAME.matcher(reportName);
            if (legacy.matches()) {
                List<String> tags = Arrays.asList(legacy.group(2).split(","));
                return RunName.of(date, runId, legacy.group(1), tags);
            }
            return reportName.isEmpty() ? RunName.of(date, runId, null, null) : reportName;
        } catch (IOException e) {
            log.warn("Failed to read executor.json from {}", allureResultsDir, e);
            return RunName.of(date, runId, null, null);
        }
    }

    /** Reads the buildOrder field (Unix timestamp in ms) from a run's executor.json. */
    private long readTimestampFromExecutorJson(Path allureResultsDir) {
        Path executorFile = allureResultsDir.resolve("executor.json");
        if (!Files.exists(executorFile)) return 0L;
        try {
            return objectMapper.readTree(executorFile.toFile()).path("buildOrder").asLong(0L);
        } catch (IOException e) {
            log.warn("Failed to read timestamp from executor.json at {}", allureResultsDir, e);
            return 0L;
        }
    }

    /** Write executor.json for Allure (enables executor widget and trends in combined reports). Called after a run finishes. */
    public void writeExecutorJson(UUID runId, TestExecutionRequest request) {
        try {
            Path allureResultsDir = getResultsPath(runId).resolve("allure-results");
            if (!Files.exists(allureResultsDir)) return;

            String runName = RunName.of(LocalDate.now(), runId, request.getEnvironment(), request.getTags());

            Files.writeString(allureResultsDir.resolve("executor.json"),
                    buildExecutorJson(runName, System.currentTimeMillis(), runName, runId.toString()));
        } catch (IOException e) {
            log.warn("Failed to write executor.json for runId={}", runId, e);
        }
    }

    /** Builds the JSON content for Allure's executor.json. {@code reportName} is omitted when null. */
    private String buildExecutorJson(String buildName, long buildOrder, String reportName, String runId)
            throws IOException {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", "Cucumber Test Service");
        node.put("type", "api");
        node.put("buildName", buildName);
        node.put("buildOrder", buildOrder);
        if (reportName != null) {
            node.put("reportName", reportName);
        }
        node.put("reportUrl", "/reports/" + runId + "/allure-report/index.html");
        return objectMapper.writeValueAsString(node);
    }

    private void copyHistory(Path sourceReportDir, Path targetResultsDir) {
        Path historySource = sourceReportDir.resolve("history");
        if (!Files.exists(historySource)) return;

        Path historyTarget = targetResultsDir.resolve("history");
        try {
            Files.createDirectories(historyTarget);
            try (var files = Files.list(historySource)) {
                files.forEach(source -> {
                    try {
                        Files.copy(source, historyTarget.resolve(source.getFileName()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Failed to copy history file: {}", source, e);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to copy history directory", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        reportExecutor.shutdownNow();
    }
}
