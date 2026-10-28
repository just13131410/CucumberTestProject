package org.example.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.example.utils.ConfigReader;

import java.util.Arrays;
import java.util.List;

/**
 * Prüft {@link KnownEnvironment} gegen {@code test.environments}. Die Liste wird bei jeder Prüfung
 * über {@link ConfigReader} gelesen, Änderungen per {@code .env}/ConfigMap greifen also ohne Rebuild.
 */
public class KnownEnvironmentValidator implements ConstraintValidator<KnownEnvironment, String> {

    static final String ENVIRONMENTS_KEY = "test.environments";
    static final String DEFAULT_ENVIRONMENTS = "dev,staging,prod,performance";

    /** Erlaubte Umgebungsnamen (getrimmt, ohne Leereinträge, Groß-/Kleinschreibung wie konfiguriert). */
    public static List<String> allowedEnvironments() {
        return Arrays.stream(ConfigReader.get(ENVIRONMENTS_KEY, DEFAULT_ENVIRONMENTS).split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        List<String> allowed = allowedEnvironments();
        if (allowed.contains(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "Environment muss eine der konfigurierten Umgebungen sein: " + String.join(", ", allowed))
                .addConstraintViolation();
        return false;
    }
}
