package com.prj.keplerv0;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private StarGLSurfaceView glSurfaceView;
    private TextView starLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // GLSurfaceView for the star field
        glSurfaceView = new StarGLSurfaceView(this);

        // TextView overlay to show the tapped star name (Bug 1 fix)
        starLabel = new TextView(this);
        starLabel.setTextColor(Color.WHITE);
        starLabel.setTextSize(18);
        starLabel.setTypeface(Typeface.DEFAULT_BOLD);
        starLabel.setPadding(32, 32, 32, 32);
        starLabel.setBackgroundColor(0x88000000); // semi-transparent black
        starLabel.setVisibility(android.view.View.INVISIBLE);

        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.gravity = Gravity.TOP | Gravity.START;
        labelParams.topMargin = 48;
        labelParams.leftMargin = 48;

        // Stack GLSurfaceView + TextView in a FrameLayout
        FrameLayout root = new FrameLayout(this);
        root.addView(glSurfaceView);
        root.addView(starLabel, labelParams);
        setContentView(root);

        // Wire up the listener — runs on UI thread, safe to touch Views
        glSurfaceView.setOnStarPickedListener(name -> {
            starLabel.setText(name);
            starLabel.setVisibility(android.view.View.VISIBLE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume(); // important for OpenGL
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause(); // important for OpenGL
    }
}