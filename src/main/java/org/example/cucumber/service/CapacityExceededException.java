package org.example.cucumber.service;

/**
 * Wird geworfen, wenn keine weitere Test-Ausführung angenommen werden kann, weil die maximale
 * Anzahl gleichzeitiger Läufe plus die begrenzte Warteschlange erschöpft ist (Backpressure).
 * Der Controller übersetzt dies in HTTP 429 (Too Many Requests).
 */
public class CapacityExceededException extends RuntimeException {
    public CapacityExceededException(String message) {
        super(message);
    }
}
