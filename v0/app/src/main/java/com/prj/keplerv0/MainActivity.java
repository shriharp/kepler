package com.prj.keplerv0;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private StarGLSurfaceView glSurfaceView;
    private DrawerLayout drawerLayout;
    private TextView starLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ImageButton btnMenu = findViewById(R.id.btn_menu);
        FrameLayout container = findViewById(R.id.container);

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
        container.addView(glSurfaceView);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_join_dots) {
                Intent intent = new Intent(MainActivity.this, JoinDotsActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_battle) {
                // Modified to lead to Multiplayer/Mode selection
                Intent intent = new Intent(MainActivity.this, MultiplayerSetupActivity.class);
                startActivity(intent);
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
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