package com.example.tvoege_Java.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface QuestionDao {
    @Query("SELECT * FROM questions WHERE taskId = :taskId ORDER BY RANDOM()")
    List<QuestionEntity> getQuestionsForTask(int taskId);

    @Query("SELECT * FROM questions WHERE taskId = :taskId AND wrongCount > 0 ORDER BY wrongCount DESC")
    List<QuestionEntity> getMistakesForTask(int taskId);

    @Query("SELECT COUNT(*) FROM questions")
    int getCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<QuestionEntity> questions);

    @Query("UPDATE questions SET correctCount = correctCount + 1 WHERE id = :id")
    void incrementCorrect(int id);

    @Query("UPDATE questions SET wrongCount = wrongCount + 1 WHERE id = :id")
    void incrementWrong(int id);
}