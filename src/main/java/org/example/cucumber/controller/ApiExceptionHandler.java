package org.example.cucumber.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.cucumber.service.CapacityExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Zentrale Fehlerübersetzung für alle REST-Controller. Vorher lag der 429-Handler nur im
 * {@link TestExecutionController} und griff z.B. nicht für die Report-Erzeugung im {@link ReportController}.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Übersetzt eine erschöpfte Kapazität (volle Run-Queue, laufende Kombi-Report-Erzeugung) in
     * HTTP 429, sodass Aufrufer die Backpressure erkennen und später erneut versuchen können.
     */
    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<Map<String, String>> handleCapacityExceeded(CapacityExceededException e) {
        log.warn("Anfrage abgelehnt (429): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "capacity_exceeded", "message", e.getMessage()));
    }
}
