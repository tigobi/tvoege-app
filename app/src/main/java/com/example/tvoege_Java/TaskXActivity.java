package com.example.tvoege_Java;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.data.QuestionRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TaskXActivity extends AppCompatActivity {

    protected TextView tvQuestion;
    protected MaterialButton[] ansButtons;
    protected GestureDetector gestureDetector;
    protected QuestionRepository repository;

    protected List<QuestionEntity> questions = new ArrayList<>();
    protected int currentIndex = 0;
    protected String currentCorrectAnswer;
    protected int currentTaskId;

    protected abstract void setupSwipeNavigation(float diffX);

    protected abstract int getLayoutResId();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
        initViews();
        setupListeners();
        repository = new QuestionRepository(this);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    setupSwipeNavigation(diffX);
                    return true;
                }
                return false;
            }
        });
    }

    protected void loadQuestions() {
        repository.getQuestionsForTask(currentTaskId, loadedQuestions -> {
            if (isFinishing() || isDestroyed()) return;
            this.questions = loadedQuestions;
            this.currentIndex = 0;
            showQuestion();
        });
    }

    protected void initViews() {
        tvQuestion = findViewById(R.id.tvQuestion);
        ansButtons = new MaterialButton[]{
                findViewById(R.id.btnAnswer1),
                findViewById(R.id.btnAnswer2),
                findViewById(R.id.btnAnswer3)
        };
    }

    protected void setupListeners() {
        findViewById(R.id.btnRule).setOnClickListener(v -> showRule());
        findViewById(R.id.btnFeedback).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.yandex.ru/u/69a46131eb61465caad99bec"))));
        findViewById(R.id.tvLogo).setOnClickListener(v -> Toast.makeText(this, "Сделал Тихон", Toast.LENGTH_SHORT).show());

        setupNavigationButton(R.id.btnTask7, 7);
        setupNavigationButton(R.id.btnTask12, 12);
        setupNavigationButton(R.id.btnTask13, 13);
        setupNavigationButton(R.id.btnTask14, 14);

        if (ansButtons != null) {
            for (MaterialButton btn : ansButtons) {
                if (btn != null) btn.setOnClickListener(v -> handleAnswer(btn));
            }
        }
    }

    private void setupNavigationButton(int viewId, int taskId) {
        View btn = findViewById(viewId);
        if (btn != null) {
            btn.setOnClickListener(v -> navigateTo(taskId));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    protected void navigateTo(int taskId) {
        if (currentTaskId == taskId) return;

        Intent intent = new Intent(this, UniversalTaskActivity.class);
        intent.putExtra("EXTRA_TASK_ID", taskId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);

        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }

    protected void highlightCurrentTask(int taskId) {
        int[] btnIds = {R.id.btnTask7, R.id.btnTask12, R.id.btnTask13, R.id.btnTask14};

        for (int id : btnIds) {
            MaterialButton btn = findViewById(id);
            if (btn == null) continue;

            boolean isCurrent = (id == R.id.btnTask7 && taskId == 7)
                    || (id == R.id.btnTask12 && taskId == 12)
                    || (id == R.id.btnTask13 && taskId == 13)
                    || (id == R.id.btnTask14 && taskId == 14);

            if (isCurrent) {
                btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.active_back)));
                btn.setTextColor(getColor(R.color.active_text));
                btn.setElevation(8f);
            } else {
                btn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.unActive_back)));
                btn.setTextColor(getColor(R.color.unActive_text));
                btn.setElevation(0f);
            }
        }
    }

    protected void handleAnswer(MaterialButton selectedBtn) {
        if (questions == null || questions.isEmpty()) return;

        for (MaterialButton btn : ansButtons) {
            if (btn != null) btn.setEnabled(false);
        }

        boolean isCorrect = selectedBtn.getText().toString().equals(currentCorrectAnswer);
        repository.recordAnswer(questions.get(currentIndex).id, isCorrect);

        if (isCorrect) {
            selectedBtn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green)));
            selectedBtn.postDelayed(() -> {
                selectedBtn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.active_back)));
                nextQuestion();
            }, 400);
        } else {
            selectedBtn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.red)));
            selectedBtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            selectedBtn.postDelayed(() -> {
                selectedBtn.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.active_back)));
                for (MaterialButton btn : ansButtons) {
                    if (btn != null) btn.setEnabled(true);
                }
                showRule();
            }, 400);
        }
    }

    protected void showQuestion() {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        QuestionEntity q = questions.get(currentIndex);
        tvQuestion.setText(q.text);
        currentCorrectAnswer = q.correctAnswer;

        List<String> shuffledOptions = q.getAllOptions();
        Collections.shuffle(shuffledOptions);

        for (int i = 0; i < ansButtons.length; i++) {
            if (i < shuffledOptions.size()) {
                ansButtons[i].setText(shuffledOptions.get(i));
                ansButtons[i].setVisibility(View.VISIBLE);
                ansButtons[i].setEnabled(true);
            } else {
                ansButtons[i].setVisibility(View.GONE);
            }
        }
        tvQuestion.setAlpha(0f);
        tvQuestion.animate().alpha(1f).setDuration(500);
    }

    protected void nextQuestion() {
        currentIndex = (currentIndex + 1) % questions.size();
        showQuestion();
    }

    protected void showRule() {
        if (questions == null || questions.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Правило")
                .setMessage(questions.get(currentIndex).rule)
                .setPositiveButton("Понятно", null)
                .show();
    }
}