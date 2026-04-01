package com.prj.keplerv0;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class JoinDotsActivity extends AppCompatActivity implements JoinDotsView.GameListener {

    private JoinDotsView joinDotsView;
    private TextView tvName;
    private String currentConstellationName;
    
    private static class ZodiacConstellation {
        String name;
        int[] memberStars;

         ZodiacConstellation(String name, int[] memberStars) {
            this.name = name;
            this.memberStars = memberStars;
        }
    }

    private final ZodiacConstellation[] ZODIAC = {
        new ZodiacConstellation("Aries", new int[]{9884, 8903, 8832}),
        new ZodiacConstellation("Taurus", new int[]{21421, 20889, 20894, 20205, 20455, 18724, 15900, 20648, 17847}),
        new ZodiacConstellation("Gemini", new int[]{37826, 36850, 32246, 30883, 30343, 29655, 28734, 31681, 34088, 35550, 35350, 32362, 36962, 37740}),
        new ZodiacConstellation("Cancer", new int[]{43103, 42806, 40843, 42911, 40526, 44066}),
        new ZodiacConstellation("Leo", new int[]{49669, 50583, 54872, 57632, 54879, 49583, 50335, 48455, 47908}),
        new ZodiacConstellation("Virgo", new int[]{65474, 66249, 68520, 72220, 63090, 63608, 61941, 57380, 60030}),
        new ZodiacConstellation("Libra", new int[]{72622, 73714, 76333, 74785}),
        new ZodiacConstellation("Scorpius", new int[]{80763, 78401, 78265, 78820, 77516, 77622, 77070, 76276, 77233, 78072, 77450}),
        new ZodiacConstellation("Sagittarius", new int[]{89931, 90496, 89642, 90185, 88635, 87072, 93506, 92041, 89341, 93864, 92855, 93085, 93683, 94820, 95168, 96406, 98688, 98412, 98032, 95347, 95294}),
        new ZodiacConstellation("Capricornus", new int[]{100064, 100345, 104139, 105515, 106985, 107556, 105881, 102485, 102978}),
        new ZodiacConstellation("Aquarius", new int[]{109074, 110395, 110960, 111497, 112961, 114855, 115438, 110003, 109139, 111123, 112716, 113136, 114341, 106278}),
        new ZodiacConstellation("Pisces", new int[]{113881, 112158, 109352, 112748, 112440, 109176, 107354, 113963, 112447, 112029, 109427, 107315, 677, 1067})
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_dots);

        tvName = findViewById(R.id.tv_constellation_name);
        joinDotsView = findViewById(R.id.join_dots_view);
        joinDotsView.setListener(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_next).setOnClickListener(v -> startNewGame());

        // Use post to ensure view dimensions are available
        joinDotsView.post(this::startNewGame);
    }

    private void startNewGame() {
        ZodiacConstellation selected = ZODIAC[new Random().nextInt(ZODIAC.length)];
        currentConstellationName = selected.name;
        tvName.setText(selected.name);
        loadConstellationData(selected);
    }

    private void loadConstellationData(ZodiacConstellation zodiac) {
        try {
            String starsJson = loadAsset("hyg_trimmed_bright_stars.json");
            JSONArray starsArray = new JSONArray(starsJson);
            
            Set<Integer> memberSet = new HashSet<>();
            for(int id : zodiac.memberStars) memberSet.add(id);

            List<RawStar> rawStars = new ArrayList<>();
            float minRa = Float.MAX_VALUE, maxRa = -Float.MAX_VALUE;
            float minDec = Float.MAX_VALUE, maxDec = -Float.MAX_VALUE;

            for (int i = 0; i < starsArray.length(); i++) {
                JSONObject obj = starsArray.getJSONObject(i);
                int id = (int) obj.getDouble("hip");
                if (memberSet.contains(id)) {
                    float ra = (float) obj.getDouble("ra");
                    float dec = (float) obj.getDouble("dec");
                    float mag = (float) obj.getDouble("mag");
                    
                    rawStars.add(new RawStar(id, ra, dec, mag));
                    
                    if (ra < minRa) minRa = ra;
                    if (ra > maxRa) maxRa = ra;
                    if (dec < minDec) minDec = dec;
                    if (dec > maxDec) maxDec = dec;
                }
            }

            if (maxRa - minRa > 12) {
                minRa = Float.MAX_VALUE; maxRa = -Float.MAX_VALUE;
                for (RawStar s : rawStars) {
                    if (s.ra < 12) s.ra += 24;
                    if (s.ra < minRa) minRa = s.ra;
                    if (s.ra > maxRa) maxRa = s.ra;
                }
            }

            float widthRange = (maxRa - minRa) * 15f; // Convert hours to degrees for uniform scaling
            float heightRange = (maxDec - minDec);
            float maxRange = Math.max(widthRange, heightRange);
            if (maxRange == 0) maxRange = 1;

            float viewW = joinDotsView.getWidth();
            float viewH = joinDotsView.getHeight();
            
            // Fallback if dimensions aren't ready
            if (viewW == 0) viewW = getResources().getDisplayMetrics().widthPixels;
            if (viewH == 0) viewH = getResources().getDisplayMetrics().heightPixels;

            float scale = Math.min(viewW, viewH) * 0.6f / maxRange;

            float centerRa = (minRa + maxRa) / 2f;
            float centerDec = (minDec + maxDec) / 2f;

            List<JoinDotsView.StarPoint> filteredStars = new ArrayList<>();
            Map<Integer, JoinDotsView.StarPoint> idMap = new HashMap<>();

            for (RawStar rs : rawStars) {
                // Astronomical projection: RA increases to the left
                float x = (centerRa - rs.ra) * 15f * scale + (viewW / 2f);
                float y = (centerDec - rs.dec) * scale + (viewH / 2f);
                float size = (float) (1.2f - (rs.mag / 6.0f));

                JoinDotsView.StarPoint sp = new JoinDotsView.StarPoint(rs.id, x, y, size);
                filteredStars.add(sp);
                idMap.put(rs.id, sp);
            }

            String linesJson = loadAsset("constellations.json");
            JSONArray linesArray = new JSONArray(linesJson);
            List<JoinDotsView.Line> connections = new ArrayList<>();

            for (int i = 0; i < linesArray.length(); i++) {
                JSONArray pair = linesArray.getJSONArray(i);
                if (pair.length() < 2) continue;
                try {
                    int id1 = pair.getInt(0);
                    int id2 = pair.getInt(1);
                    if (idMap.containsKey(id1) && idMap.containsKey(id2)) {
                        connections.add(new JoinDotsView.Line(idMap.get(id1), idMap.get(id2)));
                    }
                } catch (Exception e) {}
            }

            joinDotsView.setData(filteredStars, connections);

        } catch (Exception e) {
            Log.e("Kepler", "Error loading constellation", e);
        }
    }

    private static class RawStar {
        int id;
        float ra, dec, mag;
        RawStar(int id, float ra, float dec, float mag) {
            this.id = id; this.ra = ra; this.dec = dec; this.mag = mag;
        }
    }

    private String loadAsset(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public void onConstellationCompleted() {
        if (currentConstellationName != null) {
            CollectionManager.addCard(this, currentConstellationName);
            Toast.makeText(this, "Well Done! " + currentConstellationName + " added to your collection!", Toast.LENGTH_LONG).show();
        }
    }
}
