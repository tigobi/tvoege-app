package com.example.tvoege_Java.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет SQL статистики из {@link TaskStatsQueries} на настоящем SQLite:
 * схема версии 3 создаётся вручную, затем выполняются те же запросы,
 * что и в {@link QuestionDao}.
 */
public class TaskStatsSqlTest {

    private File dbFile;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        dbFile = File.createTempFile("tvoege_stats", ".db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE `questions` ("
                    + "`id` INTEGER NOT NULL, "
                    + "`taskId` INTEGER NOT NULL, "
                    + "`text` TEXT, "
                    + "`rule` TEXT, "
                    + "`correctAnswer` TEXT, "
                    + "`wrongAnswers` TEXT, "
                    + "`correctCount` INTEGER NOT NULL, "
                    + "`wrongCount` INTEGER NOT NULL, "
                    + "`mistakeDebt` INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(`id`))");
            st.execute("CREATE INDEX `index_questions_taskId` ON `questions` (`taskId`)");

            // Задание 7: освоен 1 вопрос, 2 вопроса в работе над ошибками.
            insert(1, 7, 3, 1, 0);
            insert(2, 7, 0, 2, 2);
            insert(3, 7, 1, 0, 1);
            insert(4, 7, 0, 0, 0);
            // Задание 12: освоен 1 вопрос, ошибок нет.
            insert(5, 12, 2, 0, 0);
            insert(6, 12, 0, 0, 0);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (dbFile != null) {
            dbFile.delete();
        }
    }

    @Test
    public void taskStatsAggregatesOneTask() throws Exception {
        TaskStats stats = queryTaskStats(7);

        assertNotNull(stats);
        assertEquals(7, stats.taskId);
        assertEquals(4, stats.totalCount);
        assertEquals("Освоен только вопрос с correctCount > 0 и mistakeDebt = 0",
                1, stats.masteredCount);
        assertEquals("В работе над ошибками оба вопроса с mistakeDebt > 0",
                2, stats.mistakesCount);
        assertEquals(4, stats.totalCorrect);
        assertEquals(3, stats.totalWrong);
        assertEquals(57, stats.getAccuracyPercent());
        assertEquals(25, stats.getMasteredPercent());
    }

    @Test
    public void taskStatsWithoutQuestionsReturnsNoRow() throws Exception {
        assertNull(queryTaskStats(99));
    }

    @Test
    public void allTasksStatsAreGroupedAndOrderedByTaskId() throws Exception {
        List<TaskStats> stats = queryAllStats();

        assertEquals(2, stats.size());
        assertEquals(7, stats.get(0).taskId);
        assertEquals(12, stats.get(1).taskId);

        TaskStats second = stats.get(1);
        assertEquals(2, second.totalCount);
        assertEquals(1, second.masteredCount);
        assertEquals(0, second.mistakesCount);
        assertEquals(2, second.totalCorrect);
        assertEquals(0, second.totalWrong);
        assertEquals(100, second.getAccuracyPercent());
        assertEquals(50, second.getMasteredPercent());
    }

    @Test
    public void topicsWithZeroAnswersHaveUndefinedAccuracy() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM questions WHERE taskId = 12");
            insert(7, 12, 0, 0, 0);
        }

        TaskStats stats = queryTaskStats(12);

        assertNotNull(stats);
        assertEquals(1, stats.totalCount);
        assertEquals(0, stats.masteredCount);
        assertEquals(0, stats.mistakesCount);
        assertFalse(stats.hasAnswers());
        assertEquals(TaskStats.NO_ACCURACY, stats.getAccuracyPercent());
    }

    @Test
    public void resetAllStatsClearsProgressButKeepsQuestions() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute(TaskStatsQueries.RESET_ALL_STATS);
        }

        assertEquals("Вопросы не должны удаляться", 6, countQuestions());
        for (TaskStats stats : queryAllStats()) {
            assertEquals("Счётчики освоения должны обнулиться", 0, stats.masteredCount);
            assertEquals("Активные ошибки должны обнулиться", 0, stats.mistakesCount);
            assertEquals(0, stats.totalCorrect);
            assertEquals(0, stats.totalWrong);
            assertFalse(stats.hasAnswers());
            assertEquals(TaskStats.NO_ACCURACY, stats.getAccuracyPercent());
        }
    }

    private void insert(int id, int taskId, int correctCount, int wrongCount, int mistakeDebt)
            throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("INSERT INTO questions "
                    + "(id, taskId, text, rule, correctAnswer, wrongAnswers, "
                    + "correctCount, wrongCount, mistakeDebt) VALUES ("
                    + id + ", " + taskId + ", 'вопрос', 'правило', 'верно', '[]', "
                    + correctCount + ", " + wrongCount + ", " + mistakeDebt + ")");
        }
    }

    private TaskStats queryTaskStats(int taskId) throws Exception {
        String sql = TaskStatsQueries.TASK_STATS.replace(":taskId", String.valueOf(taskId));
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return readStats(rs);
        }
    }

    private List<TaskStats> queryAllStats() throws Exception {
        List<TaskStats> stats = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(TaskStatsQueries.ALL_TASK_STATS)) {
            while (rs.next()) {
                stats.add(readStats(rs));
            }
        }
        return stats;
    }

    private TaskStats readStats(ResultSet rs) throws Exception {
        TaskStats stats = new TaskStats();
        stats.taskId = rs.getInt("taskId");
        stats.totalCount = rs.getInt("totalCount");
        stats.masteredCount = rs.getInt("masteredCount");
        stats.mistakesCount = rs.getInt("mistakesCount");
        stats.totalCorrect = rs.getInt("totalCorrect");
        stats.totalWrong = rs.getInt("totalWrong");
        return stats;
    }

    private int countQuestions() throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM questions")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
