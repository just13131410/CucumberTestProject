package org.example.cucumber.service;

import org.example.cucumber.model.TestStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunStatusRegistryTest {

    @Test
    void evictOldStatuses_SizeCap_RemovesOldestTerminalOnly() {
        // TTL aus (0), Größen-Cap = 2
        RunStatusRegistry registry = new RunStatusRegistry(0, 2);
        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
            registry.put(id, TestStatus.builder().runId(id).status("COMPLETED")
                    .endTime(LocalDateTime.now().minusMinutes(i)).build());
        }

        registry.evictOldStatuses();

        assertEquals(2L, registry.getStatistics(null).get("totalRuns"),
                "Größen-Cap muss auf 2 reduzieren (älteste zuerst entfernt)");
    }

    @Test
    void evictOldStatuses_TTL_RemovesExpiredButKeepsFreshAndRunning() {
        RunStatusRegistry registry = new RunStatusRegistry(1, 500);
        UUID oldId = UUID.randomUUID();
        registry.put(oldId, TestStatus.builder().runId(oldId).status("COMPLETED")
                .endTime(LocalDateTime.now().minusHours(2)).build());
        UUID freshId = UUID.randomUUID();
        registry.put(freshId, TestStatus.builder().runId(freshId).status("COMPLETED")
                .endTime(LocalDateTime.now()).build());
        UUID runningId = UUID.randomUUID();
        registry.put(runningId, TestStatus.builder().runId(runningId).status("RUNNING").build());

        registry.evictOldStatuses();

        assertTrue(registry.find(oldId).isEmpty(), "abgelaufener Endzustand muss entfernt werden");
        assertTrue(registry.find(freshId).isPresent(), "frischer Endzustand bleibt");
        assertTrue(registry.find(runningId).isPresent(), "laufender Run wird nie evictet");
    }

    @Test
    void getActiveTests_ReturnsOnlyQueuedAndRunning() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID queuedId = UUID.randomUUID();
        registry.put(queuedId, TestStatus.builder().runId(queuedId).status("QUEUED").build());
        UUID runningId = UUID.randomUUID();
        registry.put(runningId, TestStatus.builder().runId(runningId).status("RUNNING").build());
        UUID completedId = UUID.randomUUID();
        registry.put(completedId, TestStatus.builder().runId(completedId).status("COMPLETED").build());

        var active = registry.getActiveTests();

        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(s -> s.getRunId().equals(queuedId)));
        assertTrue(active.stream().anyMatch(s -> s.getRunId().equals(runningId)));
    }

    @Test
    void updateStatus_SetsStatusAndOptionalErrorMessage() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID id = UUID.randomUUID();
        registry.put(id, TestStatus.builder().runId(id).status("RUNNING").build());

        registry.updateStatus(id, "FAILED", "boom");

        TestStatus status = registry.get(id);
        assertEquals("FAILED", status.getStatus());
        assertEquals("boom", status.getErrorMessage());
    }

    @Test
    void updateStatus_UnknownRunId_DoesNotThrow() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        assertDoesNotThrow(() -> registry.updateStatus(UUID.randomUUID(), "FAILED", "boom"));
    }

    @Test
    void isTerminal_TerminalStatuses_ReturnsTrue() {
        assertTrue(RunStatusRegistry.isTerminal("COMPLETED"));
        assertTrue(RunStatusRegistry.isTerminal("FAILED"));
        assertTrue(RunStatusRegistry.isTerminal("CANCELLED"));
        assertTrue(RunStatusRegistry.isTerminal("INTERRUPTED"));
    }

    @Test
    void isTerminal_NonTerminalStatuses_ReturnsFalse() {
        assertFalse(RunStatusRegistry.isTerminal("QUEUED"));
        assertFalse(RunStatusRegistry.isTerminal("RUNNING"));
    }

    @Test
    void getStatistics_FiltersByEnvironment() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID devId = UUID.randomUUID();
        registry.put(devId, TestStatus.builder().runId(devId).status("COMPLETED").environment("dev").build());
        UUID stagingId = UUID.randomUUID();
        registry.put(stagingId, TestStatus.builder().runId(stagingId).status("COMPLETED").environment("staging").build());

        var devStats = registry.getStatistics("dev");

        assertEquals(1L, devStats.get("totalRuns"));
    }
}
