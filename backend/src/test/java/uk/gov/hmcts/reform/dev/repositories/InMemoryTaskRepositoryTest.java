package uk.gov.hmcts.reform.dev.repositories;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.hmcts.reform.dev.models.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTaskRepositoryTest {
    private InMemoryTaskRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTaskRepository();
    }

    @Test
    void createAssignsIdsAndFindsTasksByCase() {
        Task first = repository.create(task(1, "First"));
        Task second = repository.create(task(2, "Second"));

        assertTrue(first.getId() > 0);
        assertTrue(second.getId() > first.getId());
        assertEquals(1, repository.findByCaseId(1).size());
        assertEquals(first, repository.findByTaskId(1, first.getId()));
        assertNull(repository.findByTaskId(2, first.getId()));
    }

    @Test
    void updateReplacesTaskAndCanBeRetrievedById() {
        Task saved = repository.create(task(1, "Original"));
        Task replacement = task(1, "Updated");

        assertTrue(repository.update(saved.getId(), replacement).isPresent());
        assertEquals(saved.getId(), replacement.getId());
        assertEquals("Updated", repository.findById(saved.getId()).orElseThrow().getTitle());
        assertTrue(repository.update(404, replacement).isEmpty());
    }

    @Test
    void deleteRemovesTaskAndReportsWhetherItExisted() {
        Task saved = repository.create(task(1, "To delete"));

        assertTrue(repository.delete(saved.getId()));
        assertTrue(repository.findById(saved.getId()).isEmpty());
        assertFalse(repository.delete(saved.getId()));
    }

    private static Task task(int caseId, String title) {
        return new Task(0, caseId, title, "Description", "0001", "Not started",
            LocalDateTime.of(2026, 12, 1, 0, 0), 1, 12, 2026);
    }
}
