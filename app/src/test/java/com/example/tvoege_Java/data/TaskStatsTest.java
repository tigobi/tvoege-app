package com.example.tvoege_Java.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Проверяет расчёт точности, освоенности и общей сводки без Android и SQLite. */
public class TaskStatsTest {

    @Test
    public void accuracyIsUndefinedWithoutAnswers() {
        TaskStats stats = new TaskStats();
        stats.totalCount = 5;

        assertFalse(stats.hasAnswers());
        assertEquals(TaskStats.NO_ACCURACY, stats.getAccuracyPercent());
        assertEquals(0, stats.getAnsweredCount());
    }

    @Test
    public void accuracyCountsCorrectAnswersShare() {
        TaskStats stats = new TaskStats();
        stats.totalCorrect = 1;
        stats.totalWrong = 2;

        assertEquals(3, stats.getAnsweredCount());
        assertEquals(33, stats.getAccuracyPercent());

        stats.totalCorrect = 2;
        assertEquals(50, stats.getAccuracyPercent());

        stats.totalCorrect = 3;
        assertEquals(60, stats.getAccuracyPercent());

        stats.totalWrong = 0;
        assertEquals(100, stats.getAccuracyPercent());
    }

    @Test
    public void masteredRuleRequiresCorrectAnswerAndNoDebt() {
        assertTrue(TaskStats.isMastered(1, 0));
        assertTrue(TaskStats.isMastered(10, 0));
        assertFalse("Без правильных ответов вопрос не освоен", TaskStats.isMastered(0, 0));
        assertFalse("Непогашенный долг не даёт освоенности", TaskStats.isMastered(5, 1));
        assertFalse(TaskStats.isMastered(0, 2));
    }

    @Test
    public void masteredPercentHandlesEmptyTask() {
        TaskStats stats = new TaskStats();
        stats.masteredCount = 0;
        stats.totalCount = 0;

        assertEquals(0, stats.getMasteredPercent());

        stats.totalCount = 4;
        stats.masteredCount = 1;
        assertEquals(25, stats.getMasteredPercent());

        stats.masteredCount = 3;
        assertEquals(75, stats.getMasteredPercent());
    }

    @Test
    public void sumAggregatesAllTasksAndKeepsAccuracy() {
        TaskStats first = new TaskStats();
        first.taskId = 7;
        first.totalCount = 4;
        first.masteredCount = 1;
        first.mistakesCount = 1;
        first.totalCorrect = 4;
        first.totalWrong = 3;

        TaskStats second = new TaskStats();
        second.taskId = 12;
        second.totalCount = 2;
        second.masteredCount = 1;
        second.mistakesCount = 0;
        second.totalCorrect = 2;
        second.totalWrong = 0;

        TaskStats total = TaskStats.sum(Arrays.asList(first, second));

        assertEquals(6, total.totalCount);
        assertEquals(2, total.masteredCount);
        assertEquals(1, total.mistakesCount);
        assertEquals(6, total.totalCorrect);
        assertEquals(3, total.totalWrong);
        assertEquals(9, total.getAnsweredCount());
        assertEquals(67, total.getAccuracyPercent());
        assertEquals(33, total.getMasteredPercent());
    }

    @Test
    public void sumOfEmptyListIsZeroed() {
        TaskStats total = TaskStats.sum(Collections.emptyList());

        assertEquals(0, total.totalCount);
        assertEquals(0, total.masteredCount);
        assertEquals(TaskStats.NO_ACCURACY, total.getAccuracyPercent());
    }
}
