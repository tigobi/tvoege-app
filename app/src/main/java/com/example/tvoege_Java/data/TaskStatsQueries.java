package com.example.tvoege_Java.data;

/**
 * SQL-запросы статистики. Вынесены в константы, чтобы их можно было
 * использовать и в {@link QuestionDao}, и в unit-тестах на реальном SQLite.
 *
 * <p>«Освоенный» вопрос: {@code correctCount > 0 AND mistakeDebt = 0}
 * (см. {@link TaskStats#isMastered(int, int)}).
 */
public final class TaskStatsQueries {

    private static final String SELECT_STATS =
            "SELECT taskId, "
                    + "COUNT(*) AS totalCount, "
                    + "SUM(CASE WHEN correctCount > 0 AND mistakeDebt = 0 "
                    + "THEN 1 ELSE 0 END) AS masteredCount, "
                    + "SUM(CASE WHEN mistakeDebt > 0 THEN 1 ELSE 0 END) AS mistakesCount, "
                    + "SUM(correctCount) AS totalCorrect, "
                    + "SUM(wrongCount) AS totalWrong "
                    + "FROM questions ";

    public static final String TASK_STATS =
            SELECT_STATS + "WHERE taskId = :taskId GROUP BY taskId";

    public static final String ALL_TASK_STATS =
            SELECT_STATS + "GROUP BY taskId ORDER BY taskId";

    public static final String RESET_ALL_STATS =
            "UPDATE questions SET correctCount = 0, wrongCount = 0, mistakeDebt = 0";

    private TaskStatsQueries() {
    }
}
