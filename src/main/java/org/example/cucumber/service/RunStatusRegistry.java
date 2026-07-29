package org.example.cucumber.service;

import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-Memory-Bestand aller {@link TestStatus}-Objekte: Ablage, Abfrage, Eviction (TTL +
 * Größen-Cap) und Statistik-Aggregation. Ausgelagert aus {@link TestExecutionService}, damit
 * dessen Orchestrierungslogik (Queueing, Ausführung, Report-Anstoß) nicht mit der
 * Status-Buchhaltung vermischt bleibt. {@link TestStatus}-Objekte selbst werden nach dem
 * Abruf direkt mutiert (Lombok {@code @Data}) - die Registry ist bewusst eine dünne Hülle
 * um die Map, keine eigene Kopie der Feld-Setter.
 */
@Slf4j
@Component
public class RunStatusRegistry {

    /** Abgeschlossene Runs älter als N Stunden werden aus dem Speicher evictet (0 = nie via TTL). */
    private final long statusRetentionHours;
    /** Obergrenze der im Speicher gehaltenen Run-Status (verhindert unbegrenztes Wachstum). */
    private final int statusMaxEntries;

    private final Map<UUID, TestStatus> statusMap = new ConcurrentHashMap<>();

    public RunStatusRegistry(
            @Value("${test.execution.status-retention-hours:24}") long statusRetentionHours,
            @Value("${test.execution.status-max-entries:500}") int statusMaxEntries) {
        this.statusRetentionHours = statusRetentionHours;
        this.statusMaxEntries = Math.max(1, statusMaxEntries);
    }

    public void put(UUID runId, TestStatus status) {
        statusMap.put(runId, status);
    }

    public void putIfAbsent(UUID runId, TestStatus status) {
        statusMap.putIfAbsent(runId, status);
    }

    public TestStatus get(UUID runId) {
        return statusMap.get(runId);
    }

    public Optional<TestStatus> find(UUID runId) {
        return Optional.ofNullable(statusMap.get(runId));
    }

    public void remove(UUID runId) {
        statusMap.remove(runId);
    }

    public List<TestStatus> getActiveTests() {
        return statusMap.values().stream()
                .filter(s -> "QUEUED".equals(s.getStatus()) || "RUNNING".equals(s.getStatus()))
                .collect(Collectors.toList());
    }

    public void updateStatus(UUID runId, String newStatus, String errorMessage) {
        TestStatus status = statusMap.get(runId);
        if (status != null) {
            status.setStatus(newStatus);
            if (errorMessage != null) {
                status.setErrorMessage(errorMessage);
            }
        }
    }

    /** True für Endzustände, deren Status evictbar ist. */
    public static boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
                || "CANCELLED".equals(status) || "INTERRUPTED".equals(status);
    }

    /**
     * Entfernt abgeschlossene Run-Status aus dem Speicher: erst per TTL (älter als
     * {@code statusRetentionHours}), dann per Größen-Cap ({@code statusMaxEntries}, ältester zuerst).
     * Laufende/wartende Runs bleiben unangetastet. Best-effort, wirft nie.
     */
    public void evictOldStatuses() {
        try {
            if (statusRetentionHours > 0) {
                Instant cutoff = Instant.now().minus(Duration.ofHours(statusRetentionHours));
                statusMap.entrySet().removeIf(e -> {
                    TestStatus s = e.getValue();
                    if (!isTerminal(s.getStatus()) || s.getEndTime() == null) return false;
                    return s.getEndTime().atZone(ZoneId.systemDefault()).toInstant().isBefore(cutoff);
                });
            }
            int overflow = statusMap.size() - statusMaxEntries;
            if (overflow > 0) {
                statusMap.entrySet().stream()
                        .filter(e -> isTerminal(e.getValue().getStatus()))
                        .sorted(Comparator.comparing(e ->
                                Optional.ofNullable(e.getValue().getEndTime()).orElse(LocalDateTime.MIN)))
                        .limit(overflow)
                        .map(Map.Entry::getKey)
                        .toList()
                        .forEach(statusMap::remove);
            }
        } catch (Exception e) {
            log.debug("Status-Eviction übersprungen: {}", e.getMessage());
        }
    }

    /** Aggregierte Kennzahlen ohne Concurrency-Config (die liefert {@link TestExecutionService} dazu). */
    public Map<String, Object> getStatistics(String environment) {
        var allStatuses = statusMap.values().stream()
                .filter(s -> environment == null || environment.equals(s.getEnvironment()))
                .toList();

        long total = allStatuses.size();
        long completed = allStatuses.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failed = allStatuses.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long running = allStatuses.stream().filter(s -> "RUNNING".equals(s.getStatus())).count();
        long queued = allStatuses.stream().filter(s -> "QUEUED".equals(s.getStatus())).count();

        return new java.util.LinkedHashMap<>(Map.of(
                "totalRuns", total,
                "completedRuns", completed,
                "failedRuns", failed,
                "runningRuns", running,
                "queuedRuns", queued,
                "successRate", total > 0 ? (completed * 100.0 / total) : 0.0
        ));
    }
}
