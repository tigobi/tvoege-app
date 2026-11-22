package com.example.tvoege_Java;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.databinding.ActivityUniversalTaskBinding;
import com.example.tvoege_Java.databinding.ItemTaskButtonBinding;
import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Единственный экран тренировки: переключение заданий происходит внутри этой Activity,
 * а состояние сессии хранится в {@link TaskViewModel}. Список доступных заданий
 * берётся из БД, поэтому новые задания появляются без правок кода.
 */
public class UniversalTaskActivity extends AppCompatActivity {

    private static final String EXTRA_TASK_ID = "EXTRA_TASK_ID";
    private static final int NO_TASK_ID = -1;

    private ActivityUniversalTaskBinding binding;
    private TaskViewModel viewModel;
    private GestureDetector gestureDetector;
    private MaterialButton[] answerButtons;
    private final Map<Integer, MaterialButton> taskButtons = new LinkedHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        viewModel.startTask(getIntent().getIntExtra(EXTRA_TASK_ID, NO_TASK_ID));
    }

    private void setupListeners() {
        binding.btnRule.setOnClickListener(v -> showRule());
        binding.btnFeedback.setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://forms.yandex.ru/u/69a46131eb61465caad99bec"))));
        binding.tvLogo.setOnClickListener(v ->
                Toast.makeText(this, "Сделал Тихон", Toast.LENGTH_SHORT).show());
        binding.btnMistakes.setOnClickListener(v -> viewModel.selectMistakes());

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
        viewModel.getAvailableTaskIds().observe(this, this::renderTaskButtons);

        viewModel.getCurrentTaskId().observe(this, this::highlightCurrentTask);

        viewModel.getMistakesMode().observe(this, this::renderMistakesState);

        viewModel.getQuestions().observe(this, questions -> renderCurrentQuestion());
        viewModel.getCurrentIndex().observe(this, index -> renderCurrentQuestion());
        viewModel.getLoading().observe(this, loading -> renderCurrentQuestion());
        viewModel.getCurrentOptions().observe(this, this::renderOptions);
    }

    private void navigateTo(int taskId) {
        Integer current = viewModel.getCurrentTaskId().getValue();
        boolean isMistakes = Boolean.TRUE.equals(viewModel.getMistakesMode().getValue());
        if (current != null && current == taskId && !isMistakes) {
            return;
        }
        mainHandler.removeCallbacksAndMessages(null);
        viewModel.selectTask(taskId);
    }

    private void navigateToTaskByOffset(int offset) {
        List<Integer> taskIds = viewModel.getAvailableTaskIds().getValue();
        Integer current = viewModel.getCurrentTaskId().getValue();
        if (taskIds == null || current == null) {
            return;
        }
        int currentPosition = taskIds.indexOf(current);
        int nextPosition = currentPosition + offset;
        if (currentPosition < 0 || nextPosition < 0 || nextPosition >= taskIds.size()) {
            return;
        }
        navigateTo(taskIds.get(nextPosition));
    }

    private void renderTaskButtons(List<Integer> taskIds) {
        binding.taskButtonsContainer.removeAllViews();
        taskButtons.clear();
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }

        for (int i = 0; i < taskIds.size(); i++) {
            int taskId = taskIds.get(i);
            ItemTaskButtonBinding item = ItemTaskButtonBinding.inflate(
                    getLayoutInflater(), binding.taskButtonsContainer, false);
            MaterialButton button = item.getRoot();
            button.setText(String.valueOf(taskId));

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) button.getLayoutParams();
            params.setMarginEnd(i == taskIds.size() - 1 ? 0 : dp(8));

            button.setOnClickListener(v -> navigateTo(taskId));
            binding.taskButtonsContainer.addView(button);
            taskButtons.put(taskId, button);
        }

        Integer current = viewModel.getCurrentTaskId().getValue();
        if (current != null) {
            highlightCurrentTask(current);
        }
    }

    private void highlightCurrentTask(int taskId) {
        for (Map.Entry<Integer, MaterialButton> entry : taskButtons.entrySet()) {
            applyTaskButtonState(entry.getValue(), entry.getKey() == taskId);
        }
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

    private void renderMistakesState(Boolean isActive) {
        boolean active = Boolean.TRUE.equals(isActive);
        int color = getColor(active ? R.color.active_back : R.color.unActive_text);
        binding.btnMistakes.setTextColor(color);
        binding.btnMistakes.setIconTint(ColorStateList.valueOf(color));
        binding.btnMistakes.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
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
            boolean isLoading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
            boolean isMistakes = Boolean.TRUE.equals(viewModel.getMistakesMode().getValue());
            if (isLoading) {
                binding.tvQuestion.setText("Загрузка...");
            } else if (isMistakes) {
                binding.tvQuestion.setText("Ошибок по этому заданию пока нет");
            } else {
                binding.tvQuestion.setText("Вопросы не найдены");
            }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
