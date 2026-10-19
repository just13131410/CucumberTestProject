package org.example.cucumber.service;

/**
 * Die angefragte Ziel-Umgebung ist nicht in {@code test.environments} konfiguriert. Wird in HTTP 400
 * übersetzt, statt still gegen die globale (dev-)Konfiguration zu testen.
 */
public class UnknownEnvironmentException extends RuntimeException {
    public UnknownEnvironmentException(String message) {
        super(message);
    }
}
