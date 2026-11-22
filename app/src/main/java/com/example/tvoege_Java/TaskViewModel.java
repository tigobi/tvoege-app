package com.example.tvoege_Java;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tvoege_Java.data.QuestionEntity;
import com.example.tvoege_Java.data.QuestionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Хранит состояние тренировки: текущее задание, список вопросов, индекс текущего вопроса
 * и перемешанные варианты ответа. Живёт дольше Activity, поэтому поворот экрана
 * не сбрасывает прогресс и не перезапрашивает базу данных.
 */
public class TaskViewModel extends AndroidViewModel {

    private final QuestionRepository repository;

    private final MutableLiveData<Integer> currentTaskId = new MutableLiveData<>();
    private final MutableLiveData<List<QuestionEntity>> questions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> currentOptions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    /** Задание, для которого уже запущена загрузка (защищает от повторного запроса при повороте). */
    private int requestedTaskId = -1;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new QuestionRepository(application);
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

    /**
     * Запускает тренировку с заданием по умолчанию, если сессия ещё не создана.
     * При пересоздании Activity состояние уже есть, поэтому повторной загрузки не будет.
     */
    public void startTask(int initialTaskId) {
        if (currentTaskId.getValue() == null) {
            selectTask(initialTaskId);
        }
    }

    /** Переключает задание внутри текущей сессии. */
    public void selectTask(int taskId) {
        if (requestedTaskId == taskId) {
            return;
        }
        requestedTaskId = taskId;
        currentTaskId.setValue(taskId);
        loadQuestions(taskId);
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
        repository.recordAnswer(question.id, isCorrect);
    }

    public void nextQuestion() {
        List<QuestionEntity> list = questions.getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer index = currentIndex.getValue();
        int next = ((index == null ? 0 : index) + 1) % list.size();
        currentIndex.setValue(next);
        updateOptions(list, next);
    }

    private void loadQuestions(int taskId) {
        loading.setValue(true);
        repository.getQuestionsForTask(taskId, loaded -> {
            if (requestedTaskId != taskId) {
                return;
            }
            List<QuestionEntity> safeList = loaded == null ? Collections.emptyList() : loaded;
            questions.setValue(safeList);
            currentIndex.setValue(0);
            updateOptions(safeList, 0);
            loading.setValue(false);
        });
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
