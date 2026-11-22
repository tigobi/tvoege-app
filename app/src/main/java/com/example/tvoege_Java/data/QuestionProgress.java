package com.example.tvoege_Java.data;

/**
 * Проекция прогресса ответов. Используется при обновлении контента, чтобы
 * перенести correctCount/wrongCount на новые записи.
 */
public class QuestionProgress {
    public int id;
    public int taskId;
    public String text;
    public int correctCount;
    public int wrongCount;
}
