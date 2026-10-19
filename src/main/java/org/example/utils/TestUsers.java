package org.example.utils;

import java.util.regex.Pattern;

/**
 * Zugangsdaten je System und Rolle aus der Konfiguration statt aus den Feature-Dateien, damit jede
 * Ziel-Umgebung eigene Nutzer haben kann. Keys: {@code <system>.user.<rolle>.username} und
 * {@code .password}, umgebungsspezifisch als {@code env.<umgebung>.<system>.user.<rolle>.password}
 * (Auflösung über {@link ConfigReader}).
 */
public final class TestUsers {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private TestUsers() {}

    public record Credentials(String username, String password) {
        @Override
        public String toString() {
            return "Credentials[username=" + username + ", password=***]";
        }
    }

    /**
     * @param system z.B. {@code gui} oder {@code todo}
     * @param role   z.B. {@code admin}
     * @throws IllegalArgumentException bei ungültigem System-/Rollennamen
     * @throws IllegalStateException    wenn Benutzername oder Passwort nicht konfiguriert sind
     */
    public static Credentials forRole(String system, String role) {
        requireName("System", system);
        requireName("Rolle", role);
        String prefix = system + ".user." + role + ".";
        String username = ConfigReader.get(prefix + "username", null);
        String password = ConfigReader.get(prefix + "password", null);
        if (username == null || password == null) {
            throw new IllegalStateException("Keine Zugangsdaten für Rolle '" + role + "' im System '" + system
                    + "' konfiguriert (" + prefix + "username/password"
                    + environmentHint(prefix) + ")");
        }
        return new Credentials(username, password);
    }

    private static String environmentHint(String prefix) {
        String environment = ConfigReader.activeEnvironment();
        return environment == null ? ""
                : ", für Umgebung '" + environment + "' auch " + ConfigReader.environmentKey(environment, prefix)
                + "username/password";
    }

    private static void requireName(String label, String value) {
        if (value == null || !NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " ungültig: '" + value + "' (erlaubt: A-Z, a-z, 0-9, _ und -)");
        }
    }
}
