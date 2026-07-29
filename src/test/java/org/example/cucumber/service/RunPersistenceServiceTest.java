package org.example.cucumber.service;

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
        persistence.saveStatus(runId, TestStatus.builder().runId(runId).status("COMPLETED").build());
        assertFalse(Files.exists(tempDir.resolve(runId.toString())),
                "ohne Run-Verzeichnis darf nichts geschrieben werden");
    }

    @Test
    void saveAndLoadStatus_RoundTrip(@TempDir Path tempDir) throws Exception {
        System.setProperty("test.results.path", tempDir.toString());
        UUID runId = UUID.randomUUID();
        Files.createDirectories(tempDir.resolve(runId.toString()));

        TestStatus status = TestStatus.builder()
                .runId(runId).status("COMPLETED").environment("dev")
                .endTime(LocalDateTime.of(2026, 7, 27, 10, 30)).build();
        persistence.saveStatus(runId, status);

        assertTrue(Files.exists(tempDir.resolve(runId.toString()).resolve("status.json")));
        List<TestStatus> loaded = persistence.loadAllStatuses();
        assertEquals(1, loaded.size());
        assertEquals(runId, loaded.get(0).getRunId());
        assertEquals("COMPLETED", loaded.get(0).getStatus());
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
}
