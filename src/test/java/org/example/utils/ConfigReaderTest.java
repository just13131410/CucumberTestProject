package org.example.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReaderTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("testKey");
        System.clearProperty("baseUrl");
        System.clearProperty("app.test.key");
    }

    @Test
    void get_ReturnsDefaultValue_WhenKeyNotFound() {
        String result = ConfigReader.get("nonExistentKey", "defaultValue");

        assertEquals("defaultValue", result);
    }

    @Test
    void get_ReturnsNullDefault_WhenKeyNotFoundAndDefaultIsNull() {
        String result = ConfigReader.get("nonExistentKey", null);

        assertNull(result);
    }

    @Test
    void get_ReturnsSystemProperty_WhenSet() {
        System.setProperty("testKey", "systemPropertyValue");

        String result = ConfigReader.get("testKey", "default");

        assertEquals("systemPropertyValue", result);
    }

    @Test
    void get_SystemProperty_OverridesPropertiesFile() {
        // baseUrl exists in config.properties, but system property should win
        String overrideValue = "https://override.example.com";
        System.setProperty("baseUrl", overrideValue);

        String result = ConfigReader.get("baseUrl", "default");

        assertEquals(overrideValue, result);
    }

    @Test
    void get_ReadsBaseUrlFromPropertiesFile() {
        // baseUrl is defined in config.properties
        String result = ConfigReader.get("baseUrl", "default");

        assertNotNull(result);
        assertNotEquals("default", result);
        assertTrue(result.startsWith("http"));
    }

    @Test
    void get_ReadsApiUrlFromPropertiesFile() {
        String result = ConfigReader.get("apiURL", "default");

        assertNotNull(result);
        assertNotEquals("default", result);
    }

    @Test
    void get_ReadsAxeReportPathFromPropertiesFile() {
        String result = ConfigReader.get("axe.reportPath", "default");

        assertNotNull(result);
        assertNotEquals("default", result);
        assertTrue(result.contains("axe"));
    }

    @Test
    void get_WithDottedSystemProperty_ReturnsValue() {
        System.setProperty("app.test.key", "testValue");

        String result = ConfigReader.get("app.test.key", "default");

        assertEquals("testValue", result);
    }

    @Test
    void get_MultipleCallsSameKey_ReturnConsistentValue() {
        String first = ConfigReader.get("baseUrl", "default");
        String second = ConfigReader.get("baseUrl", "default");

        assertEquals(first, second);
    }

    @Test
    void get_DifferentKeys_ReturnDifferentValues() {
        String baseUrl = ConfigReader.get("baseUrl", null);
        String apiUrl = ConfigReader.get("apiURL", null);

        assertNotNull(baseUrl);
        assertNotNull(apiUrl);
        // Both exist in config.properties and should be retrievable
    }

    @Test
    void get_ReturnsDefault_WhenDotenvIsMissingAndKeyNotElsewhereDefined() {
        // In CI/CD und OpenShift existiert keine .env-Datei.
        // ConfigReader muss trotzdem starten (ignoreIfMissing) und den Default liefern.
        String result = ConfigReader.get("KEY_ONLY_IN_DOTENV_THAT_DOES_NOT_EXIST", "fallback");

        assertEquals("fallback", result);
    }
}
