package com.prj.keplerv0;

//wpackage com.prj.keplerv0;

import java.util.Random;

public class    StarField {

    public static float[] generate(int count) {
        float[] stars = new float[count * 3];
        Random r = new Random();

        for (int i = 0; i < count; i++) {
            float theta = (float)(2 * Math.PI * r.nextFloat());
            float phi   = (float)(Math.acos(2*r.nextFloat() - 1));

            float x = (float)(Math.sin(phi) * Math.cos(theta));
            float y = (float)(Math.sin(phi) * Math.sin(theta));
            float z = (float)(Math.cos(phi));

            stars[i*3] = x * 10f;
            stars[i*3+1] = y * 10f;
            stars[i*3+2] = z * 10f;
        }

        return stars;
    }
}

