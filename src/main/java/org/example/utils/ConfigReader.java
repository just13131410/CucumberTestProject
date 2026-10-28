package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class ConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);
    private static final Properties properties = new Properties();
    private static final Properties secretProperties;
    private static final Dotenv dotenv;

    static {
        // config.properties aus dem Classpath laden (Fallback-Defaults)
        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                properties.load(is);
                log.info("Konfiguration geladen aus: Classpath (config.properties)");
            } else {
                log.warn("config.properties nicht im Classpath gefunden – verwende Defaults.");
            }
        } catch (Exception e) {
            log.warn("Fehler beim Laden von config.properties: {}", e.getMessage());
        }

        // Secret-Dateien laden aus CONFIGPATH (OpenShift ConfigMap/Secret-Mount)
        // Priorität: CONFIGPATH Env-Var → CONFIGPATH System-Property → kein Secret-Verzeichnis
        String configPath = System.getenv("CONFIGPATH");
        if (configPath == null) {
            configPath = System.getProperty("CONFIGPATH");
        }
        secretProperties = (configPath != null) ? loadSecretFiles(configPath) : new Properties();

        // .env-Datei (nur lokale Entwicklung, in OpenShift nicht vorhanden)
        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    /**
     * Lädt alle *.properties-Dateien aus dem angegebenen Verzeichnis und merged sie
     * in ein einzelnes Properties-Objekt (alphabetische Reihenfolge, spätere überschreiben frühere).
     * <p>
     * Package-private für Unit-Tests.
     */
    static Properties loadSecretFiles(String dirPath) {
        Properties result = new Properties();
        File dir = new File(dirPath);

        if (!dir.isDirectory()) {
            log.warn("CONFIGPATH-Verzeichnis nicht gefunden: {} (wird ignoriert)", dirPath);
            return result;
        }

        File[] secretFiles = dir.listFiles((d, name) -> name.endsWith(".properties"));
        if (secretFiles == null || secretFiles.length == 0) {
            log.info("Keine *.properties-Dateien in CONFIGPATH: {}", dirPath);
            return result;
        }

        Arrays.sort(secretFiles);
        for (File file : secretFiles) {
            try (FileReader reader = new FileReader(file)) {
                Properties fileProp = new Properties();
                fileProp.load(reader);
                result.putAll(fileProp);
                log.info("Secrets geladen aus: {}", file.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Fehler beim Laden der Secret-Datei {}: {}", file.getName(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Wandelt einen Property-Key in den erwarteten Umgebungsvariablen-Namen um (z.B.
     * {@code "zephyr.api-token"} -> {@code "ZEPHYR_API_TOKEN"}), analog zu Spring Boots
     * Relaxed-Binding-Konvention für {@code @Value}. Package-private für Unit-Tests.
     */
    static String toEnvKey(String key) {
        return key.toUpperCase().replace(".", "_").replace("-", "_");
    }

    /**
     * Liest einen Konfigurationswert mit folgender Priorität:
     * <ol>
     *   <li>Umgebungsvariable (UPPER_SNAKE_CASE, z.B. von OpenShift ConfigMap/Secret)</li>
     *   <li>System-Property (-Dkey=value)</li>
     *   <li>.env-Datei (lokale Entwicklung)</li>
     *   <li>Secret-Dateien aus CONFIGPATH-Verzeichnis (OpenShift gemountete Secrets)</li>
     *   <li>config.properties (Classpath-Fallback)</li>
     *   <li>Default-Wert</li>
     * </ol>
     * Leere bzw. nur aus Whitespace bestehende Werte gelten als nicht gesetzt, es greift die nächste
     * Quelle. Sonst würde z.B. ein leer angelegtes {@code ZEPHYR_BASE_URL} aus ConfigMap/Secret den
     * Default überdecken und erst beim ersten HTTP-Call als {@code URI is not absolute} auffallen.
     * <p>
     * Ist eine Ziel-Umgebung aktiv ({@value #ACTIVE_ENVIRONMENT_PROPERTY}), wird zuerst
     * {@code env.<umgebung>.<key>} über dieselbe Kette gesucht (Env-Variable z.B.
     * {@code ENV_TEST_BASEURL}), erst danach der globale Key.
     */
    public static String get(String key, String defaultValue) {
        return getWithSource(key, defaultValue).value();
    }

    /**
     * System-Property mit der Ziel-Umgebung des laufenden Testlaufs. Wird bewusst nur als
     * System-Property gelesen (pro Lauf gesetzt bzw. als {@code -D} an die Kind-JVM), nie aus einer
     * Env-Variable - sonst könnte eine Pod-weite Variable alle Läufe auf eine Umgebung zwingen.
     */
    public static final String ACTIVE_ENVIRONMENT_PROPERTY = "test.environment";

    /** Aufgelöster Konfigurationswert plus menschenlesbare Angabe, aus welcher Quelle er stammt. */
    public record ResolvedValue(String value, String source) {}

    /**
     * Wie {@link #get(String, String)}, liefert zusätzlich die Quelle mit - gedacht für
     * Diagnose-/Startup-Logs (z.B. "welche Zephyr-URL/Credentials werden tatsächlich verwendet").
     */
    public static ResolvedValue getWithSource(String key, String defaultValue) {
        String scopedKey = environmentScopedKey(key);
        if (scopedKey != null) {
            ResolvedValue scoped = lookup(scopedKey);
            if (scoped != null) {
                return scoped;
            }
        }
        ResolvedValue global = lookup(key);
        if (global != null) {
            return global;
        }
        log.debug("Key '{}' nicht gefunden – verwende Default: '{}'", key, defaultValue);
        return new ResolvedValue(defaultValue, "Default");
    }

    /**
     * Umgebungsspezifischer Key {@code env.<umgebung>.<key>} oder {@code null}, wenn keine
     * Umgebung aktiv ist. Package-private für Unit-Tests.
     */
    static String environmentScopedKey(String key) {
        String environment = activeEnvironment();
        return environment != null ? "env." + environment + "." + key : null;
    }

    /**
     * Ziel-Umgebung des laufenden Testlaufs ({@value #ACTIVE_ENVIRONMENT_PROPERTY}) oder
     * {@code null}, wenn keine gesetzt ist (z.B. Maven-Lauf ohne Request).
     */
    public static String activeEnvironment() {
        String environment = System.getProperty(ACTIVE_ENVIRONMENT_PROPERTY);
        return isSet(environment) ? environment.strip() : null;
    }

    /** {@code true}, wenn der Lauf gegen eine der genannten Umgebungen geht (Groß-/Kleinschreibung egal). */
    public static boolean isActiveEnvironment(String... environments) {
        String active = activeEnvironment();
        return active != null && Arrays.stream(environments).anyMatch(active::equalsIgnoreCase);
    }

    /** Durchläuft die Quellen-Kette für genau einen Key; {@code null}, wenn keine Quelle ihn setzt. */
    private static ResolvedValue lookup(String key) {
        String envKey = toEnvKey(key);

        String envValue = System.getenv(envKey);
        if (isSet(envValue)) {
            log.debug("Key '{}' bezogen aus: Umgebungsvariable ({})", key, envKey);
            return new ResolvedValue(trim(envValue), "Umgebungsvariable (" + envKey + ")");
        }

        String sysProp = System.getProperty(key);
        if (isSet(sysProp)) {
            log.debug("Key '{}' bezogen aus: System-Property (-D{})", key, key);
            return new ResolvedValue(trim(sysProp), "System-Property (-D" + key + ")");
        }

        String dotenvValue = dotenv.get(envKey, null);
        if (isSet(dotenvValue)) {
            log.debug("Key '{}' bezogen aus: .env-Datei", key);
            return new ResolvedValue(trim(dotenvValue), ".env-Datei");
        }

        String secretValue = secretProperties.getProperty(key);
        if (isSet(secretValue)) {
            log.debug("Key '{}' bezogen aus: Secret-Datei (CONFIGPATH)", key);
            return new ResolvedValue(trim(secretValue), "Secret-Datei (CONFIGPATH)");
        }

        String propValue = properties.getProperty(key);
        if (isSet(propValue)) {
            log.debug("Key '{}' bezogen aus: config.properties (Classpath)", key);
            return new ResolvedValue(trim(propValue), "config.properties (Classpath)");
        }
        return null;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Entfernt fuehrende/abschliessende Whitespaces (inkl. \r von Windows-Zeilenenden in .env-
     * Dateien). Ohne das koennte z.B. ein unsichtbares \r am Ende eines Tokens aus der .env
     * mit ins Basic-Auth-Encoding wandern und die Anmeldung mit einem kaum diagnostizierbaren
     * Fehler scheitern lassen.
     */
    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
