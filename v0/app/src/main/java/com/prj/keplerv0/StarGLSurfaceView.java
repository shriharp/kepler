package com.prj.keplerv0;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class StarGLSurfaceView extends GLSurfaceView {

    private final StarRenderer renderer;
    private float previousX, previousY;

    public StarGLSurfaceView(Context context) {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new StarRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = x - previousX;
            float dy = y - previousY;

            renderer.rotate(dx, dy);
        }

        previousX = x;
        previousY = y;
        return true;
    }
}

