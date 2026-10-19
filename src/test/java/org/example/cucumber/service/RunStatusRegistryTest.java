package org.example.cucumber.service;

import org.example.cucumber.model.RunState;
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
            registry.put(id, TestStatus.builder().runId(id).status(RunState.COMPLETED)
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
        registry.put(oldId, TestStatus.builder().runId(oldId).status(RunState.COMPLETED)
                .endTime(LocalDateTime.now().minusHours(2)).build());
        UUID freshId = UUID.randomUUID();
        registry.put(freshId, TestStatus.builder().runId(freshId).status(RunState.COMPLETED)
                .endTime(LocalDateTime.now()).build());
        UUID runningId = UUID.randomUUID();
        registry.put(runningId, TestStatus.builder().runId(runningId).status(RunState.RUNNING).build());

        registry.evictOldStatuses();

        assertTrue(registry.find(oldId).isEmpty(), "abgelaufener Endzustand muss entfernt werden");
        assertTrue(registry.find(freshId).isPresent(), "frischer Endzustand bleibt");
        assertTrue(registry.find(runningId).isPresent(), "laufender Run wird nie evictet");
    }

    @Test
    void getActiveTests_ReturnsOnlyQueuedAndRunning() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID queuedId = UUID.randomUUID();
        registry.put(queuedId, TestStatus.builder().runId(queuedId).status(RunState.QUEUED).build());
        UUID runningId = UUID.randomUUID();
        registry.put(runningId, TestStatus.builder().runId(runningId).status(RunState.RUNNING).build());
        UUID completedId = UUID.randomUUID();
        registry.put(completedId, TestStatus.builder().runId(completedId).status(RunState.COMPLETED).build());

        var active = registry.getActiveTests();

        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(s -> s.getRunId().equals(queuedId)));
        assertTrue(active.stream().anyMatch(s -> s.getRunId().equals(runningId)));
    }

    @Test
    void updateStatus_SetsStatusAndOptionalErrorMessage() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID id = UUID.randomUUID();
        registry.put(id, TestStatus.builder().runId(id).status(RunState.RUNNING).build());

        registry.updateStatus(id, RunState.FAILED, "boom");

        TestStatus status = registry.get(id);
        assertEquals(RunState.FAILED, status.getStatus());
        assertEquals("boom", status.getErrorMessage());
    }

    @Test
    void updateStatus_TerminalStatusIsFinal_CancelledNotOverwrittenByFailed() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID id = UUID.randomUUID();
        registry.put(id, TestStatus.builder().runId(id).status(RunState.RUNNING).build());

        assertTrue(registry.updateStatus(id, RunState.CANCELLED, "Cancelled by user"));
        assertFalse(registry.updateStatus(id, RunState.FAILED, "InterruptedException"));

        TestStatus status = registry.get(id);
        assertEquals(RunState.CANCELLED, status.getStatus());
        assertEquals("Cancelled by user", status.getErrorMessage());
    }

    @Test
    void updateStatus_UnknownRunId_DoesNotThrow() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        assertDoesNotThrow(() -> registry.updateStatus(UUID.randomUUID(), RunState.FAILED, "boom"));
    }

    @Test
    void isTerminal_TerminalStatuses_ReturnsTrue() {
        assertTrue(RunStatusRegistry.isTerminal(RunState.COMPLETED));
        assertTrue(RunStatusRegistry.isTerminal(RunState.FAILED));
        assertTrue(RunStatusRegistry.isTerminal(RunState.CANCELLED));
        assertTrue(RunStatusRegistry.isTerminal(RunState.INTERRUPTED));
    }

    @Test
    void isTerminal_NonTerminalStatuses_ReturnsFalse() {
        assertFalse(RunStatusRegistry.isTerminal(RunState.QUEUED));
        assertFalse(RunStatusRegistry.isTerminal(RunState.RUNNING));
    }

    @Test
    void getStatistics_FiltersByEnvironment() {
        RunStatusRegistry registry = new RunStatusRegistry(24, 500);
        UUID devId = UUID.randomUUID();
        registry.put(devId, TestStatus.builder().runId(devId).status(RunState.COMPLETED).environment("dev").build());
        UUID stagingId = UUID.randomUUID();
        registry.put(stagingId, TestStatus.builder().runId(stagingId).status(RunState.COMPLETED).environment("staging").build());

        var devStats = registry.getStatistics("dev");

        assertEquals(1L, devStats.get("totalRuns"));
    }
}
