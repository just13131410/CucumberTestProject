package org.example.cucumber.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Stößt nach dem App-Start den Abgleich persistierter Läufe an (siehe
 * {@link TestExecutionService#reconcileAfterRestart()}).
 * <p>
 * Abschaltbar über {@code app.startup-tasks.enabled=false}: Der Cucumber-Kontext startet die
 * Anwendung ein zweites Mal in derselben JVM und würde sonst den gerade laufenden Lauf als
 * {@code INTERRUPTED} markieren und offene Outboxen doppelt hochladen.
 */
@Component
@ConditionalOnProperty(name = StartupReconciler.STARTUP_TASKS_ENABLED, havingValue = "true", matchIfMissing = true)
public class StartupReconciler {

    /** Schaltet einmalige Start-Aufgaben ab (Reconcile, Browser-Download beim Start). */
    public static final String STARTUP_TASKS_ENABLED = "app.startup-tasks.enabled";

    private final TestExecutionService testExecutionService;

    public StartupReconciler(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        testExecutionService.reconcileAfterRestart();
    }
}
