package com.example.slagalicavpl.model;

public class Question {
    public final String text;
    public final String optionA;
    public final String optionB;
    public final String optionC;
    public final String optionD;
    public final char correct; // 'A', 'B', 'C' or 'D'

    public Question(String text, String a, String b, String c, String d, char correct) {
        this.text    = text;
        this.optionA = a;
        this.optionB = b;
        this.optionC = c;
        this.optionD = d;
        this.correct = correct;
    }
}
