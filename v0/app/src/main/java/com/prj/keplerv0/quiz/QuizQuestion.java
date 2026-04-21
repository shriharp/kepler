package com.prj.keplerv0.quiz;

import java.util.List;

public class QuizQuestion {
    private String id;
    private String question;
    private List<String> options;
    private int correctIndex;
    private String difficulty; // "EASY", "MEDIUM", "HARD"
    private String explanation;

    public QuizQuestion(String id, String question, List<String> options, int correctIndex, String difficulty, String explanation) {
        this.id = id;
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
        this.difficulty = difficulty;
        this.explanation = explanation;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return options; }
    public int getCorrectIndex() { return correctIndex; }
    public String getDifficulty() { return difficulty; }
    public String getExplanation() { return explanation; }
}
