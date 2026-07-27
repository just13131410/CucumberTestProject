package org.example.cucumber.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.example.utils.TestResultPaths;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persistiert Run-Status und eine Integrations-Outbox auf das (PVC-)Dateisystem, damit ein
 * Pod-Restart keine Arbeit vernichtet:
 * <ul>
 *   <li><b>status.json</b> je Run – beim Start wieder eingelesen (Historie überlebt Restart;
 *       nicht abgeschlossene Läufe werden als {@code INTERRUPTED} markiert).</li>
 *   <li><b>integration-outbox.json</b> je Run – vor dem Zephyr/Jira-Upload geschrieben, nach Erfolg
 *       gelöscht. Ein Restart mitten in der Integration führt zu Replay (at-least-once) statt
 *       Datenverlust (genau der Fall, der im Lasttest das WireMock-Journal leerte).</li>
 * </ul>
 *
 * <p>Schreiboperationen sind ein No-op, wenn das Run-Verzeichnis (noch) nicht existiert – so werden
 * Unit-Tests mit gemocktem Runner nicht mit Dateien zugemüllt.
 */
@Slf4j
@Service
public class RunPersistenceService {

    static final String STATUS_FILE = "status.json";
    static final String OUTBOX_FILE = "integration-outbox.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Persistiert den aktuellen Run-Status (nur wenn das Run-Verzeichnis existiert). */
    public void saveStatus(UUID runId, TestStatus status) {
        Path dir = TestResultPaths.forRun(runId.toString());
        if (!Files.exists(dir)) return;
        try {
            mapper.writeValue(dir.resolve(STATUS_FILE).toFile(), status);
        } catch (IOException e) {
            log.warn("Konnte Status nicht persistieren (runId={}): {}", runId, e.getMessage());
        }
    }

    /** Schreibt den Outbox-Eintrag vor dem Integrations-Upload (nur wenn Run-Verzeichnis existiert). */
    public void writeOutbox(UUID runId, TestExecutionRequest request, int exitCode) {
        Path dir = TestResultPaths.forRun(runId.toString());
        if (!Files.exists(dir)) return;
        try {
            mapper.writeValue(dir.resolve(OUTBOX_FILE).toFile(),
                    new OutboxEntry(runId.toString(), request, exitCode));
        } catch (IOException e) {
            log.warn("Konnte Outbox nicht schreiben (runId={}): {}", runId, e.getMessage());
        }
    }

    /** Entfernt den Outbox-Eintrag nach erfolgreichem Upload. */
    public void deleteOutbox(UUID runId) {
        try {
            Files.deleteIfExists(TestResultPaths.forRun(runId.toString()).resolve(OUTBOX_FILE));
        } catch (IOException e) {
            log.warn("Konnte Outbox nicht löschen (runId={}): {}", runId, e.getMessage());
        }
    }

    /** Liest alle persistierten Run-Status vom Dateisystem (für Reconcile beim Start). */
    public List<TestStatus> loadAllStatuses() {
        return loadAll(STATUS_FILE, TestStatus.class);
    }

    /** Liest alle offenen Outbox-Einträge (für Integration-Replay beim Start). */
    public List<OutboxEntry> loadOutboxes() {
        return loadAll(OUTBOX_FILE, OutboxEntry.class);
    }

    private <T> List<T> loadAll(String fileName, Class<T> type) {
        Path base = TestResultPaths.base();
        List<T> result = new ArrayList<>();
        if (!Files.exists(base)) return result;
        try (Stream<Path> dirs = Files.list(base)) {
            dirs.filter(Files::isDirectory).forEach(d -> {
                Path file = d.resolve(fileName);
                if (Files.exists(file)) {
                    try {
                        result.add(mapper.readValue(file.toFile(), type));
                    } catch (IOException e) {
                        log.warn("Konnte {} nicht lesen: {}", file, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Konnte Ergebnisverzeichnis nicht auflisten: {}", e.getMessage());
        }
        return result;
    }

    /** Outbox-Eintrag: der Original-Request plus Exit-Code, um den Upload replayen zu können. */
    public record OutboxEntry(String runId, TestExecutionRequest request, int exitCode) {}
}
