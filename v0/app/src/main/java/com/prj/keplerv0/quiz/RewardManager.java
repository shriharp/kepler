package com.prj.keplerv0.quiz;

import android.content.Context;
import android.content.SharedPreferences;
import com.prj.keplerv0.PersistenceManager;

public class RewardManager {
    private static final String PREFS_NAME = "kepler_prefs";
    private static final String KEY_ENERGY = "user_energy";

    public static void addEnergy(Context context, int amount) {
        PersistenceManager.getInstance(context).addEnergyPoints(amount);
    }

    public static int getEnergy(Context context) {
        return PersistenceManager.getInstance(context).getEnergyPoints();
    }
}
