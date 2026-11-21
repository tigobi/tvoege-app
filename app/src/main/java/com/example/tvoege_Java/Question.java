package com.example.tvoege_Java;

public class Question {//ALWAYS set correct answer as first in options
    public final String text;
    public final String[] options;
    public final String rule;

    public Question(String text, String rule, String... options) {
        this.text = text;
        this.rule = rule;
        this.options = options;
    }
}