package uk.gov.hmcts.reform.dev.repositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import uk.gov.hmcts.reform.dev.models.Task;

@Repository
@ConditionalOnProperty(name = "app.task-repository", havingValue = "sqlite")
public class SqliteTaskRepository implements TaskRepository {
    private static final String TASK_COLUMNS =
        "id, case_id, title, description, status_code, status, due_date, "
            + "due_date_day, due_date_month, due_date_year";

    private final String jdbcUrl;

    public SqliteTaskRepository(@Value("${app.sqlite.path:./data/tasks.db}") String databasePath) {
        Path path = Path.of(databasePath).toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + path;

        try {
            Files.createDirectories(path.getParent());
            initializeSchema();
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Unable to initialize the SQLite task repository.", exception);
        }
    }

    @Override
    public Task create(Task task) {
        String sql = "INSERT INTO tasks (case_id, title, description, status_code, status, due_date, "
            + "due_date_day, due_date_month, due_date_year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setTaskFields(statement, task, 1);
            statement.executeUpdate();

            try (Statement idStatement = connection.createStatement();
                 ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid()")) {
                if (!resultSet.next()) {
                    throw new SQLException("SQLite did not return an ID for the created task.");
                }
                task.setId(resultSet.getInt(1));
            }
            return task;
        } catch (SQLException exception) {
            throw repositoryException("create task", exception);
        }
    }

    @Override
    public List<Task> findByCaseId(int caseId) {
        String sql = "SELECT " + TASK_COLUMNS + " FROM tasks WHERE case_id = ?";
        List<Task> tasks = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(readTask(resultSet));
                }
            }
            return List.copyOf(tasks);
        } catch (SQLException exception) {
            throw repositoryException("find tasks by case", exception);
        }
    }

    @Override
    public Task findByTaskId(int caseId, int taskId) {
        String sql = "SELECT " + TASK_COLUMNS + " FROM tasks WHERE case_id = ? AND id = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, caseId);
            statement.setInt(2, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readTask(resultSet) : null;
            }
        } catch (SQLException exception) {
            throw repositoryException("find task by case and ID", exception);
        }
    }

    @Override
    public Optional<Task> findById(int id) {
        String sql = "SELECT " + TASK_COLUMNS + " FROM tasks WHERE id = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readTask(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw repositoryException("find task by ID", exception);
        }
    }

    @Override
    public Optional<Task> update(int id, Task updated) {
        String sql = "UPDATE tasks SET case_id = ?, title = ?, description = ?, status_code = ?, status = ?, "
            + "due_date = ?, due_date_day = ?, due_date_month = ?, due_date_year = ? WHERE id = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setTaskFields(statement, updated, 1);
            statement.setInt(10, id);

            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
            updated.setId(id);
            return Optional.of(updated);
        } catch (SQLException exception) {
            throw repositoryException("update task", exception);
        }
    }

    @Override
    public boolean delete(int id) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw repositoryException("delete task", exception);
        }
    }

    private void initializeSchema() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                title TEXT,
                description TEXT,
                status_code TEXT,
                status TEXT,
                due_date TEXT,
                due_date_day INTEGER NOT NULL,
                due_date_month INTEGER NOT NULL,
                due_date_year INTEGER NOT NULL
            )
            """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static void setTaskFields(PreparedStatement statement, Task task, int firstIndex) throws SQLException {
        statement.setInt(firstIndex, task.getCaseId());
        statement.setString(firstIndex + 1, task.getTitle());
        statement.setString(firstIndex + 2, task.getDescription());
        statement.setString(firstIndex + 3, task.getStatusCode());
        statement.setString(firstIndex + 4, task.getStatus());
        if (task.getDueDate() == null) {
            statement.setNull(firstIndex + 5, Types.VARCHAR);
        } else {
            statement.setString(firstIndex + 5, task.getDueDate().toString());
        }
        statement.setInt(firstIndex + 6, task.getDueDateDay());
        statement.setInt(firstIndex + 7, task.getDueDateMonth());
        statement.setInt(firstIndex + 8, task.getDueDateYear());
    }

    private static Task readTask(ResultSet resultSet) throws SQLException {
        String dueDate = resultSet.getString("due_date");
        return new Task(
            resultSet.getInt("id"),
            resultSet.getInt("case_id"),
            resultSet.getString("title"),
            resultSet.getString("description"),
            resultSet.getString("status_code"),
            resultSet.getString("status"),
            dueDate == null ? null : LocalDateTime.parse(dueDate),
            resultSet.getInt("due_date_day"),
            resultSet.getInt("due_date_month"),
            resultSet.getInt("due_date_year")
        );
    }

    private static IllegalStateException repositoryException(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation + " in the SQLite task repository.", cause);
    }
}
