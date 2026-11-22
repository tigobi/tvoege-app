package com.example.tvoege_Java;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.databinding.ActivityUniversalTaskBinding;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Единственный экран тренировки: переключение заданий происходит внутри этой Activity,
 * а состояние сессии хранится в {@link TaskViewModel}.
 */
public class UniversalTaskActivity extends AppCompatActivity {

    private static final String EXTRA_TASK_ID = "EXTRA_TASK_ID";
    private static final int DEFAULT_TASK_ID = 7;
    private static final int[] TASK_IDS = {7, 12, 13, 14};

    private ActivityUniversalTaskBinding binding;
    private TaskViewModel viewModel;
    private GestureDetector gestureDetector;
    private MaterialButton[] answerButtons;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int currentTaskId = DEFAULT_TASK_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUniversalTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        answerButtons = new MaterialButton[]{
                binding.btnAnswer1,
                binding.btnAnswer2,
                binding.btnAnswer3
        };

        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        setupListeners();
        setupGestureDetector();
        observeViewModel();

        viewModel.startTask(getIntent().getIntExtra(EXTRA_TASK_ID, DEFAULT_TASK_ID));
    }

    private void setupListeners() {
        binding.btnRule.setOnClickListener(v -> showRule());
        binding.btnFeedback.setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://forms.yandex.ru/u/69a46131eb61465caad99bec"))));
        binding.tvLogo.setOnClickListener(v ->
                Toast.makeText(this, "Сделал Тихон", Toast.LENGTH_SHORT).show());

        binding.btnTask7.setOnClickListener(v -> navigateTo(7));
        binding.btnTask12.setOnClickListener(v -> navigateTo(12));
        binding.btnTask13.setOnClickListener(v -> navigateTo(13));
        binding.btnTask14.setOnClickListener(v -> navigateTo(14));

        for (MaterialButton button : answerButtons) {
            button.setOnClickListener(v -> handleAnswer(button));
        }
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                                   float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    navigateToTaskByOffset(diffX < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    private void observeViewModel() {
        viewModel.getCurrentTaskId().observe(this, taskId -> {
            if (taskId == null) {
                return;
            }
            currentTaskId = taskId;
            highlightCurrentTask(taskId);
        });

        viewModel.getQuestions().observe(this, questions -> renderCurrentQuestion());
        viewModel.getCurrentIndex().observe(this, index -> renderCurrentQuestion());
        viewModel.getCurrentOptions().observe(this, this::renderOptions);
    }

    private void navigateTo(int taskId) {
        if (currentTaskId == taskId) {
            return;
        }
        mainHandler.removeCallbacksAndMessages(null);
        viewModel.selectTask(taskId);
    }

    private void navigateToTaskByOffset(int offset) {
        int currentPosition = -1;
        for (int i = 0; i < TASK_IDS.length; i++) {
            if (TASK_IDS[i] == currentTaskId) {
                currentPosition = i;
                break;
            }
        }
        int nextPosition = currentPosition + offset;
        if (currentPosition < 0 || nextPosition < 0 || nextPosition >= TASK_IDS.length) {
            return;
        }
        navigateTo(TASK_IDS[nextPosition]);
    }

    private void highlightCurrentTask(int taskId) {
        applyTaskButtonState(binding.btnTask7, taskId == 7);
        applyTaskButtonState(binding.btnTask12, taskId == 12);
        applyTaskButtonState(binding.btnTask13, taskId == 13);
        applyTaskButtonState(binding.btnTask14, taskId == 14);
    }

    private void applyTaskButtonState(MaterialButton button, boolean isCurrent) {
        if (isCurrent) {
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.active_back)));
            button.setTextColor(getColor(R.color.active_text));
            button.setElevation(8f);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.unActive_back)));
            button.setTextColor(getColor(R.color.unActive_text));
            button.setElevation(0f);
        }
    }

    private void handleAnswer(MaterialButton selectedButton) {
        QuestionEntity question = viewModel.getCurrentQuestion();
        if (question == null) {
            return;
        }

        setAnswerButtonsEnabled(false);
        boolean isCorrect = selectedButton.getText().toString().equals(question.correctAnswer);
        viewModel.recordAnswer(isCorrect);

        if (isCorrect) {
            selectedButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green)));
            mainHandler.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    selectedButton.setBackgroundTintList(
                            ColorStateList.valueOf(getColor(R.color.active_back)));
                }
                viewModel.nextQuestion();
            }, 400);
        } else {
            selectedButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.red)));
            selectedButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            mainHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                selectedButton.setBackgroundTintList(
                        ColorStateList.valueOf(getColor(R.color.active_back)));
                setAnswerButtonsEnabled(true);
                showRule();
            }, 400);
        }
    }

    private void setAnswerButtonsEnabled(boolean enabled) {
        for (MaterialButton button : answerButtons) {
            button.setEnabled(enabled);
        }
    }

    private void renderCurrentQuestion() {
        QuestionEntity question = viewModel.getCurrentQuestion();
        if (question == null) {
            Boolean isLoading = viewModel.getLoading().getValue();
            binding.tvQuestion.setText(isLoading != null && isLoading
                    ? "Загрузка..."
                    : "Вопросы не найдены");
            return;
        }

        binding.tvQuestion.setText(question.text);
        binding.tvQuestion.setAlpha(0f);
        binding.tvQuestion.animate().alpha(1f).setDuration(500);
    }

    private void renderOptions(List<String> options) {
        for (int i = 0; i < answerButtons.length; i++) {
            MaterialButton button = answerButtons[i];
            if (options != null && i < options.size()) {
                button.setText(options.get(i));
                button.setBackgroundTintList(
                        ColorStateList.valueOf(getColor(R.color.active_back)));
                button.setVisibility(View.VISIBLE);
                button.setEnabled(true);
            } else {
                button.setVisibility(View.GONE);
            }
        }
    }

    private void showRule() {
        QuestionEntity question = viewModel.getCurrentQuestion();
        if (question == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Правило")
                .setMessage(question.rule)
                .setPositiveButton("Понятно", null)
                .show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }
    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
