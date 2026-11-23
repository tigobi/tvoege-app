package com.example.tvoege_Java.data;

import java.util.List;

/**
 * Сводная статистика по заданию. Заполняется Room из агрегирующего SQL-запроса
 * ({@link QuestionDao#getTaskStats(int)} и {@link QuestionDao#getAllTasksStats()}).
 *
 * <p>«Освоенный» вопрос — это вопрос, на который хотя бы раз ответили правильно
 * и у которого нет непогашенного «долга» по ошибке:
 * {@code correctCount > 0 && mistakeDebt == 0}.
 */
public class TaskStats {

    /** Точность не определена: пользователь ещё не давал ни одного ответа. */
    public static final int NO_ACCURACY = -1;

    public int taskId;
    public int totalCount;
    public int masteredCount;
    public int mistakesCount;
    public int totalCorrect;
    public int totalWrong;

    public TaskStats() {
    }

    /** Всего ответов (правильных и неправильных) по заданию. */
    public int getAnsweredCount() {
        return totalCorrect + totalWrong;
    }

    public boolean hasAnswers() {
        return getAnsweredCount() > 0;
    }

    /**
     * Точность в процентах (0..100) или {@link #NO_ACCURACY}, если ответов ещё не было.
     */
    public int getAccuracyPercent() {
        int answered = getAnsweredCount();
        if (answered == 0) {
            return NO_ACCURACY;
        }
        return Math.round(totalCorrect * 100f / answered);
    }

    /** Доля освоенных вопросов в процентах (0..100). */
    public int getMasteredPercent() {
        if (totalCount <= 0) {
            return 0;
        }
        return Math.round(masteredCount * 100f / totalCount);
    }

    /** Правило «освоенности», продублированное в SQL-запросе статистики. */
    public static boolean isMastered(int correctCount, int mistakeDebt) {
        return correctCount > 0 && mistakeDebt == 0;
    }

    /**
     * Складывает статистику нескольких заданий в общую сводку.
     * Поле {@code taskId} у результата не имеет смысла.
     */
    public static TaskStats sum(List<TaskStats> stats) {
        TaskStats total = new TaskStats();
        if (stats == null) {
            return total;
        }
        for (TaskStats item : stats) {
            if (item == null) {
                continue;
            }
            total.totalCount += item.totalCount;
            total.masteredCount += item.masteredCount;
            total.mistakesCount += item.mistakesCount;
            total.totalCorrect += item.totalCorrect;
            total.totalWrong += item.totalWrong;
        }
        return total;
    }
}
