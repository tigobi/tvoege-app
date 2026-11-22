package com.example.tvoege_Java.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет SQL миграции 2 -> 3 на настоящем SQLite, без Android:
 * схема версии 2 создаётся вручную, затем применяются те же запросы,
 * что и в {@link AppDatabase#MIGRATION_2_3}.
 */
public class Migration2To3Test {

    private File dbFile;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        dbFile = File.createTempFile("tvoege_migration_2_3", ".db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            // Схема questions версии 2 (см. AppDatabase.MIGRATION_1_2).
            st.execute("CREATE TABLE `questions` ("
                    + "`id` INTEGER NOT NULL, "
                    + "`taskId` INTEGER NOT NULL, "
                    + "`text` TEXT, "
                    + "`rule` TEXT, "
                    + "`correctAnswer` TEXT, "
                    + "`wrongAnswers` TEXT, "
                    + "`correctCount` INTEGER NOT NULL, "
                    + "`wrongCount` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");
            st.execute("CREATE INDEX `index_questions_taskId` ON `questions` (`taskId`)");
            st.execute("INSERT INTO questions VALUES "
                    + "(1, 7, 'без ошибок', 'правило', 'верно', '[]', 5, 0)");
            st.execute("INSERT INTO questions VALUES "
                    + "(2, 7, 'с ошибками', 'правило', 'верно', '[]', 1, 3)");
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
    public void migrationAddsMistakeDebtColumn() throws Exception {
        migrate();

        boolean found = false;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`questions`)")) {
            while (rs.next()) {
                if ("mistakeDebt".equals(rs.getString("name"))) {
                    found = true;
                    assertEquals("INTEGER", rs.getString("type"));
                    assertEquals(1, rs.getInt("notnull"));
                    assertEquals("0", rs.getString("dflt_value"));
                }
            }
        }
        assertTrue("Колонка mistakeDebt не создана", found);
    }

    @Test
    public void existingMistakesGetDebtTwo() throws Exception {
        migrate();

        assertEquals("Вопрос без ошибок не должен попадать в работу над ошибками",
                0, valueOf(1, "mistakeDebt"));
        assertEquals("Существующие ошибки должны сохраниться",
                2, valueOf(2, "mistakeDebt"));
    }

    @Test
    public void progressStatisticsArePreserved() throws Exception {
        migrate();

        assertEquals(5, valueOf(1, "correctCount"));
        assertEquals(0, valueOf(1, "wrongCount"));
        assertEquals(1, valueOf(2, "correctCount"));
        assertEquals(3, valueOf(2, "wrongCount"));
    }

    @Test
    public void newRowsUseZeroDebtByDefault() throws Exception {
        migrate();

        try (Statement st = connection.createStatement()) {
            st.execute("INSERT INTO questions "
                    + "(id, taskId, text, rule, correctAnswer, wrongAnswers, "
                    + "correctCount, wrongCount) VALUES "
                    + "(3, 7, 'новый', 'правило', 'верно', '[]', 0, 0)");
        }

        assertEquals(0, valueOf(3, "mistakeDebt"));
    }

    @Test
    public void debtDecreasesOnCorrectAndResetsOnWrongWithoutTouchingStats() throws Exception {
        migrate();

        // Повторяет SQL из QuestionDao#incrementCorrect / incrementWrong.
        try (Statement st = connection.createStatement()) {
            for (int i = 0; i < 2; i++) {
                st.execute("UPDATE questions SET correctCount = correctCount + 1, "
                        + "mistakeDebt = MAX(0, mistakeDebt - 1) WHERE id = 2");
            }
            assertEquals("После двух правильных ответов долг должен закрыться",
                    0, valueOf(2, "mistakeDebt"));

            st.execute("UPDATE questions SET correctCount = correctCount + 1, "
                    + "mistakeDebt = MAX(0, mistakeDebt - 1) WHERE id = 2");
            assertEquals("Долг не должен уходить в минус", 0, valueOf(2, "mistakeDebt"));

            st.execute("UPDATE questions SET wrongCount = wrongCount + 1, "
                    + "mistakeDebt = 2 WHERE id = 2");
        }

        assertEquals(2, valueOf(2, "mistakeDebt"));
        assertEquals(4, valueOf(2, "correctCount"));
        assertEquals(4, valueOf(2, "wrongCount"));
    }

    private void migrate() throws Exception {
        try (Statement st = connection.createStatement()) {
            for (String sql : AppDatabase.MIGRATION_2_3_SQL) {
                st.execute(sql);
            }
        }
    }

    private int valueOf(int id, String column) throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT " + column + " FROM questions WHERE id = " + id)) {
            assertTrue("Вопрос с id=" + id + " не найден", rs.next());
            return rs.getInt(column);
        }
    }
}
