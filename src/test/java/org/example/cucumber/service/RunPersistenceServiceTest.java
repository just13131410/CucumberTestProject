package org.example.cucumber.service;

import org.example.cucumber.model.RunState;
import org.example.cucumber.model.TestExecutionRequest;
import org.example.cucumber.model.TestStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunPersistenceServiceTest {

    private final RunPersistenceService persistence = new RunPersistenceService();

    @AfterEach
    void clearProp() {
        System.clearProperty("test.results.path");
    }

    @Test
    void saveStatus_NoRunDir_IsNoOp(@TempDir Path tempDir) {
        System.setProperty("test.results.path", tempDir.toString());
        UUID runId = UUID.randomUUID();
        // Kein Run-Verzeichnis angelegt → saveStatus darf keine Datei schreiben.
        persistence.saveStatus(runId, TestStatus.builder().runId(runId).status(RunState.COMPLETED).build());
        assertFalse(Files.exists(tempDir.resolve(runId.toString())),
                "ohne Run-Verzeichnis darf nichts geschrieben werden");
    }

    @Test
    void saveAndLoadStatus_RoundTrip(@TempDir Path tempDir) throws Exception {
        System.setProperty("test.results.path", tempDir.toString());
        UUID runId = UUID.randomUUID();
        Files.createDirectories(tempDir.resolve(runId.toString()));

        TestStatus status = TestStatus.builder()
                .runId(runId).status(RunState.COMPLETED).environment("dev")
                .endTime(LocalDateTime.of(2026, 7, 27, 10, 30)).build();
        persistence.saveStatus(runId, status);

        assertTrue(Files.exists(tempDir.resolve(runId.toString()).resolve("status.json")));
        List<TestStatus> loaded = persistence.loadAllStatuses();
        assertEquals(1, loaded.size());
        assertEquals(runId, loaded.get(0).getRunId());
        assertEquals(RunState.COMPLETED, loaded.get(0).getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 27, 10, 30), loaded.get(0).getEndTime());
    }

    @Test
    void outbox_WriteLoadDelete_RoundTrip(@TempDir Path tempDir) throws Exception {
        System.setProperty("test.results.path", tempDir.toString());
        UUID runId = UUID.randomUUID();
        Files.createDirectories(tempDir.resolve(runId.toString()));

        TestExecutionRequest req = new TestExecutionRequest();
        req.setEnvironment("dev");
        req.setTags(List.of("@API-Test"));
        req.setProjectKey("QA");

        persistence.writeOutbox(runId, req, 1);
        List<RunPersistenceService.OutboxEntry> entries = persistence.loadOutboxes();
        assertEquals(1, entries.size());
        assertEquals(runId.toString(), entries.get(0).runId());
        assertEquals(1, entries.get(0).exitCode());
        assertEquals("QA", entries.get(0).request().getProjectKey());
        assertEquals(List.of("@API-Test"), entries.get(0).request().getTags());

        persistence.deleteOutbox(runId);
        assertTrue(persistence.loadOutboxes().isEmpty(), "nach delete darf keine Outbox mehr existieren");
    }

    // --- Q6: atomares Schreiben ---

    @Test
    void writeAtomically_ReplacesExistingFileAndLeavesNoTempFiles(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("status.json");
        Files.writeString(target, "{\"old\":true}");

        persistence.writeAtomically(target, java.util.Map.of("new", true));

        assertEquals("{\"new\":true}", Files.readString(target));
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(target), files.toList(), "Keine .tmp-Reste im Run-Verzeichnis");
        }
    }

    @Test
    void writeAtomically_SerializationFailure_KeepsOldContentAndCleansUp(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("status.json");
        Files.writeString(target, "{\"old\":true}");
        Object notSerializable = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(Exception.class, () -> persistence.writeAtomically(target, notSerializable));

        assertEquals("{\"old\":true}", Files.readString(target), "Alter Inhalt bleibt bei Fehler erhalten");
        try (var files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "Temp-Datei wurde aufgeräumt");
        }
    }
}
