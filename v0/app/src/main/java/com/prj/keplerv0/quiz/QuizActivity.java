package com.prj.keplerv0.quiz;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.prj.keplerv0.R;

import java.util.List;

public class QuizActivity extends AppCompatActivity {

    private QuizManager quizManager;
    private View cvQuestion;
    private TextView tvQuestion;
    private TextView tvDifficulty;
    private TextView tvEnergy;
    private LinearLayout llOptions;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInteractionLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure action bar is hidden if taking full dark theme approach
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_quiz);

        cvQuestion = findViewById(R.id.cv_question);
        tvQuestion = findViewById(R.id.tv_question);
        tvDifficulty = findViewById(R.id.tv_difficulty);
        tvEnergy = findViewById(R.id.tv_energy);
        llOptions = findViewById(R.id.ll_options);

        findViewById(R.id.btn_close_quiz).setOnClickListener(v -> finish());

        quizManager = new QuizManager(this);
        updateEnergyDisplay();
        loadNextQuestion();
    }

    private void updateEnergyDisplay() {
        int energy = RewardManager.getEnergy(this);
        tvEnergy.setText("Energy: " + energy);
    }

    private void loadNextQuestion() {
        QuizQuestion currentQuestion = quizManager.getNextQuestion();
        
        if (currentQuestion == null) {
            Toast.makeText(this, "No more questions!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isInteractionLocked = false;
        tvQuestion.setText(currentQuestion.getQuestion());
        
        DifficultyManager.Difficulty diff = quizManager.getCurrentDifficulty();
        tvDifficulty.setText(diff.name());
        
        switch (diff) {
            case EASY: tvDifficulty.setTextColor(Color.parseColor("#AEEA00")); break;
            case MEDIUM: tvDifficulty.setTextColor(Color.parseColor("#FFCA28")); break;
            case HARD: tvDifficulty.setTextColor(Color.parseColor("#EF5350")); break;
        }

        llOptions.removeAllViews();
        List<String> options = currentQuestion.getOptions();
        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(this);
            btn.setText(options.get(i));
            btn.setAllCaps(false);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(16f);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 24); // More spacing
            btn.setLayoutParams(params);
            
            // Use custom drawable
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_quiz_btn_default));
            
            final int selectedIndex = i;
            btn.setOnClickListener(v -> onOptionSelected(btn, selectedIndex, currentQuestion.getCorrectIndex()));
            
            llOptions.addView(btn);
        }

        // Animate the Question Card fading and slightly scaling in for a polished look
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                cvQuestion,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f)
        );
        animator.setDuration(400);
        animator.setInterpolator(new OvershootInterpolator(1.2f));
        animator.start();
    }

    private void onOptionSelected(Button selectedBtn, int selectedIndex, int correctIndex) {
        if (isInteractionLocked) return;
        isInteractionLocked = true;

        boolean isCorrect = quizManager.submitAnswer(selectedIndex);

        if (isCorrect) {
            selectedBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_quiz_btn_correct));
            RewardManager.addEnergy(this, 10);
            updateEnergyDisplay();
            
            handler.postDelayed(this::loadNextQuestion, 1000);
        } else {
            selectedBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_quiz_btn_incorrect));
            
            View correctBtn = llOptions.getChildAt(correctIndex);
            if (correctBtn != null) {
                correctBtn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_quiz_btn_correct));
            }

            // Slight delay before showing explanation
            handler.postDelayed(this::showEducationalFeedback, 400);
        }
    }

    private void showEducationalFeedback() {
        String explanation = quizManager.getCurrentExplanation();
        
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Almost!")
                .setMessage(explanation)
                .setCancelable(false)
                .setPositiveButton("Next", (dialog, which) -> loadNextQuestion())
                .show();
    }
}
