package com.example.tvoege_Java.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface QuestionDao {

    /**
     * Сначала вопросы с ошибками, затем ещё не пройденные, затем остальные.
     * Внутри каждой группы порядок случайный.
     */
    @Query("SELECT * FROM questions WHERE taskId = :taskId "
            + "ORDER BY CASE "
            + "WHEN wrongCount > 0 THEN 0 "
            + "WHEN correctCount = 0 THEN 1 "
            + "ELSE 2 END, RANDOM()")
    List<QuestionEntity> getQuestionsForTask(int taskId);

    @Query("SELECT * FROM questions WHERE taskId = :taskId AND wrongCount > 0 "
            + "ORDER BY wrongCount DESC, RANDOM()")
    List<QuestionEntity> getMistakesForTask(int taskId);

    @Query("SELECT DISTINCT taskId FROM questions ORDER BY taskId")
    List<Integer> getTaskIds();

    @Query("SELECT id, taskId, text, correctCount, wrongCount FROM questions")
    List<QuestionProgress> getProgress();

    @Query("SELECT id FROM questions")
    List<Integer> getAllIds();

    @Query("SELECT COUNT(*) FROM questions")
    int getCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuestionEntity> questions);

    @Query("DELETE FROM questions WHERE id IN (:ids)")
    void deleteByIds(List<Integer> ids);

    @Query("UPDATE questions SET correctCount = correctCount + 1, wrongCount = MAX(0, wrongCount - 1) WHERE id = :id")
    void incrementCorrect(int id);

    @Query("UPDATE questions SET wrongCount = wrongCount + 1 WHERE id = :id")
    void incrementWrong(int id);
}
