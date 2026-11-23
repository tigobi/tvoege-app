package com.example.tvoege_Java.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuestionRepository {
    private static final String TAG = "QuestionRepository";

    private static final String PREFS_NAME = "content_prefs";
    private static final String KEY_CONTENT_VERSION = "content_version";

    /**
     * Версия контента. Увеличивайте её при любом изменении questions.json —
     * тогда при запуске новые вопросы добавятся/обновятся в существующей БД,
     * а correctCount, wrongCount и mistakeDebt сохранятся.
     */
    private static final int CONTENT_VERSION = 2;

    /** SQLite ограничивает число параметров в одном запросе, поэтому удаляем пачками. */
    private static final int DELETE_BATCH_SIZE = 500;

    public interface Callback<T> {
        void onResult(T result);
    }

    private final Context appContext;
    private final AppDatabase database;
    private final QuestionDao dao;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public QuestionRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = AppDatabase.getInstance(appContext);
        this.dao = database.questionDao();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void getQuestionsForTask(int taskId, Callback<List<QuestionEntity>> callback) {
        executor.execute(() -> {
            ensureContentUpToDate();
            List<QuestionEntity> list = dao.getQuestionsForTask(taskId);
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void getMistakesForTask(int taskId, Callback<List<QuestionEntity>> callback) {
        executor.execute(() -> {
            ensureContentUpToDate();
            List<QuestionEntity> list = dao.getMistakesForTask(taskId);
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void getTaskIds(Callback<List<Integer>> callback) {
        executor.execute(() -> {
            ensureContentUpToDate();
            List<Integer> taskIds = dao.getTaskIds();
            mainHandler.post(() -> callback.onResult(taskIds));
        });
    }

    public void recordAnswer(int questionId, boolean isCorrect) {
        executor.execute(() -> {
            if (isCorrect) dao.incrementCorrect(questionId);
            else dao.incrementWrong(questionId);
        });
    }

    /** Статистика по одному заданию. Для пустого задания вернёт нулевую сводку. */
    public void getTaskStats(int taskId, Callback<TaskStats> callback) {
        executor.execute(() -> {
            ensureContentUpToDate();
            TaskStats stats = dao.getTaskStats(taskId);
            if (stats == null) {
                stats = new TaskStats();
                stats.taskId = taskId;
            }
            TaskStats result = stats;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** Статистика по всем заданиям в порядке возрастания taskId. */
    public void getAllTasksStats(Callback<List<TaskStats>> callback) {
        executor.execute(() -> {
            ensureContentUpToDate();
            List<TaskStats> stats = dao.getAllTasksStats();
            mainHandler.post(() -> callback.onResult(
                    stats == null ? Collections.emptyList() : stats));
        });
    }

    /** Сбрасывает весь прогресс ответов и по завершении сообщает на главном потоке. */
    public void resetAllStats(Callback<Void> callback) {
        executor.execute(() -> {
            dao.resetAllStats();
            mainHandler.post(() -> callback.onResult(null));
        });
    }

    /**
     * Синхронизирует содержимое БД с questions.json, если версия контента изменилась.
     * Прогресс переносится по стабильному id, а для баз, созданных до появления
     * стабильных id (версия 1), — по паре taskId + text.
     */
    private void ensureContentUpToDate() {
        if (prefs.getInt(KEY_CONTENT_VERSION, 0) == CONTENT_VERSION && dao.getCount() > 0) {
            return;
        }

        List<QuestionEntity> items = readQuestions();
        if (items.isEmpty()) {
            Log.w(TAG, "questions.json пуст или не прочитан — контент оставлен без изменений");
            return;
        }

        Map<Integer, QuestionProgress> progressById = new HashMap<>();
        Map<String, QuestionProgress> progressByText = new HashMap<>();
        for (QuestionProgress progress : dao.getProgress()) {
            progressById.put(progress.id, progress);
            progressByText.put(progressKey(progress.taskId, progress.text), progress);
        }

        Set<Integer> newIds = new HashSet<>();
        for (QuestionEntity item : items) {
            newIds.add(item.id);
            QuestionProgress progress = progressById.get(item.id);
            if (progress == null) {
                progress = progressByText.get(progressKey(item.taskId, item.text));
            }
            if (progress != null) {
                item.correctCount = progress.correctCount;
                item.wrongCount = progress.wrongCount;
                item.mistakeDebt = progress.mistakeDebt;
            }
        }

        List<Integer> staleIds = new ArrayList<>();
        for (Integer existingId : dao.getAllIds()) {
            if (!newIds.contains(existingId)) {
                staleIds.add(existingId);
            }
        }

        database.runInTransaction(() -> {
            dao.insertAll(items);
            for (int start = 0; start < staleIds.size(); start += DELETE_BATCH_SIZE) {
                int end = Math.min(start + DELETE_BATCH_SIZE, staleIds.size());
                dao.deleteByIds(staleIds.subList(start, end));
            }
        });

        prefs.edit().putInt(KEY_CONTENT_VERSION, CONTENT_VERSION).apply();
    }

    private List<QuestionEntity> readQuestions() {
        List<QuestionEntity> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(appContext.getAssets().open("questions.json"),
                        StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONArray array = new JSONArray(sb.toString());
            items = new ArrayList<>(array.length());

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONArray wrongArr = obj.getJSONArray("wrongAnswers");
                List<String> wrongList = new ArrayList<>(wrongArr.length());
                for (int j = 0; j < wrongArr.length(); j++) {
                    wrongList.add(wrongArr.getString(j));
                }
                items.add(new QuestionEntity(
                        obj.getInt("id"),
                        obj.getInt("taskId"),
                        obj.getString("text"),
                        obj.getString("rule"),
                        obj.getString("correctAnswer"),
                        wrongList
                ));
            }
            return items;
        } catch (Exception e) {
            Log.e(TAG, "Не удалось загрузить вопросы из questions.json", e);
            return Collections.emptyList();
        }
    }

    private static String progressKey(int taskId, String text) {
        return taskId + "|" + text;
    }
}
