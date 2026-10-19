package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Erlaubte Ziel-Umgebungen für Test-Läufe ({@code test.environments}, kommagetrennt). Die Namen sind
 * frei wählbar; je Umgebung können URLs und Zugangsdaten als {@code env.<umgebung>.<key>} hinterlegt
 * werden (siehe {@link org.example.utils.ConfigReader}).
 */
@Component
public class TestEnvironments {

    private final List<String> names;

    public TestEnvironments(@Value("${test.environments:dev}") String environments) {
        this.names = Arrays.stream(environments.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (names.isEmpty()) {
            throw new IllegalStateException("test.environments ist leer - mindestens eine Umgebung angeben (z.B. dev)");
        }
    }

    public List<String> names() {
        return names;
    }

    /** Groß-/Kleinschreibung zählt, da der Name Teil der Konfigurations-Keys ist. */
    public boolean isKnown(String environment) {
        return environment != null && names.contains(environment);
    }
}
