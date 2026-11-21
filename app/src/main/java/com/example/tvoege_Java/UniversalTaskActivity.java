package com.example.tvoege_Java;

import android.os.Bundle;

public class UniversalTaskActivity extends TaskXActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int taskIdFromIntent = getIntent().getIntExtra("EXTRA_TASK_ID", 7);
        this.currentTaskId = taskIdFromIntent;

        super.onCreate(savedInstanceState);

        tvQuestion.post(() -> highlightCurrentTask(currentTaskId));

        loadQuestions();
    }

    @Override
    protected void setupSwipeNavigation(float diffX) {
        int nextTask = currentTaskId;

        if (diffX < 0) {
            if (currentTaskId == 7) nextTask = 12;
            else if (currentTaskId == 12) nextTask = 13;
            else if (currentTaskId == 13) nextTask = 14;
        } else {
            if (currentTaskId == 14) nextTask = 13;
            else if (currentTaskId == 13) nextTask = 12;
            else if (currentTaskId == 12) nextTask = 7;
        }

        if (nextTask != currentTaskId) {
            navigateTo(nextTask);
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_universal_task;
    }
}