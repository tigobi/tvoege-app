package com.example.tvoege_Java;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.data.QuestionRepository;
import com.example.tvoege_Java.data.TaskStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Хранит состояние тренировки: текущее задание, список вопросов, индекс текущего вопроса
 * и перемешанные варианты ответа. Живёт дольше Activity, поэтому поворот экрана
 * не сбрасывает прогресс и не перезапрашивает базу данных.
 */
public class TaskViewModel extends AndroidViewModel {

    /** Режим тренировки: все вопросы задания или только вопросы с ошибками. */
    public enum Mode { ALL, MISTAKES }

    /** Сколько правильных ответов подряд нужно, чтобы вопрос ушёл из работы над ошибками. */
    private static final int MISTAKE_DEBT_ON_ERROR = 2;

    private final QuestionRepository repository;

    private final MutableLiveData<Integer> currentTaskId = new MutableLiveData<>();
    private final MutableLiveData<List<QuestionEntity>> questions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> currentOptions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<List<Integer>> availableTaskIds =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> mistakesMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> mistakesCompleted = new MutableLiveData<>(false);
    private final MutableLiveData<TaskStats> currentTaskStats = new MutableLiveData<>();
    private final MutableLiveData<List<TaskStats>> allTasksStats =
            new MutableLiveData<>(Collections.emptyList());

    /** Задание, для которого уже запущена загрузка (защищает от повторного запроса при повороте). */
    private int requestedTaskId = -1;
    private Mode requestedMode = Mode.ALL;
    private int initialTaskId = -1;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new QuestionRepository(application);
        repository.getTaskIds(taskIds -> {
            availableTaskIds.setValue(taskIds == null
                    ? Collections.emptyList()
                    : taskIds);
            ensureSessionStarted();
        });
    }

    public LiveData<Integer> getCurrentTaskId() {
        return currentTaskId;
    }

    public LiveData<List<QuestionEntity>> getQuestions() {
        return questions;
    }

    public LiveData<Integer> getCurrentIndex() {
        return currentIndex;
    }

    public LiveData<List<String>> getCurrentOptions() {
        return currentOptions;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<List<Integer>> getAvailableTaskIds() {
        return availableTaskIds;
    }

    public LiveData<Boolean> getMistakesMode() {
        return mistakesMode;
    }

    /** true, когда в режиме ошибок не осталось ни одного вопроса. */
    public LiveData<Boolean> getMistakesCompleted() {
        return mistakesCompleted;
    }

    /** Статистика по текущему заданию. */
    public LiveData<TaskStats> getCurrentTaskStats() {
        return currentTaskStats;
    }

    /** Статистика по всем заданиям из БД (для экрана сводки). */
    public LiveData<List<TaskStats>> getAllTasksStats() {
        return allTasksStats;
    }

    /**
     * Запускает тренировку с заданием по умолчанию, если сессия ещё не создана.
     * При пересоздании Activity состояние уже есть, поэтому повторной загрузки не будет.
     *
     * @param initialTaskId желаемое задание или -1, чтобы взять первое доступное из БД
     */
    public void startTask(int initialTaskId) {
        this.initialTaskId = initialTaskId;
        ensureSessionStarted();
    }

    private void ensureSessionStarted() {
        if (currentTaskId.getValue() != null) {
            return;
        }
        List<Integer> taskIds = availableTaskIds.getValue();
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        int taskId = taskIds.contains(initialTaskId) ? initialTaskId : taskIds.get(0);
        selectTask(taskId);
    }

    /** Переключает задание внутри текущей сессии и выходит из режима работы над ошибками. */
    public void selectTask(int taskId) {
        startSession(taskId, Mode.ALL);
    }

    /** Включает режим «Работа над ошибками» для текущего задания. */
    public void selectMistakes() {
        Integer taskId = currentTaskId.getValue();
        if (taskId == null) {
            return;
        }
        startSession(taskId, Mode.MISTAKES);
    }

    private void startSession(int taskId, Mode mode) {
        if (requestedTaskId == taskId && requestedMode == mode) {
            return;
        }
        requestedTaskId = taskId;
        requestedMode = mode;
        Integer activeTaskId = currentTaskId.getValue();
        if (activeTaskId == null || activeTaskId != taskId) {
            currentTaskStats.setValue(null);
        }
        currentTaskId.setValue(taskId);
        mistakesMode.setValue(mode == Mode.MISTAKES);
        mistakesCompleted.setValue(false);
        loadQuestions(taskId, mode);
        loadCurrentTaskStats(taskId);
        refreshAllTasksStats();
    }

    public QuestionEntity getCurrentQuestion() {
        List<QuestionEntity> list = questions.getValue();
        Integer index = currentIndex.getValue();
        if (list == null || list.isEmpty() || index == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    public void recordAnswer(boolean isCorrect) {
        QuestionEntity question = getCurrentQuestion();
        if (question == null) {
            return;
        }
        if (Boolean.TRUE.equals(mistakesMode.getValue())) {
            // Держим «долг» в памяти синхронно с БД, чтобы список ошибок
            // обновлялся сразу после ответа, не дожидаясь повторного запроса.
            question.mistakeDebt = isCorrect
                    ? Math.max(0, question.mistakeDebt - 1)
                    : MISTAKE_DEBT_ON_ERROR;
        }
        repository.recordAnswer(question.id, isCorrect);
        loadCurrentTaskStats(question.taskId);
        refreshAllTasksStats();
    }

    /** Перечитывает статистику по текущему заданию (например, при открытии сводки). */
    public void refreshCurrentTaskStats() {
        Integer taskId = currentTaskId.getValue();
        if (taskId != null) {
            loadCurrentTaskStats(taskId);
        }
    }

    /** Перечитывает статистику по всем заданиям. */
    public void refreshAllTasksStats() {
        repository.getAllTasksStats(allTasksStats::setValue);
    }

    /**
     * Сбрасывает весь прогресс ответов и перезагружает текущий список вопросов,
     * чтобы режим «Работа над ошибками» сразу опустел.
     */
    public void resetStatistics() {
        repository.resetAllStats(ignored -> {
            Integer taskId = currentTaskId.getValue();
            if (taskId == null) {
                return;
            }
            loadQuestions(taskId, requestedMode);
            loadCurrentTaskStats(taskId);
            refreshAllTasksStats();
        });
    }

    private void loadCurrentTaskStats(int taskId) {
        repository.getTaskStats(taskId, stats -> {
            Integer current = currentTaskId.getValue();
            if (current != null && current == taskId) {
                currentTaskStats.setValue(stats);
            }
        });
    }

    public void nextQuestion() {
        List<QuestionEntity> list = questions.getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        int index = currentIndex.getValue() == null ? 0 : currentIndex.getValue();
        if (index < 0 || index >= list.size()) {
            index = 0;
        }

        if (Boolean.TRUE.equals(mistakesMode.getValue())
                && list.get(index).mistakeDebt <= 0) {
            removeCurrentMistake(list, index);
            return;
        }

        int next = (index + 1) % list.size();
        currentIndex.setValue(next);
        updateOptions(list, next);
    }

    /**
     * Убирает отработанный вопрос из списка ошибок. Индекс остаётся на месте,
     * потому что следующий вопрос сдвигается на его позицию.
     */
    private void removeCurrentMistake(List<QuestionEntity> list, int index) {
        List<QuestionEntity> updated = new ArrayList<>(list);
        updated.remove(index);

        if (updated.isEmpty()) {
            questions.setValue(updated);
            currentIndex.setValue(0);
            currentOptions.setValue(Collections.emptyList());
            mistakesCompleted.setValue(true);
            return;
        }

        int next = index >= updated.size() ? 0 : index;
        currentIndex.setValue(next);
        questions.setValue(updated);
        updateOptions(updated, next);
    }

    private void loadQuestions(int taskId, Mode mode) {
        loading.setValue(true);
        QuestionRepository.Callback<List<QuestionEntity>> callback = loaded -> {
            if (requestedTaskId != taskId || requestedMode != mode) {
                return;
            }
            List<QuestionEntity> safeList = loaded == null ? Collections.emptyList() : loaded;
            questions.setValue(safeList);
            currentIndex.setValue(0);
            updateOptions(safeList, 0);
            loading.setValue(false);
        };
        if (mode == Mode.MISTAKES) {
            repository.getMistakesForTask(taskId, callback);
        } else {
            repository.getQuestionsForTask(taskId, callback);
        }
    }

    private void updateOptions(List<QuestionEntity> list, int index) {
        if (list.isEmpty() || index < 0 || index >= list.size()) {
            currentOptions.setValue(Collections.emptyList());
            return;
        }
        List<String> options = new ArrayList<>(list.get(index).getAllOptions());
        Collections.shuffle(options);
        currentOptions.setValue(options);
    }
}
