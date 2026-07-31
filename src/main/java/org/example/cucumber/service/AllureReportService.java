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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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

    private static final DateTimeFormatter RUN_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ObjectMapper objectMapper = new ObjectMapper();
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

    public Optional<Object> getTestReport(UUID runId) {
        Path reportPath = getResultsPath(runId).resolve("cucumber-reports").resolve("Cucumber.json");
        if (Files.exists(reportPath)) {
            try {
                String json = Files.readString(reportPath);
                return Optional.of(json);
            } catch (IOException e) {
                log.error("Failed to read report for runId={}", runId, e);
            }
        }
        return Optional.empty();
    }

    public Optional<String> generateAllureReport(UUID runId) {
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
     * Reihenfolge. Da jeder Suite-Name mit dem in {@link #copyAndEnrichResults} erzeugten Präfix
     * {@code yyyyMMddHHmm} beginnt, wird hier absteigend nach Namen sortiert, damit im Overview-
     * Widget und auf der Suites-Seite der neueste Run zuerst erscheint.
     */
    private void sortSuitesNewestFirst(Path reportDir) {
        sortJsonArrayFieldDescendingByName(reportDir.resolve("widgets").resolve("suites.json"), "items");
        sortJsonArrayFieldDescendingByName(reportDir.resolve("data").resolve("suites.json"), "children");
    }

    private void sortJsonArrayFieldDescendingByName(Path jsonFile, String arrayField) {
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
            items.sort(Comparator.comparing((JsonNode n) -> n.path("name").asText("")).reversed());
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

    public Optional<String> generateCombinedAllureReport(List<UUID> runIds) {
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
            for (int i = 0; i < sortedRuns.size(); i++) {
                RunEntry run = sortedRuns.get(i);
                Path sourceDir = getResultsPath(run.id()).resolve("allure-results");
                Path targetDir = tempDir.resolve(run.id().toString());
                copyAndEnrichResults(sourceDir, targetDir, run.id(), i + 1, run.timestamp());
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
            sortSuitesNewestFirst(combinedReportDir);

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

    private void copyAndEnrichResults(Path sourceDir, Path targetDir, UUID runId,
                                      long buildOrder, long runTimestamp) throws IOException {
        Files.createDirectories(targetDir);
        String runLabel = runId.toString().substring(0, 8);

        // Format the run timestamp as yyyyMMddHHmm (e.g. 202602191316)
        String formattedDate = runTimestamp > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(runTimestamp), ZoneId.systemDefault())
                        .format(RUN_DATE_FMT)
                : "";

        // Read tags from existing executor.json (written during test execution)
        String runTags = readTagsFromExecutorJson(sourceDir);

        // Build label:  <yyyyMMddHHmm> <shortId> <tags>
        String suiteLabel = (formattedDate.isEmpty() ? "" : formattedDate + " ")
                + runLabel
                + (runTags.isEmpty() ? "" : " " + runTags);

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

    private String readTagsFromExecutorJson(Path allureResultsDir) {
        Path executorFile = allureResultsDir.resolve("executor.json");
        if (!Files.exists(executorFile)) {
            return "";
        }
        try {
            // reportName format: "<buildName> [<env>] <tags>" - tags are everything after "]"
            String reportName = objectMapper.readTree(executorFile.toFile()).path("reportName").asText("");
            int bracketEnd = reportName.indexOf(']');
            return bracketEnd < 0 ? "" : reportName.substring(bracketEnd + 1).trim();
        } catch (IOException e) {
            log.warn("Failed to read executor.json from {}", allureResultsDir, e);
            return "";
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

            String buildName = "Run " + runId.toString().substring(0, 8);
            String env = request.getEnvironment() != null ? request.getEnvironment() : "unknown";
            String tags = request.getTags() != null ? String.join(", ", request.getTags()) : "";
            String reportName = String.format("%s [%s] %s", buildName, env, tags);

            Files.writeString(allureResultsDir.resolve("executor.json"),
                    buildExecutorJson(buildName, System.currentTimeMillis(), reportName, runId.toString()));
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
