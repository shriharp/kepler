package com.prj.keplerv0;

import android.content.Context;
import org.json.JSONArray;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConstellationLoader {

    public static int[][] load(Context context) {
        List<int[]> lineList = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open("constellations.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                
                // Expecting [starId1, starId2]
                // Skip entries that don't have at least 2 elements or have non-integers (like ["thin", id])
                if (pair.length() >= 2) {
                    try {
                        int id1 = pair.getInt(0);
                        int id2 = pair.getInt(1);
                        lineList.add(new int[]{id1, id2});
                    } catch (Exception e) {
                        // Skip this entry if it can't be parsed as two integers
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return lineList.toArray(new int[0][]);
    }
}
