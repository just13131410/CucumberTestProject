package org.example.cucumber.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class RunStateTest {

    @ParameterizedTest
    @EnumSource(value = RunState.class, names = {"QUEUED", "RUNNING"})
    void activeStates_AreNotTerminal(RunState state) {
        assertTrue(state.isActive());
        assertFalse(state.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = RunState.class, names = {"COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED"})
    void endStates_AreTerminal(RunState state) {
        assertTrue(state.isTerminal());
        assertFalse(state.isActive());
    }

    @Test
    void json_SerializedAsPlainName_KeepsApiAndPersistenceFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("\"COMPLETED\"", mapper.writeValueAsString(RunState.COMPLETED));
        assertEquals(RunState.INTERRUPTED, mapper.readValue("\"INTERRUPTED\"", RunState.class));
    }
}
