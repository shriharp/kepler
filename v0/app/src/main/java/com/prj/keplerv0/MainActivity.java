package com.prj.keplerv0;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private StarGLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        glSurfaceView = new StarGLSurfaceView(this);
        setContentView(glSurfaceView);
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