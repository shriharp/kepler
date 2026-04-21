package com.prj.keplerv0;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class StarGLSurfaceView extends GLSurfaceView implements SensorEventListener {

    private final StarRenderer renderer;

    // Swipe tracking
    private float lastX, lastY;
    private boolean hasMoved;
    private static final float MOVE_THRESHOLD = 8f; // pixels

    // Callback interface so MainActivity can display the label in a TextView overlay
    public interface OnStarPickedListener {
        void onStarPicked(String name);
    }

    private OnStarPickedListener starPickedListener;

    public void setOnStarPickedListener(OnStarPickedListener listener) {
        starPickedListener = listener;
    }
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean useSensors = true;

    public StarGLSurfaceView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        renderer = new StarRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void setUseSensors(boolean use) {
        if (useSensors == use) return;   // no-op if unchanged
        useSensors = use;
        renderer.setUseDeviceOrientation(use);
        if (use) {
            // Re-enable sensor: start receiving rotation events immediately
            if (rotationSensor != null) {
                sensorManager.registerListener(this, rotationSensor,
                        SensorManager.SENSOR_DELAY_GAME);
            }
        } else {
            // Disable sensor: stop receiving events so the view is now swipe-only
            sensorManager.unregisterListener(this);
        }
    }

    public StarRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (useSensors && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (useSensors) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        switch (e.getAction()) {

            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                hasMoved = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastX;
                float dy = e.getY() - lastY;

                if (!hasMoved && Math.abs(dx) < MOVE_THRESHOLD && Math.abs(dy) < MOVE_THRESHOLD) {
                    break; // ignore tiny jitter before marking as a swipe
                }

                hasMoved = true;
                if (!useSensors) {
                    renderer.rotate(dx, dy);
                }
                lastX = e.getX();
                lastY = e.getY();
                break;

            case MotionEvent.ACTION_UP:
                // Only trigger star-pick if the finger didn't swipe
                if (!hasMoved) {
                    final float nx = (e.getX() / getWidth()) * 2f - 1f;
                    final float ny = -((e.getY() / getHeight()) * 2f - 1f);

                    queueEvent(() -> {
                        final String star = renderer.pickStar(nx, ny);
                        post(() -> {
                            if (starPickedListener != null) {
                                starPickedListener.onStarPicked(star != null ? star : "No star nearby");
                            }
                        });
                    });
                }
                break;
        }

        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (useSensors && event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float[] rot = new float[16];
            SensorManager.getRotationMatrixFromVector(rot, event.values);

            renderer.setRotationMatrix(rot);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
