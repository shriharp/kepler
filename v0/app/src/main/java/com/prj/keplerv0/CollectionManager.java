package com.prj.keplerv0;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class CollectionManager {
    private static final String PREF_NAME   = "kepler_collection";
    private static final String KEY_CARDS   = "collected_cards";
    private static final String KEY_PARTIAL = "partial_cards";   // unlocked via star-gazing

    // --- Full unlocks (earned via JoinDots) ---

    public static void addCard(Context context, String constellationName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> cards = new HashSet<>(prefs.getStringSet(KEY_CARDS, new HashSet<>()));
        cards.add(constellationName);
        prefs.edit().putStringSet(KEY_CARDS, cards).apply();
    }

    public static Set<String> getCollection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_CARDS, new HashSet<>()));
    }

    // --- Partial unlocks (earned by tapping a constellation in the sky viewer) ---

    /**
     * Marks a constellation as partially unlocked.
     * Has no effect if the constellation is already fully unlocked.
     * Returns true if this was a *new* partial unlock (so callers can show a toast).
     */
    public static boolean addPartialCard(Context context, String constellationName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> full = new HashSet<>(prefs.getStringSet(KEY_CARDS, new HashSet<>()));
        if (full.contains(constellationName)) return false;

        Set<String> partial = new HashSet<>(prefs.getStringSet(KEY_PARTIAL, new HashSet<>()));
        boolean isNew = partial.add(constellationName);
        if (isNew) prefs.edit().putStringSet(KEY_PARTIAL, partial).apply();
        return isNew;
    }

    /** Returns constellation names that are partially (but not fully) unlocked. */
    public static Set<String> getPartialCollection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> full    = new HashSet<>(prefs.getStringSet(KEY_CARDS,   new HashSet<>()));
        Set<String> partial = new HashSet<>(prefs.getStringSet(KEY_PARTIAL, new HashSet<>()));
        partial.removeAll(full);   // promote: a fully-unlocked card is no longer partial
        return partial;
    }
}
