package org.example.integration.zephyr;

import org.example.utils.ConfigReader.ResolvedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ZephyrScaleServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://jira.example.com", "http://wiremock:8080", "HTTPS://jira.example.com/context/"})
    void requireAbsoluteHttpUrl_AcceptsAbsoluteHttpUrls(String url) {
        assertDoesNotThrow(() -> ZephyrScaleService.requireAbsoluteHttpUrl(new ResolvedValue(url, "Test")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/rest/atm/1.0", "wiremock:8080", "jira.example.com", "ftp://jira.example.com", "http://"})
    void requireAbsoluteHttpUrl_RejectsRelativeOrNonHttpUrls(String url) {
        assertThrows(IllegalStateException.class,
                () -> ZephyrScaleService.requireAbsoluteHttpUrl(new ResolvedValue(url, "Test")));
    }

    @Test
    void requireAbsoluteHttpUrl_MessageNamesValueAndSource() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ZephyrScaleService.requireAbsoluteHttpUrl(
                        new ResolvedValue("wiremock:8080", "Umgebungsvariable (ZEPHYR_BASE_URL)")));

        assertTrue(e.getMessage().contains("'wiremock:8080'"), e.getMessage());
        assertTrue(e.getMessage().contains("Umgebungsvariable (ZEPHYR_BASE_URL)"), e.getMessage());
    }
}
