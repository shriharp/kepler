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

    private SensorManager sensorManager;
    private Sensor rotationSensor;

    public StarGLSurfaceView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        renderer = new StarRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return true; // disable touch for now
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float[] rot = new float[16];
            SensorManager.getRotationMatrixFromVector(rot, event.values);

            renderer.setRotationMatrix(rot);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}

