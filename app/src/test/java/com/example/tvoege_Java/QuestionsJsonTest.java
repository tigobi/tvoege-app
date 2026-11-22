package com.example.tvoege_Java;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Проверяет целостность контента questions.json до импорта в Room.
 */
public class QuestionsJsonTest {

    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private static JSONArray questions;

    @BeforeClass
    public static void loadQuestions() throws Exception {
        File file = new File("src/main/assets/questions.json");
        if (!file.exists()) {
            file = new File("app/src/main/assets/questions.json");
        }
        assertTrue("Не найден questions.json: " + file.getAbsolutePath(), file.exists());

        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        questions = new JSONArray(json);
    }

    @Test
    public void jsonIsValidAndNotEmpty() {
        assertNotNull(questions);
        assertTrue("questions.json не должен быть пустым", questions.length() > 0);
    }

    @Test
    public void everyQuestionHasRequiredFields() throws Exception {
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            assertFalse("Пустой text в вопросе #" + i, q.getString("text").trim().isEmpty());
            assertFalse("Пустой rule в вопросе #" + i, q.getString("rule").trim().isEmpty());
            assertFalse("Пустой correctAnswer в вопросе #" + i,
                    q.getString("correctAnswer").trim().isEmpty());
        }
    }

    @Test
    public void everyQuestionHasValidOptions() throws Exception {
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            String correct = q.getString("correctAnswer");
            JSONArray wrong = q.getJSONArray("wrongAnswers");

            assertTrue("Вопрос #" + i + " должен иметь хотя бы один неверный ответ",
                    wrong.length() >= 1);
            assertTrue("Вопрос #" + i + " должен иметь не больше двух неверных ответов",
                    wrong.length() <= 2);
            assertTrue("Вопрос #" + i + " должен содержать не больше 3 вариантов",
                    wrong.length() + 1 <= 3);

            Set<String> options = new HashSet<>();
            options.add(correct);
            for (int j = 0; j < wrong.length(); j++) {
                assertTrue("Дубликат варианта ответа в вопросе #" + i + ": " + wrong.getString(j),
                        options.add(wrong.getString(j)));
            }
            assertEquals("Всего вариантов в вопросе #" + i, wrong.length() + 1, options.size());
        }
    }

    @Test
    public void everyQuestionHasStableUniqueId() throws Exception {
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            int id = q.getInt("id");
            assertTrue("id должен быть положительным в вопросе #" + i + ": " + id, id > 0);
            assertTrue("Дубликат id в вопросе #" + i + ": " + id, ids.add(id));
        }
    }

    @Test
    public void thereAreNoDuplicateQuestions() throws Exception {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            String key = q.getInt("taskId") + "|" + q.getString("text");
            assertTrue("Дубликат вопроса taskId+text: " + key, seen.add(key));
        }
    }

    @Test
    public void answersDoNotContainLatinCharacters() throws Exception {
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.getJSONObject(i);
            String correct = q.getString("correctAnswer");
            assertFalse("Латинские символы в correctAnswer вопроса #" + i + ": " + correct,
                    LATIN.matcher(correct).find());

            JSONArray wrong = q.getJSONArray("wrongAnswers");
            for (int j = 0; j < wrong.length(); j++) {
                String option = wrong.getString(j);
                assertFalse("Латинские символы в wrongAnswers вопроса #" + i + ": " + option,
                        LATIN.matcher(option).find());
            }
        }
    }
}
