package uk.gov.hmcts.reform.dev.repositories;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.gov.hmcts.reform.dev.models.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTaskRepositoryTest {
    @TempDir
    private Path tempDir;

    private SqliteTaskRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteTaskRepository(tempDir.resolve("tasks.db").toString());
    }

    @Test
    void createAssignsIdAndPersistsTaskAcrossRepositoryInstances() {
        Task created = repository.create(task(1, "Prepare bundle"));

        assertTrue(created.getId() > 0);

        SqliteTaskRepository reopenedRepository = new SqliteTaskRepository(tempDir.resolve("tasks.db").toString());
        Task retrieved = reopenedRepository.findById(created.getId()).orElseThrow();

        assertTaskEquals(created, retrieved);
        List<Task> caseTasks = reopenedRepository.findByCaseId(1);
        assertEquals(1, caseTasks.size());
        assertTaskEquals(retrieved, caseTasks.get(0));
        assertTaskEquals(retrieved, reopenedRepository.findByTaskId(1, created.getId()));
        assertNull(reopenedRepository.findByTaskId(2, created.getId()));
    }

    @Test
    void updatePersistsTaskAndReturnsEmptyForUnknownId() {
        Task created = repository.create(task(1, "Original"));
        Task updated = task(2, "Updated");

        Optional<Task> result = repository.update(created.getId(), updated);

        assertTrue(result.isPresent());
        assertEquals(created.getId(), updated.getId());

        SqliteTaskRepository reopenedRepository = new SqliteTaskRepository(tempDir.resolve("tasks.db").toString());
        Task retrieved = reopenedRepository.findById(created.getId()).orElseThrow();
        assertTaskEquals(updated, retrieved);
        assertTrue(reopenedRepository.findById(999).isEmpty());
    }

    @Test
    void deleteRemovesTaskAndReturnsFalseWhenTaskIsAlreadyMissing() {
        Task created = repository.create(task(1, "To delete"));

        assertTrue(repository.delete(created.getId()));
        assertFalse(repository.delete(created.getId()));
        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    void createAssignsUniqueIncreasingIds() {
        Task first = repository.create(task(1, "First"));
        Task second = repository.create(task(1, "Second"));

        assertNotEquals(first.getId(), second.getId());
        assertTrue(second.getId() > first.getId());
    }

    private static Task task(int caseId, String title) {
        return new Task(0, caseId, title, "Description", "0001", "Not started",
            LocalDateTime.of(2026, 12, 1, 0, 0), 1, 12, 2026);
    }

    private static void assertTaskEquals(Task expected, Task actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCaseId(), actual.getCaseId());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getStatusCode(), actual.getStatusCode());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getDueDate(), actual.getDueDate());
        assertEquals(expected.getDueDateDay(), actual.getDueDateDay());
        assertEquals(expected.getDueDateMonth(), actual.getDueDateMonth());
        assertEquals(expected.getDueDateYear(), actual.getDueDateYear());
    }
}
