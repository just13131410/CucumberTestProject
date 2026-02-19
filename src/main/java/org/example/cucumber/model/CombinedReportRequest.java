package org.example.cucumber.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request fuer kombinierten Allure Report")
public class CombinedReportRequest {

    @Schema(description = "Liste der Run-IDs fuer den kombinierten Report. Leer oder null = alle verfuegbaren Runs.",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> runIds;
}
