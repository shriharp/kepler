package com.prj.keplerv0;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;

public class StarGLSurfaceView extends GLSurfaceView implements SensorEventListener {

    private final StarRenderer renderer;

    // Swipe tracking
    private float lastX, lastY;
    private boolean hasMoved;
    private static final float MOVE_THRESHOLD = 8f; // pixels

    // Callback interface so MainActivity can display the label in a TextView
    // overlay
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
    private long lastLogTime = 0;

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
        if (useSensors == use)
            return; // no-op if unchanged
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

            int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation();
            
            float[] remappedRot = new float[16];
            int axisX = SensorManager.AXIS_X;
            int axisY = SensorManager.AXIS_Y;
            
            // Map physical axes to display coordinates depending on screen rotation
            switch (rotation) {
                case Surface.ROTATION_0:
                    axisX = SensorManager.AXIS_X;
                    axisY = SensorManager.AXIS_Y;
                    break;
                case Surface.ROTATION_90:
                    axisX = SensorManager.AXIS_Y;
                    axisY = SensorManager.AXIS_MINUS_X;
                    break;
                case Surface.ROTATION_180:
                    axisX = SensorManager.AXIS_MINUS_X;
                    axisY = SensorManager.AXIS_MINUS_Y;
                    break;
                case Surface.ROTATION_270:
                    axisX = SensorManager.AXIS_MINUS_Y;
                    axisY = SensorManager.AXIS_X;
                    break;
            }

            SensorManager.remapCoordinateSystem(rot, axisX, axisY, remappedRot);

            // Orientation debug logging
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 66) { // limit logs to ~15 FPS
                lastLogTime = now;
                float[] orRaw = new float[3];
                float[] orMap = new float[3];
                SensorManager.getOrientation(rot, orRaw);
                SensorManager.getOrientation(remappedRot, orMap);
                
                float rx = event.values[0];
                float ry = event.values[1];
                float rz = event.values.length > 2 ? event.values[2] : 0;
                
                Log.d("GYRO_DEBUG", 
                    "rawSensor=[x:" + rx + " y:" + ry + " z:" + rz + "] " +
                    "rawAngles=[yaw:" + Math.toDegrees(orRaw[0]) + 
                    " pitch:" + Math.toDegrees(orRaw[1]) + 
                    " roll:" + Math.toDegrees(orRaw[2]) + "] " +
                    "mappedAngles=[yaw:" + Math.toDegrees(orMap[0]) + 
                    " pitch:" + Math.toDegrees(orMap[1]) + 
                    " roll:" + Math.toDegrees(orMap[2]) + "]");
            }

            renderer.setRotationMatrix(remappedRot);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
