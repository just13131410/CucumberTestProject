package org.example.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ein Testfall-Eintrag innerhalb eines {@link ZephyrTestRun}. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZephyrTestRunItem {

    private String testCaseKey;
}
