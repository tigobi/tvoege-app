package com.example.tvoege_Java.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "questions", indices = {@Index("taskId")})
public class QuestionEntity {
    /** Стабильный id из questions.json: нужен, чтобы прогресс не терялся при обновлении контента. */
    @PrimaryKey
    public int id;

    public int taskId;
    public String text;
    public String rule;
    public String correctAnswer;
    public List<String> wrongAnswers;

    public int correctCount;
    public int wrongCount;

    public QuestionEntity() {}

    @Ignore
    public QuestionEntity(int id, int taskId, String text, String rule,
                          String correctAnswer, List<String> wrongAnswers) {
        this.id = id;
        this.taskId = taskId;
        this.text = text;
        this.rule = rule;
        this.correctAnswer = correctAnswer;
        this.wrongAnswers = wrongAnswers;
    }

    public List<String> getAllOptions() {
        List<String> all = new ArrayList<>();
        all.add(correctAnswer);
        if (wrongAnswers != null) {
            all.addAll(wrongAnswers);
        }
        return all;
    }
}