package com.prj.keplerv0;

import android.content.Context;
import java.util.Set;

public class CollectionManager {
    // --- Full unlocks (earned via JoinDots) ---

    public static void addCard(Context context, String constellationName) {
        PersistenceManager.getInstance(context).unlockCardFully(constellationName, "JOIN_DOTS");
    }

    public static Set<String> getCollection(Context context) {
        return PersistenceManager.getInstance(context).getFullyUnlockedCards();
    }

    // --- Partial unlocks (earned by tapping a constellation in the sky viewer) ---

    /**
     * Marks a constellation as partially unlocked.
     * Has no effect if the constellation is already fully unlocked.
     * Returns true if this was a *new* partial unlock (so callers can show a toast).
     */
    public static boolean addPartialCard(Context context, String constellationName) {
        return PersistenceManager.getInstance(context).unlockCardPartially(constellationName);
    }

    /** Returns constellation names that are partially (but not fully) unlocked. */
    public static Set<String> getPartialCollection(Context context) {
        return PersistenceManager.getInstance(context).getPartiallyUnlockedCards();
    }
}
