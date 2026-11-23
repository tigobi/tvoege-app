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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет SQL режима «Микс» из {@link QuestionQueries} на настоящем SQLite:
 * схема версии 3 создаётся вручную, затем выполняются те же запросы,
 * что и в {@link QuestionDao#getAllQuestionsPrioritized()} /
 * {@link QuestionDao#getAllMistakes()}.
 */
public class QuestionMixQueriesSqlTest {

    private File dbFile;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        dbFile = File.createTempFile("tvoege_mix", ".db");
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

            // Задание 7: освоенный, не пройденный и два вопроса с долгом по ошибке.
            insert(1, 7, 0, 0, 0);
            insert(2, 7, 0, 2, 2);
            insert(3, 7, 1, 1, 1);
            insert(4, 7, 3, 0, 0);
            // Задание 12: не пройденный вопрос и две ошибки с разным долгом.
            insert(5, 12, 0, 0, 0);
            insert(6, 12, 0, 5, 3);
            insert(7, 12, 0, 7, 2);
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
    public void allQuestionsPrioritizedReturnsEveryTaskInPriorityOrder() throws Exception {
        List<int[]> rows = queryRows(QuestionQueries.ALL_QUESTIONS_PRIORITIZED);

        assertEquals("Микс должен вернуть вопросы всех заданий", 7, rows.size());

        Set<Integer> ids = new HashSet<>();
        for (int[] row : rows) {
            assertTrue("Вопрос не должен дублироваться", ids.add(row[0]));
        }

        for (int i = 0; i < rows.size(); i++) {
            int expectedRank = i < 4 ? 0 : (i < 6 ? 1 : 2);
            assertEquals("Нарушен порядок приоритетных групп на позиции " + i,
                    expectedRank, priorityRank(rows.get(i)));
        }

        assertEquals("Вопросы с долгом идут первыми", 4, countWithDebt(rows, true));
    }

    @Test
    public void allMistakesReturnsOnlyDebtOrderedByDebtThenWrongCount() throws Exception {
        List<int[]> rows = queryRows(QuestionQueries.ALL_MISTAKES);

        assertEquals("В микс-ошибки попадают только вопросы с mistakeDebt > 0", 4, rows.size());
        assertEquals(4, countWithDebt(rows, true));

        assertEquals("Сначала самый большой долг", 6, rows.get(0)[0]);
        assertEquals("При равном долге — больше ошибок", 7, rows.get(1)[0]);
        assertEquals(2, rows.get(2)[0]);
        assertEquals("Последним — минимальный долг", 3, rows.get(3)[0]);

        for (int i = 1; i < rows.size(); i++) {
            int[] previous = rows.get(i - 1);
            int[] current = rows.get(i);
            assertTrue("mistakeDebt должен убывать", previous[3] >= current[3]);
            if (previous[3] == current[3]) {
                assertTrue("При равном долге wrongCount должен убывать",
                        previous[2] >= current[2]);
            }
        }
    }

    @Test
    public void allMistakesIsEmptyWhenThereIsNoDebt() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute(TaskStatsQueries.RESET_ALL_STATS);
        }

        assertTrue(queryRows(QuestionQueries.ALL_MISTAKES).isEmpty());
        assertEquals("Сброс статистики не удаляет вопросы",
                7, queryRows(QuestionQueries.ALL_QUESTIONS_PRIORITIZED).size());
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

    /** Возвращает строки как {id, correctCount, wrongCount, mistakeDebt}. */
    private List<int[]> queryRows(String sql) throws Exception {
        List<int[]> rows = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new int[]{
                        rs.getInt("id"),
                        rs.getInt("correctCount"),
                        rs.getInt("wrongCount"),
                        rs.getInt("mistakeDebt")
                });
            }
        }
        return rows;
    }

    private int priorityRank(int[] row) {
        if (row[3] > 0) {
            return 0;
        }
        return row[1] == 0 ? 1 : 2;
    }

    private int countWithDebt(List<int[]> rows, boolean hasDebt) {
        int count = 0;
        for (int[] row : rows) {
            if ((row[3] > 0) == hasDebt) {
                count++;
            }
        }
        return count;
    }
}
