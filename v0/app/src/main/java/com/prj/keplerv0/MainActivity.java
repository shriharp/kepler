package com.prj.keplerv0;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private StarGLSurfaceView glSurfaceView;
    private DrawerLayout      drawerLayout;
    private NavigationView    navigationView;
    private LocationProvider  locationProvider;

    // HUD references (from XML layout)
    private View     starInfoCard;
    private TextView tvStarName;
    private TextView tvLocation;
    private TextView tvStarCount;
    private TextView tvMode;
    private TextView tvStarSubtitle;
    private TextView tvStarMythology;
    private SkyOverlayView skyOverlayView;

    /** Whether the gyroscope is the active control method. */
    private boolean gyroscopeEnabled = true;

    // ── Periodic sidereal-time refresh (every 60 s) ──────────────────────
    private final Handler  skyHandler   = new Handler(Looper.getMainLooper());
    private final Runnable skyRefresher = new Runnable() {
        @Override public void run() {
            if (glSurfaceView != null
                    && locationProvider != null
                    && locationProvider.hasLocation()) {
                glSurfaceView.queueEvent(() ->
                        glSurfaceView.getRenderer().requestRebuild());
            }
            skyHandler.postDelayed(this, 60_000L);
        }
    };

    // ── Location-permission launcher ─────────────────────────────────────
    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean granted =
                                Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (granted) {
                            startLocationUpdates();
                        } else {
                            tvLocation.setText("No permission");
                            Toast.makeText(this,
                                    "Location permission denied — showing all stars",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Wire layout references
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ImageButton btnMenu = findViewById(R.id.btn_menu);
        FrameLayout container = findViewById(R.id.container);
        starInfoCard = findViewById(R.id.star_info_card);
        tvStarName   = findViewById(R.id.tv_star_name);
        tvStarSubtitle = findViewById(R.id.tv_star_subtitle);
        tvStarMythology= findViewById(R.id.tv_star_mythology);
        tvLocation   = findViewById(R.id.tv_location);
        tvStarCount  = findViewById(R.id.tv_star_count);
        tvMode       = findViewById(R.id.tv_mode);
        skyOverlayView = findViewById(R.id.sky_overlay_view);

        // Create and insert the GL surface at index 0 (bottom of Z stack)
        // so all XML overlay views (HUD, star card, labels) render on top of it.
        glSurfaceView = new StarGLSurfaceView(this);
        glSurfaceView.getRenderer().setOverlayView(skyOverlayView);
        container.addView(glSurfaceView, 0,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        // Hook up the rebuild-complete listener to update the star count HUD
        glSurfaceView.getRenderer().setOnRebuildCompleteListener(count -> {
            tvStarCount.setText(String.format(Locale.US, "%,d stars", count));
        });

        // Star info card: show on tap, dismiss on card tap
        glSurfaceView.setOnStarPickedListener(result -> {
            if (result == null || result.title == null) return;
            tvStarName.setText(result.title);
            
            if (result.subtitle != null && !result.subtitle.isEmpty()) {
                tvStarSubtitle.setText(result.subtitle);
                tvStarSubtitle.setVisibility(View.VISIBLE);
            } else {
                tvStarSubtitle.setVisibility(View.GONE);
            }
            
            if (result.mythology != null && !result.mythology.isEmpty()) {
                tvStarMythology.setText(result.mythology);
                tvStarMythology.setVisibility(View.VISIBLE);
            } else {
                tvStarMythology.setVisibility(View.GONE);
            }
            
            starInfoCard.setVisibility(View.VISIBLE);
        });
        starInfoCard.setOnClickListener(v -> starInfoCard.setVisibility(View.GONE));

        // Menu button opens the drawer
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Navigation item handler
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_join_dots) {
                startActivity(new Intent(this, JoinDotsActivity.class));

            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));

            } else if (id == R.id.nav_battle) {
                startActivity(new Intent(this, MultiplayerSetupActivity.class));

            } else if (id == R.id.nav_quiz) {
                startActivity(new Intent(this, com.prj.keplerv0.quiz.QuizActivity.class));

            } else if (id == R.id.nav_enable_touch) {
                gyroscopeEnabled = !gyroscopeEnabled;
                glSurfaceView.setUseSensors(gyroscopeEnabled);

                // Update menu title and HUD mode label
                item.setTitle(gyroscopeEnabled ? "Enable Touch Mode" : "Enable Gyroscope");
                tvMode.setText(gyroscopeEnabled ? "Gyroscope" : "Touch");

                String msg = gyroscopeEnabled
                        ? "Gyroscope on — point your phone at the sky"
                        : "Touch mode — swipe to explore";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Request location permission → real-time star filtering
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) glSurfaceView.onResume();
        skyHandler.postDelayed(skyRefresher, 60_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
        skyHandler.removeCallbacks(skyRefresher);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void startLocationUpdates() {
        locationProvider = new LocationProvider(this);
        locationProvider.setOnLocationReadyListener((lat, lon) -> {
            // Update the GL renderer on the GL thread
            glSurfaceView.queueEvent(() ->
                    glSurfaceView.getRenderer().setObserverLocation(lat, lon));

            // Update the location HUD on the main thread (listener fires on main thread)
            String locStr = String.format(Locale.US, "%.1f°%s  %.1f°%s",
                    Math.abs(lat), lat >= 0 ? "N" : "S",
                    Math.abs(lon), lon >= 0 ? "E" : "W");
            tvLocation.setText(locStr);
        });
        locationProvider.start();
    }
}