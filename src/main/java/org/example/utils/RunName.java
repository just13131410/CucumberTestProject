package org.example.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Einheitlicher Anzeigename eines Testlaufs fuer Zephyr-Test-Cycle und Allure-Reports:
 * {@code <yyyyMMdd> [<umgebung>] <runId-Praefix> <tags>}, z.B. {@code 20260223 [staging] abc12345 @SmokeTest}.
 * Fehlendes Datum, leere Umgebung bzw. leere Tags entfallen.
 */
public final class RunName {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RunName() {
    }

    public static String of(LocalDate date, UUID runId, String environment, List<String> tags) {
        StringJoiner name = new StringJoiner(" ");
        if (date != null) {
            name.add(date.format(DATE_FMT));
        }
        if (environment != null && !environment.isBlank()) {
            name.add("[" + environment.trim() + "]");
        }
        name.add(shortRunId(runId));
        if (tags != null) {
            tags.stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::trim)
                    .forEach(name::add);
        }
        return name.toString();
    }

    public static String shortRunId(UUID runId) {
        return runId.toString().substring(0, 8);
    }
}
