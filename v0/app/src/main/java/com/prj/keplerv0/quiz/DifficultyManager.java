package com.prj.keplerv0.quiz;

import android.util.Log;

public class DifficultyManager {
    public enum Difficulty { EASY, MEDIUM, HARD }

    private Difficulty currentDifficulty = Difficulty.EASY;

    private int totalAnswered = 0;
    private int correctAnswers = 0;
    private int currentStreak = 0;
    
    // Average time in ms. Simple running average.
    private long totalTimeMs = 0;

    public void recordAnswer(boolean correct, long timeTakenMs) {
        totalAnswered++;
        totalTimeMs += timeTakenMs;
        
        if (correct) {
            correctAnswers++;
            currentStreak++;
        } else {
            currentStreak = 0;
        }

        recalculateDifficulty();
    }

    private void recalculateDifficulty() {
        if (totalAnswered == 0) return;

        double accuracy = (double) correctAnswers / totalAnswered;
        
        // Speed score: 1.0 (fast, under 3s), down to 0.0 (slow, over 10s)
        long avgTime = totalTimeMs / totalAnswered;
        double speedScore = 1.0 - (Math.max(0, avgTime - 3000) / 7000.0);
        speedScore = Math.max(0.0, Math.min(1.0, speedScore));

        // Streak score: max out at 5
        double streakScore = Math.min(1.0, currentStreak / 5.0);

        // Confidence formula
        double confidence = (accuracy * 0.5) + (speedScore * 0.2) + (streakScore * 0.3);
        
        Log.d("DifficultyManager", "Confidence: " + confidence + " Acc: " + accuracy + " Streak: " + currentStreak);

        if (confidence < 0.4) {
            currentDifficulty = Difficulty.EASY;
        } else if (confidence < 0.75) {
            currentDifficulty = Difficulty.MEDIUM;
        } else {
            currentDifficulty = Difficulty.HARD;
        }
    }

    public Difficulty getCurrentDifficulty() {
        return currentDifficulty;
    }
}
