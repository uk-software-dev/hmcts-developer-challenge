package uk.gov.hmcts.reform.dev.services;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task(0, 1, "Prepare bundle", "Prepare the hearing bundle", "0001", null,
            LocalDateTime.of(2026, 12, 1, 0, 0), 1, 12, 2026);
    }

    @Test
    void getTaskListReturnsTasksForCaseFromRepository() {
        List<Task> expected = List.of(task);
        when(taskRepository.findByCaseId(1)).thenReturn(expected);

        assertEquals(expected, taskService.getTaskList(1));
        verify(taskRepository).findByCaseId(1);
    }

    @Test
    void getTaskByIdReturnsMatchingTaskFromRepository() {
        when(taskRepository.findByTaskId(1, 2)).thenReturn(task);

        assertEquals(task, taskService.getTaskById(1, 2));
        verify(taskRepository).findByTaskId(1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001", "0002", "0003", "0004"})
    void createTaskMapsValidStatusCodeAndPersistsTask(String statusCode) {
        task.setStatusCode(statusCode);
        when(taskRepository.create(task)).thenReturn(task);

        assertEquals(task, taskService.createTask(task));
        assertEquals(expectedStatus(statusCode), task.getStatus());
        verify(taskRepository).create(task);
    }

    @Test
    void createTaskRejectsCaseIdLessThanOne() {
        task.setCaseId(0);

        assertBadRequest(() -> taskService.createTask(task));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void createTaskRejectsMissingOrBlankTitle() {
        task.setTitle("  ");

        assertBadRequest(() -> taskService.createTask(task));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void createTaskRejectsMissingOrBlankDescription() {
        task.setDescription("");

        assertBadRequest(() -> taskService.createTask(task));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void createTaskRejectsMissingDueDate() {
        task.setDueDate(null);

        assertBadRequest(() -> taskService.createTask(task));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void createTaskRejectsUnknownStatusCode() {
        task.setStatusCode("9999");

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(task));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void updateTaskChangesStatusAndPersistsUpdatedTask() {
        task.setId(2);
        when(taskRepository.findByTaskId(1, 2)).thenReturn(task);

        Task updated = taskService.updateTask(2, "0004");

        assertEquals("0004", updated.getStatusCode());
        assertEquals("Complete", updated.getStatus());
        verify(taskRepository).update(2, task);
    }

    @Test
    void updateTaskKeepsExistingStatusWhenStatusCodeIsUnknown() {
        task.setId(2);
        task.setStatus("Not started");
        when(taskRepository.findByTaskId(1, 2)).thenReturn(task);

        Task unchanged = taskService.updateTask(2, "9999");

        assertEquals("0001", unchanged.getStatusCode());
        assertEquals("Not started", unchanged.getStatus());
        verify(taskRepository).findByTaskId(1, 2);
        verifyNoMoreInteractions(taskRepository);
    }

    private static void assertBadRequest(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static String expectedStatus(String statusCode) {
        return switch (statusCode) {
            case "0001" -> "Not started";
            case "0002" -> "In progress";
            case "0003" -> "On hold";
            case "0004" -> "Complete";
            default -> throw new IllegalArgumentException("Unexpected test status code.");
        };
    }
}
