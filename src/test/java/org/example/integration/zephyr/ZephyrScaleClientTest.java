package org.example.integration.zephyr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.integration.jira.JiraClient;
import org.example.integration.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ZephyrScaleClientTest {

    private static final String BASE_URL = "https://jira.example.com";
    private static final String AUTH_HEADER = "Basic " + Base64.getEncoder()
            .encodeToString("user:token".getBytes());

    private MockRestServiceServer mockServer;
    private ZephyrScaleClient zephyrClient;
    private JiraClient jiraClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        zephyrClient = new ZephyrScaleClient(restTemplate, BASE_URL, "user", "token");
        jiraClient = new JiraClient(restTemplate, BASE_URL, "user", "token");
    }

    @Test
    void createTestCycle_BuildsCorrectRequest() throws Exception {
        ZephyrTestCycle cycle = new ZephyrTestCycle();
        cycle.setKey("T-R42");
        cycle.setId(42L);
        cycle.setName("20260223 abc12345 @SmokeTest");

        mockServer.expect(requestTo(BASE_URL + "/rest/atm/1.0/testrun"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTH_HEADER))
                .andRespond(withSuccess(objectMapper.writeValueAsString(cycle), MediaType.APPLICATION_JSON));

        ZephyrTestCycle result = zephyrClient.createTestCycle(
                Map.of("name", "20260223 abc12345 @SmokeTest", "projectKey", "PROJ"));

        mockServer.verify();
        assertNotNull(result);
        assertEquals("T-R42", result.getKey());
        assertEquals(42L, result.getId());
    }

    @Test
    void uploadTestResults_SendsCorrectPayload() throws Exception {
        mockServer.expect(requestTo(BASE_URL + "/rest/atm/1.0/testrun/T-R42/testresults"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTH_HEADER))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        List<ZephyrTestExecution> executions = List.of(
                ZephyrTestExecution.builder()
                        .testCaseKey("T-3511")
                        .statusName("Pass")
                        .build());

        zephyrClient.uploadTestResults("T-R42", executions);

        mockServer.verify();
    }

    @Test
    void getFolderByName_ReturnsEmptyIfNotFound() throws Exception {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/rest/atm/1.0/folder")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<ZephyrFolder> folders = zephyrClient.getFolders("PROJ", "TEST_RUN");

        mockServer.verify();
        assertNotNull(folders);
        assertTrue(folders.isEmpty());
    }

    @Test
    void createIssue_BuiltWithAdfDescription() throws Exception {
        JiraIssueResponse issueResponse = new JiraIssueResponse();
        issueResponse.setKey("PROJ-42");
        issueResponse.setId("10042");

        mockServer.expect(requestTo(BASE_URL + "/rest/api/2/issue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTH_HEADER))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(objectMapper.writeValueAsString(issueResponse), MediaType.APPLICATION_JSON));

        JiraIssueRequest request = JiraIssueRequest.builder()
                .fields(JiraIssueRequest.Fields.builder()
                        .project(Map.of("key", "PROJ"))
                        .summary("Test Automation Failure: abc12345 [@SmokeTest]")
                        .description("Test run *abc12345* failed.\n\nAllure Report: /reports/abc/allure-report/index.html")
                        .issuetype(Map.of("name", "Bug"))
                        .assignee(Map.of("name", "automation-user"))
                        .build())
                .build();

        JiraIssueResponse result = jiraClient.createIssue(request);

        mockServer.verify();
        assertNotNull(result);
        assertEquals("PROJ-42", result.getKey());
    }
}
