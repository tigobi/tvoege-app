package com.example.tvoege_Java.data;

/**
 * SQL-запросы выборки вопросов. Вынесены в константы, чтобы их можно было
 * использовать и в {@link QuestionDao}, и в unit-тестах на реальном SQLite.
 *
 * <p>Приоритет: сначала вопросы с непогашенным «долгом» по ошибке,
 * затем ещё не пройденные, затем остальные. Внутри каждой группы порядок случайный.
 */
public final class QuestionQueries {

    private static final String PRIORITY_ORDER =
            " ORDER BY CASE "
                    + "WHEN mistakeDebt > 0 THEN 0 "
                    + "WHEN correctCount = 0 THEN 1 "
                    + "ELSE 2 END, RANDOM()";

    /** Вопросы одного задания в порядке приоритета. */
    public static final String TASK_QUESTIONS_PRIORITIZED =
            "SELECT * FROM questions WHERE taskId = :taskId" + PRIORITY_ORDER;

    /** Вопросы всех заданий в порядке приоритета. */
    public static final String ALL_QUESTIONS_PRIORITIZED =
            "SELECT * FROM questions" + PRIORITY_ORDER;

    /** Ошибки одного задания: сначала самый большой «долг», затем самые частые ошибки. */
    public static final String TASK_MISTAKES =
            "SELECT * FROM questions WHERE taskId = :taskId AND mistakeDebt > 0 "
                    + "ORDER BY mistakeDebt DESC, RANDOM()";

    /** Ошибки по всем заданиям: сначала самый большой «долг», затем самые частые ошибки. */
    public static final String ALL_MISTAKES =
            "SELECT * FROM questions WHERE mistakeDebt > 0 "
                    + "ORDER BY mistakeDebt DESC, wrongCount DESC, RANDOM()";

    private QuestionQueries() {
    }
}
