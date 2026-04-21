package com.prj.keplerv0;

public class StarData {

    /** Packed x, y, z, brightness — 4 floats per star. */
    public float[]  vertices;

    public int[]    hipIds;
    public String[] names;

    /** Right Ascension in degrees [0, 360) — used for altitude calculations. */
    public float[]  raDeg;

    /** Declination in degrees [−90, +90] — used for altitude calculations. */
    public float[]  decDeg;
    
    /** Magnitude of the star */
    public float[]  mag;

    public StarData(float[] v, int[] h, String[] n, float[] raDeg, float[] decDeg, float[] mag) {
        vertices    = v;
        hipIds      = h;
        names       = n;
        this.raDeg  = raDeg;
        this.decDeg = decDeg;
        this.mag    = mag;
    }
}