package org.example.hooks;

import io.cucumber.java.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CucumberHooksTest {

    private CucumberHooks hooks;

    @BeforeEach
    void setUp() {
        hooks = new CucumberHooks();
    }

    /**
     * Creates a minimal Scenario mock with only the stubs that CucumberHooks actually
     * invokes. Note: scenario.getUri() is NOT stubbed because the Allure lambda in
     * beforeScenario is only executed when an active Allure test case exists — which
     * is never the case in plain unit tests.
     */
    private Scenario mockScenario(String name) {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getName()).thenReturn(name);
        when(scenario.getSourceTagNames()).thenReturn(List.of("@smoke"));
        return scenario;
    }

    // --- Reflection helpers for private ThreadLocal fields ---

    @SuppressWarnings("unchecked")
    private ThreadLocal<String> getScenarioNameField() throws Exception {
        Field f = CucumberHooks.class.getDeclaredField("scenarioName");
        f.setAccessible(true);
        return (ThreadLocal<String>) f.get(hooks);
    }

    @SuppressWarnings("unchecked")
    private ThreadLocal<LocalDateTime> getScenarioStartTimeField() throws Exception {
        Field f = CucumberHooks.class.getDeclaredField("scenarioStartTime");
        f.setAccessible(true);
        return (ThreadLocal<LocalDateTime>) f.get(hooks);
    }

    // --- beforeScenario ---

    @Test
    void beforeScenario_SetsScenarioNameInThreadLocal() throws Exception {
        Scenario scenario = mockScenario("Login Test");

        hooks.beforeScenario(scenario);

        assertEquals("Login Test", getScenarioNameField().get());
    }

    @Test
    void beforeScenario_SetsStartTimeInThreadLocal() throws Exception {
        Scenario scenario = mockScenario("Login Test");
        LocalDateTime before = LocalDateTime.now();

        hooks.beforeScenario(scenario);

        LocalDateTime startTime = getScenarioStartTimeField().get();
        assertNotNull(startTime);
        assertFalse(startTime.isBefore(before));
        assertFalse(startTime.isAfter(LocalDateTime.now()));
    }

    @Test
    void beforeScenario_QueriesNameAndTags() {
        Scenario scenario = mockScenario("Buy Product");

        hooks.beforeScenario(scenario);

        verify(scenario, atLeastOnce()).getName();
        verify(scenario, atLeastOnce()).getSourceTagNames();
    }

    @Test
    void beforeScenario_DoesNotThrow() {
        Scenario scenario = mockScenario("Any Scenario");

        assertDoesNotThrow(() -> hooks.beforeScenario(scenario));
    }

    // --- afterScenario ---

    @Test
    void afterScenario_ClearsScenarioNameThreadLocal() throws Exception {
        Scenario scenario = mockScenario("Login Test");
        hooks.beforeScenario(scenario);

        hooks.afterScenario(scenario);

        assertNull(getScenarioNameField().get());
    }

    @Test
    void afterScenario_ClearsStartTimeThreadLocal() throws Exception {
        Scenario scenario = mockScenario("Login Test");
        hooks.beforeScenario(scenario);

        hooks.afterScenario(scenario);

        assertNull(getScenarioStartTimeField().get());
    }

    @Test
    void afterScenario_QueriesNameAndStatus() {
        Scenario scenario = mockScenario("Buy Product");
        hooks.beforeScenario(scenario);

        hooks.afterScenario(scenario);

        verify(scenario, atLeastOnce()).getStatus();
    }

    // --- Full lifecycle ---

    @Test
    void fullLifecycle_BeforeFollowedByAfter_DoesNotThrow() {
        Scenario scenario = mockScenario("Full Lifecycle Scenario");

        assertDoesNotThrow(() -> {
            hooks.beforeScenario(scenario);
            hooks.afterScenario(scenario);
        });
    }

    @Test
    void fullLifecycle_ThreadLocalsCleanedAfterAfter() throws Exception {
        Scenario scenario = mockScenario("Cleanup Test");
        hooks.beforeScenario(scenario);

        assertNotNull(getScenarioNameField().get());
        assertNotNull(getScenarioStartTimeField().get());

        hooks.afterScenario(scenario);

        assertNull(getScenarioNameField().get());
        assertNull(getScenarioStartTimeField().get());
    }

    @Test
    void consecutiveRuns_EachSetsOwnStartTime() throws Exception {
        Scenario s1 = mockScenario("Scenario One");
        Scenario s2 = mockScenario("Scenario Two");

        hooks.beforeScenario(s1);
        LocalDateTime time1 = getScenarioStartTimeField().get();
        hooks.afterScenario(s1);

        hooks.beforeScenario(s2);
        LocalDateTime time2 = getScenarioStartTimeField().get();
        hooks.afterScenario(s2);

        assertNotNull(time1);
        assertNotNull(time2);
        assertFalse(time2.isBefore(time1));
    }

    @Test
    void consecutiveRuns_SecondRunOverwritesScenarioName() throws Exception {
        Scenario s1 = mockScenario("First Scenario");
        Scenario s2 = mockScenario("Second Scenario");

        hooks.beforeScenario(s1);
        hooks.afterScenario(s1);

        hooks.beforeScenario(s2);

        assertEquals("Second Scenario", getScenarioNameField().get());
        hooks.afterScenario(s2);
    }
}
