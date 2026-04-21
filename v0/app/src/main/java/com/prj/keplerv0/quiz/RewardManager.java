package com.prj.keplerv0.quiz;

import android.content.Context;
import android.content.SharedPreferences;

public class RewardManager {
    private static final String PREFS_NAME = "kepler_prefs";
    private static final String KEY_ENERGY = "user_energy";

    public static void addEnergy(Context context, int amount) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentEnergy = prefs.getInt(KEY_ENERGY, 0);
        prefs.edit().putInt(KEY_ENERGY, currentEnergy + amount).apply();
    }

    public static int getEnergy(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ENERGY, 0);
    }
}
