package com.example.tvoege_Java.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuestionRepository {
    public interface Callback<T> {
        void onResult(T result);
    }

    private final Context appContext;
    private final QuestionDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public QuestionRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(appContext).questionDao();
    }

    public void getQuestionsForTask(int taskId, Callback<List<QuestionEntity>> callback) {
        executor.execute(() -> {
            ensurePopulated();
            List<QuestionEntity> list = dao.getQuestionsForTask(taskId);
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void recordAnswer(int questionId, boolean isCorrect) {
        executor.execute(() -> {
            if (isCorrect) dao.incrementCorrect(questionId);
            else dao.incrementWrong(questionId);
        });
    }

    private void ensurePopulated() {
        if (dao.getCount() > 0) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(appContext.getAssets().open("questions.json"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONArray array = new JSONArray(sb.toString());
            List<QuestionEntity> items = new ArrayList<>(array.length());

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONArray wrongArr = obj.getJSONArray("wrongAnswers");
                List<String> wrongList = new ArrayList<>(wrongArr.length());
                for (int j = 0; j < wrongArr.length(); j++) {
                    wrongList.add(wrongArr.getString(j));
                }
                items.add(new QuestionEntity(
                        obj.getInt("taskId"),
                        obj.getString("text"),
                        obj.getString("rule"),
                        obj.getString("correctAnswer"),
                        wrongList
                ));
            }
            dao.insertAll(items);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}