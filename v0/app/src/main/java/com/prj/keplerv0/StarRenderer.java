package com.prj.keplerv0;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;

public class StarRenderer implements GLSurfaceView.Renderer {

    private final Context context;
    private boolean useDeviceOrientation = true;

    // Full catalog (immutable after onSurfaceCreated)
    private float[]              allVertices;
    private float[]              allRaDeg;
    private float[]              allDecDeg;
    private String[]             allNames;
    private int[]                allHips;
    private int                  totalStars;
    private int[][]              constellationLines;
    private HashMap<Integer,Integer> hipToIndex;

    // Visible-only view (rebuilt when location/time changes)
    private float[]  starVertices;
    private String[] starNames;
    private int      starCount;

    // OpenGL state - stars + constellations
    private FloatBuffer starBuffer;
    private FloatBuffer constellationBuffer;
    private int         constellationCount;
    private int         program;

    // OpenGL state - horizon ring
    private FloatBuffer horizBuffer;
    private int         horizCount;
    private int         horizProgram;

    // Cached zenith direction (celestial frame).
    // Set in rebuildVisibleStars(), read in onDrawFrame() - both on the GL thread, no lock needed.
    private float[] zenithDir = null;

    // Observer location (written from main thread, read on GL thread)
    // Callback fired (on the main thread) after each visibility rebuild.
    // MainActivity uses this to update the "VISIBLE" counter in the bottom HUD.
    public interface OnRebuildCompleteListener {
        void onRebuildComplete(int visibleStarCount);
    }
    private OnRebuildCompleteListener rebuildListener;
    public void setOnRebuildCompleteListener(OnRebuildCompleteListener l) { rebuildListener = l; }

    private final Object locationLock = new Object();
    private double observerLat = Double.NaN;
    private double observerLon = Double.NaN;
    private volatile boolean needsRebuild = false;

    // Sensor / swipe rotation
    private final float[] rotationMatrix = new float[16];
    private volatile float angleX = 0f;
    private volatile float angleY = 0f;

    // MVP matrices
    private final float[] mvp        = new float[16];
    private final float[] projection = new float[16];
    private final float[] view       = new float[16];
    private final float[] model      = new float[16];
    private final float[] temp       = new float[16];

    // Shaders - stars + constellations
    private static final String VERTEX_SHADER =
            "attribute vec4 aPos;" +
            "uniform   mat4 uMVP;" +
            "void main(){" +
            "  gl_Position  = uMVP * vec4(aPos.xyz, 1.0);" +
            "  gl_PointSize = 2.0 + aPos.w * 6.0;" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "void main(){ gl_FragColor = vec4(1.0); }";

    // Shaders - horizon ring (colour via uniform)
    private static final String HORIZON_VERTEX_SHADER =
            "attribute vec3 aPos;" +
            "uniform   mat4 uMVP;" +
            "void main(){ gl_Position = uMVP * vec4(aPos, 1.0); }";

    private static final String HORIZON_FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main(){ gl_FragColor = uColor; }";

    // -------------------------------------------------------------------------

    public StarRenderer(Context context) {
        this.context = context;
        Matrix.setIdentityM(rotationMatrix, 0);
    }

    public void setUseDeviceOrientation(boolean use) { this.useDeviceOrientation = use; }
    public void setRotationMatrix(float[] matrix)    { System.arraycopy(matrix, 0, rotationMatrix, 0, 16); }

    public void rotate(float dx, float dy) {
        angleX += dy * 0.3f;
        angleY += dx * 0.3f;
    }

    public void setObserverLocation(double lat, double lon) {
        synchronized (locationLock) { observerLat = lat; observerLon = lon; }
        needsRebuild = true;
    }

    public void requestRebuild() { needsRebuild = true; }

    // -------------------------------------------------------------------------
    // GLSurfaceView.Renderer
    // -------------------------------------------------------------------------

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        StarData data   = StarCatalog.load(context);
        allVertices     = data.vertices;
        allRaDeg        = data.raDeg;
        allDecDeg       = data.decDeg;
        allNames        = data.names;
        allHips         = data.hipIds;
        totalStars      = allVertices.length / 4;

        hipToIndex = new HashMap<>(totalStars * 2);
        for (int i = 0; i < allHips.length; i++) hipToIndex.put(allHips[i], i);

        constellationLines = ConstellationLoader.load(context);

        // Before GPS fix: show the full catalog
        starVertices = allVertices;
        starNames    = allNames;
        starCount    = totalStars;

        uploadStarBuffer(allVertices);
        buildConstellationBuffer(null);

        program      = createProgram(VERTEX_SHADER,         FRAGMENT_SHADER);
        horizProgram = createProgram(HORIZON_VERTEX_SHADER, HORIZON_FRAGMENT_SHADER);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.perspectiveM(projection, 0, 60f, ratio, 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildVisibleStars();
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Build model matrix from sensor + swipe
        Matrix.setLookAtM(view, 0, 0, 0, 0,  0, 0, -1,  0, 1, 0);
        Matrix.setIdentityM(model, 0);

        if (useDeviceOrientation) {
            float[] remapped = new float[16];
            android.hardware.SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    android.hardware.SensorManager.AXIS_X,
                    android.hardware.SensorManager.AXIS_Y,
                    remapped);
            Matrix.multiplyMM(model, 0, remapped, 0, model, 0);
        } else {
            Matrix.multiplyMM(model, 0, rotationMatrix, 0, model, 0);
        }

        float[] swipeX = new float[16], swipeY = new float[16], swipe = new float[16];
        Matrix.setRotateM(swipeX, 0, angleX, 1f, 0f, 0f);
        Matrix.setRotateM(swipeY, 0, angleY, 0f, 1f, 0f);
        Matrix.multiplyMM(swipe, 0, swipeY, 0, swipeX, 0);
        Matrix.multiplyMM(model, 0, swipe, 0, model, 0);

        // Horizon floor clamp: keeps look-direction >= 30 deg above horizon so
        // the bottom edge of the 60-deg vertical FOV never dips below altitude 0.
        // The user only ever sees the starry sky, never empty black space below.
        if (zenithDir != null) applyHorizonFloor(model, zenithDir);

        Matrix.multiplyMM(temp, 0, view,       0, model, 0);
        Matrix.multiplyMM(mvp,  0, projection, 0, temp,  0);

        // Draw stars
        GLES20.glUseProgram(program);
        int pos    = GLES20.glGetAttribLocation(program, "aPos");
        int mvpLoc = GLES20.glGetUniformLocation(program, "uMVP");
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);
        GLES20.glEnableVertexAttribArray(pos);

        GLES20.glVertexAttribPointer(pos, 4, GLES20.GL_FLOAT, false, 0, starBuffer);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount);

        GLES20.glLineWidth(1.5f);
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, constellationBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, constellationCount);

        // Draw horizon ring (subtle blue glow at the very bottom of the view)
        if (horizBuffer != null && horizCount > 0) {
            GLES20.glUseProgram(horizProgram);
            int hPos   = GLES20.glGetAttribLocation(horizProgram, "aPos");
            int hMvp   = GLES20.glGetUniformLocation(horizProgram, "uMVP");
            int hColor = GLES20.glGetUniformLocation(horizProgram, "uColor");
            GLES20.glUniformMatrix4fv(hMvp, 1, false, mvp, 0);
            GLES20.glUniform4f(hColor, 0.45f, 0.72f, 1.0f, 0.55f);
            GLES20.glEnableVertexAttribArray(hPos);
            GLES20.glVertexAttribPointer(hPos, 3, GLES20.GL_FLOAT, false, 0, horizBuffer);
            GLES20.glLineWidth(2.5f);
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, horizCount);
        }
    }

    // -------------------------------------------------------------------------
    // Star picking
    // -------------------------------------------------------------------------

    public String pickStar(float nx, float ny) {
        if (starVertices == null || starNames == null) return null;

        float  best     = 0.15f;
        String selected = null;

        for (int i = 0; i < starCount; i++) {
            float x = starVertices[i * 4];
            float y = starVertices[i * 4 + 1];
            float z = starVertices[i * 4 + 2];

            float[] v = new float[4];
            Matrix.multiplyMV(v, 0, mvp, 0, new float[]{x, y, z, 1f}, 0);
            if (v[3] == 0f) continue;

            float sx = v[0] / v[3], sy = v[1] / v[3];
            float dx = sx - nx,     dy = sy - ny;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < best && !starNames[i].isEmpty()) {
                best     = dist;
                selected = starNames[i];
            }
        }
        return selected;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Filters the full catalog to stars above the local horizon, applies
     * atmospheric extinction near the horizon, and rebuilds the horizon ring.
     * Always called on the GL thread.
     */
    private void rebuildVisibleStars() {
        double lat, lon;
        synchronized (locationLock) { lat = observerLat; lon = observerLon; }
        if (Double.isNaN(lat)) return;   // no GPS fix yet

        long now = System.currentTimeMillis();

        // 1. Determine which stars are above the horizon
        boolean[] visible   = new boolean[totalStars];
        double[]  altitudes = new double[totalStars];
        HashSet<Integer> visHips = new HashSet<>(totalStars);
        int visCount = 0;

        for (int i = 0; i < totalStars; i++) {
            double alt = SkyCalculator.altitude(allRaDeg[i], allDecDeg[i], lat, lon, now);
            altitudes[i] = alt;
            if (alt > -0.5) {   // -0.5 deg accounts for atmospheric refraction
                visible[i] = true;
                visCount++;
                visHips.add(allHips[i]);
            }
        }

        // 2. Build filtered vertex arrays with atmospheric extinction
        float[]  visVerts = new float[visCount * 4];
        String[] visNames = new String[visCount];
        int vi = 0;

        for (int i = 0; i < totalStars; i++) {
            if (!visible[i]) continue;
            double alt = altitudes[i];
            // Full brightness above 15 deg, fades to 25% at the horizon
            float ext = (alt >= 15.0) ? 1.0f : (float) Math.max(0.25, alt / 15.0);

            visVerts[vi * 4]     = allVertices[i * 4];
            visVerts[vi * 4 + 1] = allVertices[i * 4 + 1];
            visVerts[vi * 4 + 2] = allVertices[i * 4 + 2];
            visVerts[vi * 4 + 3] = allVertices[i * 4 + 3] * ext;
            visNames[vi]         = allNames[i];
            vi++;
        }

        starVertices = visVerts;
        starNames    = visNames;
        starCount    = visCount;

        // 3. Push to GPU
        uploadStarBuffer(visVerts);
        buildConstellationBuffer(visHips);

        float[] zenith = SkyCalculator.zenithDirection(lat, lon, now);
        zenithDir = zenith;   // read in onDrawFrame on the same GL thread
        buildHorizonRing(zenith);

        // Notify MainActivity (main thread) of the new visible star count
        if (rebuildListener != null) {
            final int count = starCount;
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> rebuildListener.onRebuildComplete(count));
        }
    }

    // -------------------------------------------------------------------------
    // Horizon floor clamp (Rodrigues rotation)
    // -------------------------------------------------------------------------

    /**
     * sin(30 deg) = 0.5.  With a 60-deg vertical FOV, keeping the LOOK
     * direction at altitude >= 30 deg ensures the bottom screen edge never
     * drops below altitude 0, so only sky is visible.
     */
    private static final float MIN_LOOK_SIN_ALT = 0.5f;

    /**
     * Clamps the view so the user always sees the sky, never empty space below
     * the horizon.  Modifies {@code model} in-place.
     *
     * <p>The camera look direction in world (celestial) space is
     * model^T * (0,0,-1).  In column-major storage this equals
     * (-model[2], -model[6], -model[10]) because row 2 of column c lives at
     * flat index c*4 + 2.</p>
     *
     * <p>When the altitude of that direction falls below MIN_LOOK_SIN_ALT, a
     * minimal Rodrigues rotation R is computed such that R * look = target
     * (the clamped direction).  Applying model = model * R^T leaves the
     * horizontal component unchanged and raises only the altitude.</p>
     */
    private void applyHorizonFloor(float[] model, float[] zenith) {
        // Look direction in world space: -( row 2 of each column )
        // Column-major: row 2 of column c is at index c*4 + 2, i.e. 2, 6, 10
        float lx = -model[2];
        float ly = -model[6];
        float lz = -model[10];

        float sinAlt = lx * zenith[0] + ly * zenith[1] + lz * zenith[2];
        if (sinAlt >= MIN_LOOK_SIN_ALT) return;   // already above minimum

        // Project l onto the horizon plane -> compass direction of the look
        float hx = lx - sinAlt * zenith[0];
        float hy = ly - sinAlt * zenith[1];
        float hz = lz - sinAlt * zenith[2];
        float hLen = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hLen < 1e-6f) return;   // looking straight at nadir
        hx /= hLen; hy /= hLen; hz /= hLen;

        // Target: exactly MIN_LOOK_SIN_ALT above the horizon, same compass dir
        float cosMin = (float) Math.sqrt(Math.max(0f, 1f - MIN_LOOK_SIN_ALT * MIN_LOOK_SIN_ALT));
        float tx = cosMin * hx + MIN_LOOK_SIN_ALT * zenith[0];
        float ty = cosMin * hy + MIN_LOOK_SIN_ALT * zenith[1];
        float tz = cosMin * hz + MIN_LOOK_SIN_ALT * zenith[2];

        // Rodrigues rotation R that takes l -> target
        // axis = l x target (normalised), sinA = |l x target|, cosA = l . target
        float aX = ly * tz - lz * ty;
        float aY = lz * tx - lx * tz;
        float aZ = lx * ty - ly * tx;
        float sinA = (float) Math.sqrt(aX * aX + aY * aY + aZ * aZ);
        float cosA  = lx * tx + ly * ty + lz * tz;
        if (sinA < 1e-6f) return;
        aX /= sinA; aY /= sinA; aZ /= sinA;
        float t1 = 1f - cosA;

        // new_model = model * R^T  =>  new look = R * l = target  (proven in comments above)
        // R^T is Rodrigues with same axis and sine-term sign flipped.
        // Column-major: Rt[ col*4 + row ] = R^T[ row ][ col ]
        float[] Rt = new float[16];
        Rt[0]  = t1*aX*aX + cosA;    Rt[4] = t1*aX*aY + sinA*aZ;  Rt[8]  = t1*aX*aZ - sinA*aY; Rt[12] = 0;
        Rt[1]  = t1*aX*aY - sinA*aZ; Rt[5] = t1*aY*aY + cosA;     Rt[9]  = t1*aY*aZ + sinA*aX; Rt[13] = 0;
        Rt[2]  = t1*aX*aZ + sinA*aY; Rt[6] = t1*aY*aZ - sinA*aX;  Rt[10] = t1*aZ*aZ + cosA;    Rt[14] = 0;
        Rt[3]  = 0;                   Rt[7] = 0;                    Rt[11] = 0;                   Rt[15] = 1;

        float[] corrected = new float[16];
        Matrix.multiplyMM(corrected, 0, model, 0, Rt, 0);
        System.arraycopy(corrected, 0, model, 0, 16);
    }

    // -------------------------------------------------------------------------
    // OpenGL buffer builders
    // -------------------------------------------------------------------------

    /**
     * Builds a horizon ring (GL_LINE_LOOP) at altitude = 0 deg.
     * The ring is the great circle perpendicular to the zenith.
     */
    private void buildHorizonRing(float[] N) {
        float[][] basis = horizonBasis(N);
        float[] T1 = basis[0], T2 = basis[1];

        final int   numPts = 72;
        final float r      = 10f;

        float[] ring = new float[numPts * 3];
        for (int i = 0; i < numPts; i++) {
            double theta = 2.0 * Math.PI * i / numPts;
            float  ct    = (float) Math.cos(theta);
            float  st    = (float) Math.sin(theta);
            ring[i * 3]     = r * (ct * T1[0] + st * T2[0]);
            ring[i * 3 + 1] = r * (ct * T1[1] + st * T2[1]);
            ring[i * 3 + 2] = r * (ct * T1[2] + st * T2[2]);
        }

        horizBuffer = ByteBuffer.allocateDirect(ring.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        horizBuffer.put(ring).position(0);
        horizCount = numPts;
    }

    /** Returns two orthonormal vectors spanning the horizon plane (perpendicular to N). */
    private float[][] horizonBasis(float[] N) {
        float[] T1 = normalize(
                Math.abs(N[0]) < 0.9f
                        ? new float[]{ 0,    N[2], -N[1] }
                        : new float[]{ -N[2], 0,    N[0] });
        float[] T2 = normalize(cross(N, T1));
        return new float[][]{ T1, T2 };
    }

    private void uploadStarBuffer(float[] verts) {
        starBuffer = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        starBuffer.put(verts).position(0);
    }

    private void buildConstellationBuffer(HashSet<Integer> visibleHips) {
        float[] lineVerts = new float[constellationLines.length * 2 * 3];
        int idx = 0;

        for (int[] line : constellationLines) {
            if (visibleHips != null &&
                    (!visibleHips.contains(line[0]) || !visibleHips.contains(line[1]))) {
                continue;
            }
            Integer a = hipToIndex.get(line[0]);
            Integer b = hipToIndex.get(line[1]);
            if (a == null || b == null) continue;

            lineVerts[idx++] = allVertices[a * 4];
            lineVerts[idx++] = allVertices[a * 4 + 1];
            lineVerts[idx++] = allVertices[a * 4 + 2];
            lineVerts[idx++] = allVertices[b * 4];
            lineVerts[idx++] = allVertices[b * 4 + 1];
            lineVerts[idx++] = allVertices[b * 4 + 2];
        }

        constellationBuffer = ByteBuffer.allocateDirect(idx * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        constellationBuffer.put(lineVerts, 0, idx).position(0);
        constellationCount = idx / 3;
    }

    // -------------------------------------------------------------------------
    // Vector math helpers
    // -------------------------------------------------------------------------

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len == 0f) return new float[]{0f, 0f, 1f};
        return new float[]{v[0]/len, v[1]/len, v[2]/len};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        };
    }

    // -------------------------------------------------------------------------
    // Shader helpers
    // -------------------------------------------------------------------------

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
        int v = loadShader(GLES20.GL_VERTEX_SHADER,   vertex);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment);

        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, v);
        GLES20.glAttachShader(prog, f);
        GLES20.glLinkProgram(prog);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(prog);
            GLES20.glDeleteProgram(prog);
            throw new RuntimeException("Program link error: " + error);
        }
        return prog;
    }
}
