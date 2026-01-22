package org.example.utils;

import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception e) {
            System.out.println("Keine config.properties gefunden, verwende Defaults.");
        }
    }

    /**
     * Priorität:
     * 1. System Environment Variable (z.B. von Vault/Tekton)
     * 2. System Property (z.B. mvn test -Dkey=value)
     * 3. Properties Datei (config.properties)
     * 4. Default Wert
     */
    public static String get(String key, String defaultValue) {
        // 1. Vault/Environment (z.B. "BASE_URL")
        String envValue = System.getenv(key.toUpperCase().replace(".", "_"));
        if (envValue != null) return envValue;

        // 2. System Property (z.B. "baseUrl")
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;

        // 3. Properties Datei
        return properties.getProperty(key, defaultValue);
    }
}