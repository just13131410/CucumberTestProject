package org.example.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZephyrFolder {

    private Long id;
    private String name;
    private String projectKey;
    private String folderType;
}
