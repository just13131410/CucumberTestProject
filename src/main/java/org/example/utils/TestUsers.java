package org.example.utils;

/**
 * Zugangsdaten je System und Rolle aus der Konfiguration statt aus Feature-Dateien.
 * <p>
 * Schema: {@code <system>.user.<rolle>.username} / {@code .password}, z.B.
 * {@code gui.user.admin.password} (Env {@code GUI_USER_ADMIN_PASSWORD}). Über
 * {@link ConfigReader} greift automatisch der umgebungsspezifische Key, z.B.
 * {@code ENV_TEST_GUI_USER_ADMIN_PASSWORD} für die Umgebung {@code test}. Werte kommen aus
 * {@code .env} (lokal) bzw. Vault/Secret (OpenShift); es gibt bewusst keinen Default.
 */
public final class TestUsers {

    private TestUsers() {}

    /** Benutzername und Passwort einer Rolle; das Passwort erscheint nie in {@link #toString()}. */
    public record Credentials(String username, String password) {
        @Override
        public String toString() {
            return "Credentials[username=" + username + ", password=***]";
        }
    }

    /**
     * @param system z.B. {@code gui} oder {@code todo}
     * @param role   z.B. {@code admin}
     * @throws IllegalStateException wenn Benutzername oder Passwort nicht konfiguriert sind
     */
    public static Credentials forRole(String system, String role) {
        String prefix = system + ".user." + role + ".";
        return new Credentials(require(prefix + "username"), require(prefix + "password"));
    }

    private static String require(String key) {
        String value = ConfigReader.get(key, null);
        if (value != null) {
            return value;
        }
        String scopedKey = ConfigReader.environmentScopedKey(key);
        String hint = scopedKey != null
                ? ConfigReader.toEnvKey(scopedKey) + " oder " + ConfigReader.toEnvKey(key)
                : ConfigReader.toEnvKey(key);
        throw new IllegalStateException("Zugangsdaten fehlen: '" + key + "' ist nicht konfiguriert"
                + " (z.B. per .env/Vault als " + hint + ")");
    }
}
