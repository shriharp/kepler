package com.prj.keplerv0.quiz;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizManager {
    private final List<QuizQuestion> allQuestions = new ArrayList<>();
    private final DifficultyManager difficultyManager = new DifficultyManager();
    
    private final List<QuizQuestion> currentEasy = new ArrayList<>();
    private final List<QuizQuestion> currentMedium = new ArrayList<>();
    private final List<QuizQuestion> currentHard = new ArrayList<>();

    private QuizQuestion currentQuestion;
    private long questionStartTime;

    public QuizManager(Context context) {
        loadQuestions(context);
        filterAndShuffle();
    }

    private void loadQuestions(Context context) {
        try {
            InputStream is = context.getAssets().open("quiz_questions.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                
                JSONArray optsArray = obj.getJSONArray("options");
                List<String> options = new ArrayList<>();
                for (int j = 0; j < optsArray.length(); j++) {
                    options.add(optsArray.getString(j));
                }

                QuizQuestion q = new QuizQuestion(
                    obj.getString("id"),
                    obj.getString("question"),
                    options,
                    obj.getInt("correctIndex"),
                    obj.getString("difficulty"),
                    obj.getString("explanation")
                );
                allQuestions.add(q);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void filterAndShuffle() {
        currentEasy.clear();
        currentMedium.clear();
        currentHard.clear();

        for (QuizQuestion q : allQuestions) {
            if ("EASY".equalsIgnoreCase(q.getDifficulty())) currentEasy.add(q);
            else if ("MEDIUM".equalsIgnoreCase(q.getDifficulty())) currentMedium.add(q);
            else if ("HARD".equalsIgnoreCase(q.getDifficulty())) currentHard.add(q);
            else currentMedium.add(q); // fallback
        }

        Collections.shuffle(currentEasy);
        Collections.shuffle(currentMedium);
        Collections.shuffle(currentHard);
    }

    public QuizQuestion getNextQuestion() {
        DifficultyManager.Difficulty diff = difficultyManager.getCurrentDifficulty();
        List<QuizQuestion> pool;

        switch (diff) {
            case EASY: pool = currentEasy; break;
            case HARD: pool = currentHard; break;
            case MEDIUM:
            default: pool = currentMedium; break;
        }

        // If pool is empty, pick from another pool
        if (pool.isEmpty()) {
            if (!currentMedium.isEmpty()) pool = currentMedium;
            else if (!currentEasy.isEmpty()) pool = currentEasy;
            else if (!currentHard.isEmpty()) pool = currentHard;
            else {
                // If completely empty, reshuffle everything
                filterAndShuffle();
                pool = currentMedium; 
            }
        }

        if (pool.isEmpty()) return null; // Safety check

        currentQuestion = pool.remove(0);
        questionStartTime = System.currentTimeMillis();
        return currentQuestion;
    }

    public boolean submitAnswer(int selectedIndex) {
        long timeTakenMs = System.currentTimeMillis() - questionStartTime;
        boolean correct = (selectedIndex == currentQuestion.getCorrectIndex());
        
        difficultyManager.recordAnswer(correct, timeTakenMs);
        return correct;
    }

    public String getCurrentExplanation() {
        if (currentQuestion != null) {
            return currentQuestion.getExplanation();
        }
        return "";
    }

    public DifficultyManager.Difficulty getCurrentDifficulty() {
        return difficultyManager.getCurrentDifficulty();
    }
}
