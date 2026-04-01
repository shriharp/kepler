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

            float[] stars = new float[arr.length() * 4];
            int[] hips = new int[arr.length()];
            String[] names = new String[arr.length()];

            for (int i = 0; i < arr.length(); i++) {

                JSONObject obj = arr.getJSONObject(i);

                int hip = obj.getInt("hip");
                hips[i] = hip;

                String name = obj.optString("name", "");
                names[i] = name;

                float ra  = (float) obj.getDouble("ra");
                float dec = (float) obj.getDouble("dec");

                float raDeg = ra * 15f;
                float raRad  = (float) Math.toRadians(raDeg);
                float decRad = (float) Math.toRadians(dec);

                float x = (float)(Math.cos(decRad) * Math.cos(raRad));
                float y = (float)(Math.cos(decRad) * Math.sin(raRad));
                float z = (float)(Math.sin(decRad));

                float mag = (float) obj.getDouble("mag");

                float brightness = 1.5f - (mag / 6.0f);
                brightness = Math.max(0.2f, brightness);

                float radius = 10f;

                stars[i*4]   = x * radius;
                stars[i*4+1] = y * radius;
                stars[i*4+2] = z * radius;
                stars[i*4+3] = brightness;
            }

            return new StarData(stars, hips, names);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new StarData(new float[0], new int[0], new String[0]);
    }
}