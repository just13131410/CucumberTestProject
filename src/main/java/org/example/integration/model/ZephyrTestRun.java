package org.example.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response von {@code GET /rest/atm/1.0/testrun/{testRunKey}}. Wird genutzt, um aus einem
 * bestehenden Template-Testrun die enthaltenen Testfaelle auszulesen ("klonen").
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZephyrTestRun {

    private String key;
    private String projectKey;
    private String name;
    private List<ZephyrTestRunItem> items;
}
