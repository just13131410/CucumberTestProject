package org.example.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Status eines einzelnen BDD-Schritts, korrespondiert 1:1 zum Zephyr-Testfall-Skript-Schritt an gleicher Position. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZephyrScriptResult {

    private int index;
    private String status;
    private String comment;
}
