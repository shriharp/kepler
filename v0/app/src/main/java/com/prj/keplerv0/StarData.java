package com.prj.keplerv0;

public class StarData {
    public float[] vertices;
    public int[] hipIds;
    public String[] names;

    public StarData(float[] v, int[] h, String[] n) {
        vertices = v;
        hipIds = h;
        names = n;
    }
}