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
     * Liest einen Konfigurationswert mit folgender Priorität:
     * <ol>
     *   <li>Umgebungsvariable (UPPER_SNAKE_CASE, z.B. von OpenShift ConfigMap/Secret)</li>
     *   <li>System-Property (-Dkey=value)</li>
     *   <li>.env-Datei (lokale Entwicklung)</li>
     *   <li>Secret-Dateien aus CONFIGPATH-Verzeichnis (OpenShift gemountete Secrets)</li>
     *   <li>config.properties (Classpath-Fallback)</li>
     *   <li>Default-Wert</li>
     * </ol>
     */
    public static String get(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace(".", "_");

        String envValue = System.getenv(envKey);
        if (envValue != null) {
            log.debug("Key '{}' bezogen aus: Umgebungsvariable ({})", key, envKey);
            return envValue;
        }

        String sysProp = System.getProperty(key);
        if (sysProp != null) {
            log.debug("Key '{}' bezogen aus: System-Property (-D{})", key, key);
            return sysProp;
        }

        String dotenvValue = dotenv.get(envKey, null);
        if (dotenvValue != null) {
            log.debug("Key '{}' bezogen aus: .env-Datei", key);
            return dotenvValue;
        }

        String secretValue = secretProperties.getProperty(key);
        if (secretValue != null) {
            log.debug("Key '{}' bezogen aus: Secret-Datei (CONFIGPATH)", key);
            return secretValue;
        }

        String propValue = properties.getProperty(key);
        if (propValue != null) {
            log.debug("Key '{}' bezogen aus: config.properties (Classpath)", key);
            return propValue;
        }

        log.debug("Key '{}' nicht gefunden – verwende Default: '{}'", key, defaultValue);
        return defaultValue;
    }
}
