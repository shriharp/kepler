package com.prj.keplerv0;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class StarCatalog {

    public static StarData load(Context context) {
        try {
            InputStream is = context.getAssets().open("hyg_trimmed_named.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);

            int count = arr.length();

            float[]  stars   = new float[count * 4];
            int[]    hips    = new int[count];
            String[] names   = new String[count];
            float[]  raDeg   = new float[count];   // Right Ascension in degrees
            float[]  decDeg  = new float[count];   // Declination in degrees

            for (int i = 0; i < count; i++) {

                JSONObject obj = arr.getJSONObject(i);

                hips[i]  = obj.getInt("hip");
                names[i] = obj.optString("name", "");

                // RA is stored in hours (0–24); convert to degrees for the sky calculator
                float ra  = (float) obj.getDouble("ra");
                float dec = (float) obj.getDouble("dec");
                float raD = ra * 15f;   // hours → degrees

                // Store raw RA/Dec so StarRenderer can compute altitude per star
                raDeg[i]  = raD;
                decDeg[i] = dec;

                // Convert to unit-sphere Cartesian coordinates (radius = 10)
                float raRad  = (float) Math.toRadians(raD);
                float decRad = (float) Math.toRadians(dec);

                float x = (float)(Math.cos(decRad) * Math.cos(raRad));
                float y = (float)(Math.cos(decRad) * Math.sin(raRad));
                float z = (float)(Math.sin(decRad));

                float mag        = (float) obj.getDouble("mag");
                float brightness = Math.max(0.2f, 1.5f - (mag / 6.0f));
                float radius     = 10f;

                stars[i*4]   = x * radius;
                stars[i*4+1] = y * radius;
                stars[i*4+2] = z * radius;
                stars[i*4+3] = brightness;
            }

            return new StarData(stars, hips, names, raDeg, decDeg);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new StarData(new float[0], new int[0], new String[0], new float[0], new float[0]);
    }
}