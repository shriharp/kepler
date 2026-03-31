package com.prj.keplerv0;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;

public class StarRenderer implements GLSurfaceView.Renderer {

    private Context context;

    public StarRenderer(Context context) {
        this.context = context;
    }
    private static final String VERTEX_SHADER =
            "attribute vec4 aPos;" +
                    "uniform mat4 uMVP;" +
                    "void main(){" +
                    " gl_Position = uMVP * vec4(aPos.xyz,1.0);" +
                    " gl_PointSize = 2.0 + aPos.w * 6.0;" +
                    "}";
    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "void main(){" +
                    " gl_FragColor = vec4(1.0);" +
                    "}";

    private final float[] rotationMatrix = new float[16];

    public void setRotationMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, rotationMatrix, 0, 16);
    }
    private int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile error: " + error);
        }

        return shader;
    }

    private int createProgram(String vertex, String fragment) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vertex);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment);

        int program = GLES20.glCreateProgram();

        GLES20.glAttachShader(program, v);
        GLES20.glAttachShader(program, f);
        GLES20.glLinkProgram(program);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);

        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Program link error: " + error);
        }

        return program;
    }

    private FloatBuffer starBuffer;
    private int program;
    private int starCount = 5000;

//    private float angleX = 0;
//    private float angleY = 0;

    private final float[] mvp = new float[16];
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] temp = new float[16];

//    public void rotate(float dx, float dy) {
//        angleX += dy * 0.5f;
//        angleY += dx * 0.5f;
//    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        GLES20.glClearColor(0, 0, 0, 1);

        float[] stars = StarCatalog.load(context);
        starCount = stars.length / 4;

        starBuffer = ByteBuffer
                .allocateDirect(stars.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        starBuffer.put(stars).position(0);

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);


        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        Matrix.perspectiveM(projection, 0, 60, ratio, 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Matrix.setLookAtM(view, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);
        Matrix.setIdentityM(model, 0);

// 🔹 Remap coordinate system (IMPORTANT)
        float[] remapped = new float[16];
        android.hardware.SensorManager.remapCoordinateSystem(
                rotationMatrix,
                android.hardware.SensorManager.AXIS_X,
                android.hardware.SensorManager.AXIS_Y,
                remapped
        );

// 🔹 Apply rotation
        Matrix.multiplyMM(model, 0, remapped, 0, model, 0);

        Matrix.multiplyMM(temp, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0);

        GLES20.glUseProgram(program);

        int pos = GLES20.glGetAttribLocation(program, "aPos");
        int mvpLoc = GLES20.glGetUniformLocation(program, "uMVP");

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);

        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 4, GLES20.GL_FLOAT, false, 0, starBuffer);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount);
    }
}

