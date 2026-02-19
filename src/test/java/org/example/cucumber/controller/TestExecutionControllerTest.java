package org.example.cucumber.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.cucumber.model.TestExecutionResponse;
import org.example.cucumber.model.TestStatus;
import org.example.cucumber.service.TestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TestExecutionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TestExecutionService testExecutionService;

    @InjectMocks
    private TestExecutionController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final UUID TEST_RUN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // --- POST /api/v1/test/execute ---

    @Test
    void executeTests_ValidRequest_Returns202() throws Exception {
        TestExecutionResponse response = TestExecutionResponse.builder()
                .runId(TEST_RUN_ID)
                .status("QUEUED")
                .environment("dev")
                .message("Test execution queued successfully")
                .timestamp(LocalDateTime.now())
                .statusUrl("/api/v1/test/status/" + TEST_RUN_ID)
                .tags("@smoke")
                .build();

        when(testExecutionService.queueTestExecution(any()))
                .thenReturn(response);

        String requestJson = """
                {
                    "environment": "dev",
                    "tags": ["@smoke"]
                }
                """;

        mockMvc.perform(post("/api/v1/test/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(TEST_RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.environment").value("dev"));
    }

    @Test
    void executeTests_EmptyBody_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/test/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/v1/test/status/{runId} ---

    @Test
    void getTestStatus_ExistingRun_Returns200() throws Exception {
        TestStatus status = TestStatus.builder()
                .runId(TEST_RUN_ID)
                .status("RUNNING")
                .environment("dev")
                .progress(50)
                .build();

        when(testExecutionService.getTestStatus(TEST_RUN_ID))
                .thenReturn(Optional.of(status));

        mockMvc.perform(get("/api/v1/test/status/{runId}", TEST_RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(TEST_RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress").value(50));
    }

    @Test
    void getTestStatus_NonExistingRun_Returns404() throws Exception {
        when(testExecutionService.getTestStatus(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/test/status/{runId}", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/v1/test/active ---

    @Test
    void getActiveTests_ReturnsList() throws Exception {
        TestStatus status1 = TestStatus.builder().runId(UUID.randomUUID()).status("RUNNING").build();
        TestStatus status2 = TestStatus.builder().runId(UUID.randomUUID()).status("QUEUED").build();

        when(testExecutionService.getActiveTests())
                .thenReturn(List.of(status1, status2));

        mockMvc.perform(get("/api/v1/test/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].status").value("QUEUED"));
    }

    @Test
    void getActiveTests_EmptyList_Returns200() throws Exception {
        when(testExecutionService.getActiveTests())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/test/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
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
                .andExpect(status().isOk());
    }

    @Test
    void getReportUrl_NonExisting_Returns404() throws Exception {
        when(testExecutionService.getReportUrl(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/test/report/{runId}/url", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/v1/test/cancel/{runId} ---

    @Test
    void cancelTestExecution_Existing_Returns200() throws Exception {
        TestStatus cancelled = TestStatus.builder()
                .runId(TEST_RUN_ID)
                .status("CANCELLED")
                .build();

        when(testExecutionService.cancelTestExecution(TEST_RUN_ID))
                .thenReturn(Optional.of(cancelled));

        mockMvc.perform(delete("/api/v1/test/cancel/{runId}", TEST_RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelTestExecution_NonExisting_Returns404() throws Exception {
        when(testExecutionService.cancelTestExecution(TEST_RUN_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/test/cancel/{runId}", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/v1/test/{runId} ---

    @Test
    void deleteTestExecution_Success_Returns204() throws Exception {
        when(testExecutionService.deleteTestExecution(TEST_RUN_ID))
                .thenReturn(true);

        mockMvc.perform(delete("/api/v1/test/{runId}", TEST_RUN_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTestExecution_NotFound_Returns404() throws Exception {
        when(testExecutionService.deleteTestExecution(TEST_RUN_ID))
                .thenReturn(false);

        mockMvc.perform(delete("/api/v1/test/{runId}", TEST_RUN_ID))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/v1/test/health ---

    @Test
    void healthCheck_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/test/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    // --- GET /api/v1/test/statistics ---

    @Test
    void getStatistics_NoFilter_Returns200() throws Exception {
        when(testExecutionService.getStatistics(null))
                .thenReturn(Map.of(
                        "totalRuns", 10L,
                        "completedRuns", 8L,
                        "failedRuns", 2L,
                        "runningRuns", 0L,
                        "queuedRuns", 0L,
                        "successRate", 80.0,
                        "maxConcurrentRuns", 5
                ));

        mockMvc.perform(get("/api/v1/test/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(10))
                .andExpect(jsonPath("$.successRate").value(80.0));
    }

    @Test
    void getStatistics_WithEnvironmentFilter_Returns200() throws Exception {
        when(testExecutionService.getStatistics("dev"))
                .thenReturn(Map.of("totalRuns", 5L));

        mockMvc.perform(get("/api/v1/test/statistics")
                        .param("environment", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(5));
    }

    // --- GET /api/v1/test/runs ---

    @Test
    void listAvailableRuns_Returns200() throws Exception {
        UUID run1 = UUID.randomUUID();
        UUID run2 = UUID.randomUUID();

        when(testExecutionService.listAvailableRuns())
                .thenReturn(List.of(run1, run2));

        mockMvc.perform(get("/api/v1/test/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value(run1.toString()))
                .andExpect(jsonPath("$[1]").value(run2.toString()));
    }

    @Test
    void listAvailableRuns_Empty_Returns200() throws Exception {
        when(testExecutionService.listAvailableRuns())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/test/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- POST /api/v1/test/combined-report/generate ---

    @Test
    void generateCombinedReport_WithRunIds_Returns200() throws Exception {
        when(testExecutionService.generateCombinedAllureReport(any()))
                .thenReturn(Optional.of("/reports/combined/allure-report/index.html"));

        String requestJson = """
                {
                    "runIds": ["550e8400-e29b-41d4-a716-446655440000"]
                }
                """;

        mockMvc.perform(post("/api/v1/test/combined-report/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").value("/reports/combined/allure-report/index.html"))
                .andExpect(jsonPath("$.message").value("Combined Allure report successfully generated"));
    }

    @Test
    void generateCombinedReport_WithoutBody_Returns200() throws Exception {
        when(testExecutionService.generateCombinedAllureReport(null))
                .thenReturn(Optional.of("/reports/combined/allure-report/index.html"));

        mockMvc.perform(post("/api/v1/test/combined-report/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportUrl").value("/reports/combined/allure-report/index.html"));
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

        mockMvc.perform(post("/api/v1/test/combined-report/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }
}
