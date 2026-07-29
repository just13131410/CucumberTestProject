package org.example.integration.zephyr;

import org.example.cucumber.model.TestStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kleine gemeinsame Helper, um Metadaten/Report-URLs null-sicher in {@link TestStatus}
 * zu schreiben. Von {@link ZephyrScaleService} (echte Uploads) und
 * {@link MockIntegrationService} (Mock-Modus) gemeinsam genutzt.
 */
final class StatusMetadataSupport {

    private StatusMetadataSupport() {
    }

    static void addReportUrl(TestStatus status, String key, String url) {
        if (status == null) return;
        Map<String, String> reportUrls = status.getReportUrls();
        if (reportUrls == null) {
            reportUrls = new LinkedHashMap<>();
            status.setReportUrls(reportUrls);
        }
        reportUrls.put(key, url);
    }

    static void addMetadata(TestStatus status, String key, Object value) {
        if (status == null) return;
        Map<String, Object> metadata = status.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            status.setMetadata(metadata);
        }
        metadata.put(key, value);
    }
}
