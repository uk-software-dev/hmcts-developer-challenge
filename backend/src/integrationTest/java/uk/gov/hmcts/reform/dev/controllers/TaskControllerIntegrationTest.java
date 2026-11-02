package uk.gov.hmcts.reform.dev.controllers;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void taskCanBeCreatedListedRetrievedUpdatedAndDeletedOverHttp() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/save-task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTaskJson(1, "Lifecycle task")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Lifecycle task"))
            .andExpect(jsonPath("$.status").value("Not started"))
            .andReturn();
        int taskId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        MvcResult listResult = mockMvc.perform(get("/get-task-list").param("caseId", "1"))
            .andExpect(status().isOk())
            .andReturn();
        List<Integer> listedIds = JsonPath.read(listResult.getResponse().getContentAsString(), "$[*].id");
        assertThat(listedIds).contains(taskId);

        mockMvc.perform(get("/get-task-by-id")
                .param("caseId", "1")
                .param("taskId", Integer.toString(taskId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Lifecycle task"));

        mockMvc.perform(patch("/patch-task/{id}/{newStatus}", taskId, "0004"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("0004"))
            .andExpect(jsonPath("$.status").value("Complete"));

        mockMvc.perform(get("/get-task-by-id")
                .param("caseId", "1")
                .param("taskId", Integer.toString(taskId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Complete"));

        mockMvc.perform(delete("/delete-task/{id}", taskId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/get-task-by-id")
                .param("caseId", "1")
                .param("taskId", Integer.toString(taskId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void taskEndpointsReturnNotFoundForMissingTasksAndCases() throws Exception {
        mockMvc.perform(get("/get-task-list").param("caseId", "987654"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/get-task-by-id").param("caseId", "1").param("taskId", "2147483647"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/delete-task/{id}", "2147483647"))
            .andExpect(status().isNotFound());
    }

    @Test
    void taskEndpointsRejectMalformedIdsAndRequiredFields() throws Exception {
        mockMvc.perform(delete("/delete-task/{id}", "not-an-id"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/delete-task/{id}", "2147483648"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/save-task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTaskJson(1, " ")))
            .andExpect(status().isBadRequest());

        MvcResult createResult = mockMvc.perform(post("/save-task")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTaskJson(1, "Status validation task")))
            .andExpect(status().isCreated())
            .andReturn();
        int taskId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/patch-task/{id}/{newStatus}", taskId, "9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("0001"))
            .andExpect(jsonPath("$.status").value("Not started"));

        mockMvc.perform(delete("/delete-task/{id}", taskId))
            .andExpect(status().isNoContent());
    }

    @Test
    void exampleCaseEndpointReturnsExpectedCaseData() throws Exception {
        mockMvc.perform(get("/get-example-case"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.caseNumber").value("ABC12345"))
            .andExpect(jsonPath("$.title").value("Case Title"))
            .andExpect(jsonPath("$.description").value("Case Description"))
            .andExpect(jsonPath("$.status").value("Case Status"));
    }

    private static String validTaskJson(int caseId, String title) {
        return """
            {
              "id": 0,
              "caseId": %d,
              "title": "%s",
              "description": "Created over HTTP",
              "statusCode": "0001",
              "dueDate": "2026-12-01T00:00:00",
              "dueDateDay": 1,
              "dueDateMonth": 12,
              "dueDateYear": 2026
            }
            """.formatted(caseId, title);
    }

}
