package org.example.cucumber.controller;

import org.example.cucumber.service.TestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TestExecutionService testExecutionService;

    private ReportController controller;

    private static final UUID TEST_RUN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        controller = new ReportController(testExecutionService, new ReportUrlResolver());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // --- GET /api/v1/test/report/{runId} ---

    @Test
    void getTestReport_Existing_Returns200() throws Exception {
        when(testExecutionService.getTestReport(TEST_RUN_ID))
                .thenReturn(Optional.of("[{\"feature\":\"test\"}]"));

        mockMvc.perform(get("/api/v1/test/report/{runId}", TEST_RUN_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getTestReport_NonExisting_Returns404() throws Exception {
        when(testExecutionService.getTestReport(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/test/report/{runId}", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/v1/test/report/{runId}/generate ---

    @Test
    void generateAllureReport_Success_Returns200() throws Exception {
        when(testExecutionService.generateAllureReport(TEST_RUN_ID))
                .thenReturn(Optional.of("http://localhost:8080/reports/" + TEST_RUN_ID + "/allure-report/index.html"));

        mockMvc.perform(post("/api/v1/test/report/{runId}/generate", TEST_RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").isNotEmpty())
                .andExpect(jsonPath("$.runId").value(TEST_RUN_ID.toString()))
                .andExpect(jsonPath("$.message").value("Allure report successfully generated"));
    }

    @Test
    void generateAllureReport_NotFound_Returns404() throws Exception {
        when(testExecutionService.generateAllureReport(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/test/report/{runId}/generate", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/v1/test/report/{runId}/url ---

    @Test
    void getReportUrl_Existing_Returns200() throws Exception {
        when(testExecutionService.getReportUrl(TEST_RUN_ID))
                .thenReturn(Optional.of("http://localhost:8080/reports/" + TEST_RUN_ID + "/index.html"));

        mockMvc.perform(get("/api/v1/test/report/{runId}/url", TEST_RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").isNotEmpty());
    }

    @Test
    void getReportUrl_NonExisting_Returns404() throws Exception {
        when(testExecutionService.getReportUrl(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/test/report/{runId}/url", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/v1/test/report/combined/generate ---

    @Test
    void generateCombinedReport_WithRunIds_Returns200() throws Exception {
        when(testExecutionService.generateCombinedAllureReport(any()))
                .thenReturn(Optional.of("/reports/combined/allure-report/index.html"));

        String requestJson = """
                {
                    "runIds": ["550e8400-e29b-41d4-a716-446655440000"]
                }
                """;

        mockMvc.perform(post("/api/v1/test/report/combined/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").value(containsString("/reports/combined/allure-report/index.html")))
                .andExpect(jsonPath("$.message").value("Combined Allure report successfully generated"));
    }

    @Test
    void generateCombinedReport_WithoutBody_Returns200() throws Exception {
        when(testExecutionService.generateCombinedAllureReport(null))
                .thenReturn(Optional.of("/reports/combined/allure-report/index.html"));

        mockMvc.perform(post("/api/v1/test/report/combined/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").value(containsString("/reports/combined/allure-report/index.html")));
    }

    @Test
    void generateCombinedReport_NoRunsAvailable_Returns404() throws Exception {
        when(testExecutionService.generateCombinedAllureReport(any()))
                .thenReturn(Optional.empty());

        String requestJson = """
                {
                    "runIds": []
                }
                """;

        mockMvc.perform(post("/api/v1/test/report/combined/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }
}
