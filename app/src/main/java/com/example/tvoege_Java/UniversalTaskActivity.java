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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.data.TaskStats;
import com.example.tvoege_Java.databinding.ActivityUniversalTaskBinding;
import com.example.tvoege_Java.databinding.DialogStatisticsBinding;
import com.example.tvoege_Java.databinding.ItemTaskButtonBinding;
import com.example.tvoege_Java.databinding.ItemTaskStatsBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;
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

    private BottomSheetDialog statsSheet;
    private DialogStatisticsBinding statsSheetBinding;
    private TaskStats currentStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
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
        binding.btnStats.setOnClickListener(v -> showStatistics());
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

        viewModel.getMistakesCompleted().observe(this, completed -> renderCurrentQuestion());

        viewModel.getQuestions().observe(this, questions -> renderCurrentQuestion());
        viewModel.getCurrentIndex().observe(this, index -> renderCurrentQuestion());
        viewModel.getLoading().observe(this, loading -> renderCurrentQuestion());
        viewModel.getCurrentOptions().observe(this, this::renderOptions);

        viewModel.getCurrentTaskStats().observe(this, this::renderCurrentTaskStats);
        viewModel.getAllTasksStats().observe(this, stats -> {
            if (statsSheetBinding != null) {
                renderStatistics(stats);
            }
        });
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
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMarginEnd(i == taskIds.size() - 1 ? 0 : dp(8));
            if (params instanceof LinearLayout.LayoutParams) {
                // В горизонтальном скролле кнопки должны сохранять естественную ширину,
                // иначе при большом числе заданий они сжимаются под ширину экрана.
                ((LinearLayout.LayoutParams) params).weight = 0f;
            }
            button.setLayoutParams(params);

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
        scrollToCurrentTask(taskId);
    }

    /** Плавно подкручивает панель заданий так, чтобы активная кнопка была видна. */
    private void scrollToCurrentTask(int taskId) {
        MaterialButton button = taskButtons.get(taskId);
        if (button == null) {
            return;
        }
        binding.taskButtonsScroll.post(() -> {
            int viewportWidth = binding.taskButtonsScroll.getWidth();
            int contentWidth = binding.taskButtonsContainer.getWidth();
            int target = button.getLeft() - (viewportWidth - button.getWidth()) / 2;
            int maxScroll = Math.max(0, contentWidth - viewportWidth);
            binding.taskButtonsScroll.smoothScrollTo(
                    Math.max(0, Math.min(target, maxScroll)), 0);
        });
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
        int foreground = getColor(active ? R.color.active_text : R.color.unActive_text);
        binding.btnMistakes.setBackgroundTintList(ColorStateList.valueOf(getColor(
                active ? R.color.active_back : R.color.unActive_back)));
        binding.btnMistakes.setTextColor(foreground);
        binding.btnMistakes.setIconTint(ColorStateList.valueOf(foreground));
        binding.btnMistakes.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        renderMistakesLabel();
    }

    /** Показывает количество активных ошибок прямо на компактной кнопке режима отработки. */
    private void renderMistakesLabel() {
        int mistakes = currentStats == null ? 0 : currentStats.mistakesCount;
        binding.btnMistakes.setText(mistakes > 0 ? String.valueOf(mistakes) : "");
        if (mistakes > 0) {
            binding.btnMistakes.setIconPadding(dp(4));
            binding.btnMistakes.setPadding(dp(8), 0, dp(10), 0);
        } else {
            // Без текста иконка должна стоять строго по центру квадратной кнопки.
            binding.btnMistakes.setIconPadding(0);
            binding.btnMistakes.setPadding(dp(10), 0, dp(10), 0);
        }
        binding.btnMistakes.setContentDescription(mistakes > 0
                ? "Работа над ошибками: активных ошибок " + mistakes
                : "Работа над ошибками: активных ошибок нет");
    }

    private void renderCurrentTaskStats(TaskStats stats) {
        currentStats = stats;
        renderMistakesLabel();

        if (stats == null || stats.totalCount <= 0) {
            binding.tvMasteredCounter.setVisibility(View.GONE);
            return;
        }

        binding.tvMasteredCounter.setText(stats.masteredCount + " / " + stats.totalCount);
        binding.tvMasteredCounter.setVisibility(View.VISIBLE);
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
        binding.btnRule.setVisibility(question == null ? View.GONE : View.VISIBLE);
        if (question == null) {
            boolean isLoading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
            boolean isMistakes = Boolean.TRUE.equals(viewModel.getMistakesMode().getValue());
            if (isLoading) {
                binding.tvQuestion.setText("Загрузка...");
            } else if (isMistakes) {
                boolean completed = Boolean.TRUE.equals(
                        viewModel.getMistakesCompleted().getValue());
                binding.tvQuestion.setText(completed
                        ? "Все ошибки по этому заданию отработаны"
                        : "Ошибок по этому заданию пока нет");
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

    private void showStatistics() {
        if (statsSheet != null && statsSheet.isShowing()) {
            return;
        }
        viewModel.refreshCurrentTaskStats();
        viewModel.refreshAllTasksStats();

        statsSheetBinding = DialogStatisticsBinding.inflate(getLayoutInflater());
        statsSheet = new BottomSheetDialog(this);
        statsSheet.setContentView(statsSheetBinding.getRoot());
        statsSheetBinding.btnResetStats.setOnClickListener(v -> confirmResetStatistics());
        statsSheet.setOnDismissListener(dialog -> {
            statsSheet = null;
            statsSheetBinding = null;
        });

        renderStatistics(viewModel.getAllTasksStats().getValue());
        statsSheet.show();
    }

    private void renderStatistics(List<TaskStats> stats) {
        if (statsSheetBinding == null) {
            return;
        }
        List<TaskStats> safeStats = stats == null ? Collections.emptyList() : stats;
        TaskStats total = TaskStats.sum(safeStats);

        StringBuilder summary = new StringBuilder();
        summary.append("Освоено: ").append(total.masteredCount)
                .append(" / ").append(total.totalCount);
        if (total.hasAnswers()) {
            summary.append(" • Точность: ").append(total.getAccuracyPercent()).append("%");
        }
        summary.append('\n').append("Активных ошибок: ").append(total.mistakesCount);
        if (total.hasAnswers()) {
            summary.append(" • Ответов: ").append(total.getAnsweredCount());
        }
        statsSheetBinding.tvStatsOverall.setText(summary);

        LinearLayout container = statsSheetBinding.statsContainer;
        container.removeAllViews();

        if (safeStats.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Статистики пока нет");
            empty.setTextColor(getColor(R.color.unActive_text));
            empty.setTextSize(14f);
            container.addView(empty);
            return;
        }

        for (TaskStats item : safeStats) {
            ItemTaskStatsBinding row = ItemTaskStatsBinding.inflate(
                    getLayoutInflater(), container, false);
            row.tvStatsTaskTitle.setText("Задание " + item.taskId);
            row.tvStatsMastered.setText("Освоено: " + item.masteredCount + " / " + item.totalCount);
            row.pbStatsMastered.setProgress(item.getMasteredPercent());
            row.tvStatsAccuracy.setText(item.hasAnswers()
                    ? "Точность: " + item.getAccuracyPercent() + "%"
                    : "Точность: —");
            row.tvStatsMistakes.setText("Активных ошибок: " + item.mistakesCount);
            container.addView(row.getRoot());
        }
    }

    private void confirmResetStatistics() {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить статистику?")
                .setMessage("Будут обнулены освоенные вопросы, точность и активные ошибки "
                        + "по всем заданиям. Отменить это действие нельзя.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    viewModel.resetStatistics();
                    Toast.makeText(this, "Статистика сброшена", Toast.LENGTH_SHORT).show();
                })
                .show();
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
