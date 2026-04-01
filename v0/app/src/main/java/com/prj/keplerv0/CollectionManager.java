package com.prj.keplerv0;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class CollectionManager {
    private static final String PREF_NAME = "kepler_collection";
    private static final String KEY_CARDS = "collected_cards";

    public static void addCard(Context context, String constellationName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> cards = new HashSet<>(prefs.getStringSet(KEY_CARDS, new HashSet<>()));
        cards.add(constellationName);
        prefs.edit().putStringSet(KEY_CARDS, cards).apply();
    }

    public static Set<String> getCollection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_CARDS, new HashSet<>());
    }
}
