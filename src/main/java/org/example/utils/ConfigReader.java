package org.example.utils;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {
    private static final Properties properties = new Properties();
    private static final Dotenv dotenv;

    static {
        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception e) {
            System.out.println("Keine config.properties gefunden, verwende Defaults.");
        }

        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    /**
     * Priorität:
     * 1. System Environment Variable (z.B. von OpenShift ConfigMap/Secret oder Vault)
     * 2. System Property (z.B. mvn test -Dkey=value)
     * 3. .env-Datei (lokale Entwicklung, wird ignoriert wenn nicht vorhanden)
     * 4. Properties Datei (config.properties)
     * 5. Default Wert
     */
    public static String get(String key, String defaultValue) {
        // 1. Echte Umgebungsvariable (OpenShift, CI/CD)
        String envValue = System.getenv(key.toUpperCase().replace(".", "_"));
        if (envValue != null) return envValue;

        // 2. System Property (-Dkey=value)
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;

        // 3. .env-Datei (nur lokal, in OpenShift nicht vorhanden → wird ignoriert)
        String dotenvValue = dotenv.get(key.toUpperCase().replace(".", "_"), null);
        if (dotenvValue != null) return dotenvValue;

        // 4. config.properties + Default
        return properties.getProperty(key, defaultValue);
    }
}