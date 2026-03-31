package com.prj.keplerv0;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class JoinDotsActivity extends AppCompatActivity {

    private StarGLSurfaceView glSurfaceView;
    private final String[] ZODIAC = {
        "Aries", "Taurus", "Gemini", "Cancer",
        "Leo", "Virgo", "Libra", "Scorpio",
        "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_dots);

        TextView tvName = findViewById(R.id.tv_constellation_name);
        Button btnBack = findViewById(R.id.btn_back);
        FrameLayout container = findViewById(R.id.join_dots_container);

        // Select a random zodiac constellation for the task
        String selected = ZODIAC[new Random().nextInt(ZODIAC.length)];
        tvName.setText("Find " + selected);

        glSurfaceView = new StarGLSurfaceView(this);
        container.addView(glSurfaceView);

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }
}