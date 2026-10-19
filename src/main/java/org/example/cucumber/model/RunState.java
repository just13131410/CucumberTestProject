package org.example.cucumber.model;

/**
 * Lebenszyklus eines Test-Runs. Wird als Name serialisiert ({@code "COMPLETED"} …), das
 * JSON-Format der API und der persistierten {@code status.json} bleibt damit unverändert.
 */
public enum RunState {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    /** Lauf wurde durch einen Neustart des Service unterbrochen. */
    INTERRUPTED;

    /** Endzustand: der Run läuft nicht mehr und darf evictet bzw. gelöscht werden. */
    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }

    /** Wartet oder läuft gerade. */
    public boolean isActive() {
        return !isTerminal();
    }
}
