package com.example.tvoege_Java.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface QuestionDao {

    /**
     * Сначала вопросы с непогашенным «долгом» по ошибке, затем ещё не пройденные,
     * затем остальные. Внутри каждой группы порядок случайный.
     */
    @Query("SELECT * FROM questions WHERE taskId = :taskId "
            + "ORDER BY CASE "
            + "WHEN mistakeDebt > 0 THEN 0 "
            + "WHEN correctCount = 0 THEN 1 "
            + "ELSE 2 END, RANDOM()")
    List<QuestionEntity> getQuestionsForTask(int taskId);

    @Query("SELECT * FROM questions WHERE taskId = :taskId AND mistakeDebt > 0 "
            + "ORDER BY mistakeDebt DESC, RANDOM()")
    List<QuestionEntity> getMistakesForTask(int taskId);

    @Query("SELECT DISTINCT taskId FROM questions ORDER BY taskId")
    List<Integer> getTaskIds();

    @Query("SELECT id, taskId, text, correctCount, wrongCount, mistakeDebt FROM questions")
    List<QuestionProgress> getProgress();

    @Query("SELECT id FROM questions")
    List<Integer> getAllIds();

    @Query("SELECT COUNT(*) FROM questions")
    int getCount();

    /** Сводная статистика по одному заданию; {@code null}, если вопросов нет. */
    @Query(TaskStatsQueries.TASK_STATS)
    TaskStats getTaskStats(int taskId);

    /** Статистика по всем заданиям в порядке возрастания taskId. */
    @Query(TaskStatsQueries.ALL_TASK_STATS)
    List<TaskStats> getAllTasksStats();

    /** Сбрасывает весь накопленный прогресс, не удаляя сами вопросы. */
    @Query(TaskStatsQueries.RESET_ALL_STATS)
    void resetAllStats();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuestionEntity> questions);

    @Query("DELETE FROM questions WHERE id IN (:ids)")
    void deleteByIds(List<Integer> ids);

    @Query("UPDATE questions SET correctCount = correctCount + 1, "
            + "mistakeDebt = MAX(0, mistakeDebt - 1) WHERE id = :id")
    void incrementCorrect(int id);

    @Query("UPDATE questions SET wrongCount = wrongCount + 1, mistakeDebt = 2 WHERE id = :id")
    void incrementWrong(int id);
}
