package com.prj.keplerv0;

import android.content.Context;

/**
 * BattleRecoveryManager — Emergency EP spend options during combat.
 *
 * Design goals:
 *   - Feels like a strategic lifeline, NOT a cheat.
 *   - Costs are high enough to matter; can't be spammed.
 *   - Recoveries are modest — they buy breathing room, not an auto-win.
 *
 * Cost Balancing:
 *   Quiz: ~10 EP per correct answer (easy), ~20 EP (hard differential).
 *   A typical session: 5–8 questions ≈ 50–80 EP.
 *   Recovery options should cost 1–2 sessions worth of answers so players
 *   feel the investment without grinding.
 *
 * Supported options:
 *   RESTORE_HP    → +5 HP  (capped at max 20)    | 80 EP
 *   RESTORE_STAMINA → +2 Stamina (capped at 10)  | 60 EP
 *   FULL_RECOVERY   → +8 HP, +3 Stamina          | 200 EP  (once per battle)
 */
public class BattleRecoveryManager {

    public static final int COST_RESTORE_HP       = 80;
    public static final int COST_RESTORE_STAMINA  = 60;
    public static final int COST_FULL_RECOVERY    = 200;

    public static final int HEAL_HP_AMOUNT        = 5;
    public static final int HEAL_STAMINA_AMOUNT   = 2;
    public static final int FULL_HEAL_HP          = 8;
    public static final int FULL_HEAL_STAMINA     = 3;

    public enum RecoveryResult {
        SUCCESS,
        INSUFFICIENT_EP,
        FULL_RECOVERY_ALREADY_USED,
        RECOVERY_NOT_NEEDED   // HP / Stamina already full — disallow waste
    }

    /**
     * Spends 80 EP to restore +5 HP (capped at 20).
     */
    public static RecoveryResult restoreHp(Context context, GameEngine engine) {
        GameEngine.Player user = engine.user;

        if (user.hp >= 20) return RecoveryResult.RECOVERY_NOT_NEEDED;

        PersistenceManager pm = PersistenceManager.getInstance(context);
        if (pm.getEnergyPoints() < COST_RESTORE_HP) return RecoveryResult.INSUFFICIENT_EP;

        pm.spendEnergyPoints(COST_RESTORE_HP);
        user.hp = Math.min(20, user.hp + HEAL_HP_AMOUNT);
        return RecoveryResult.SUCCESS;
    }

    /**
     * Spends 60 EP to restore +2 Stamina (capped at 10).
     */
    public static RecoveryResult restoreStamina(Context context, GameEngine engine) {
        GameEngine.Player user = engine.user;

        if (user.stamina >= 10) return RecoveryResult.RECOVERY_NOT_NEEDED;

        PersistenceManager pm = PersistenceManager.getInstance(context);
        if (pm.getEnergyPoints() < COST_RESTORE_STAMINA) return RecoveryResult.INSUFFICIENT_EP;

        pm.spendEnergyPoints(COST_RESTORE_STAMINA);
        user.stamina = Math.min(10, user.stamina + HEAL_STAMINA_AMOUNT);
        return RecoveryResult.SUCCESS;
    }

    /**
     * Spends 200 EP for full emergency recovery: +8 HP, +3 Stamina.
     * Limited to ONCE per battle via a flag on the engine.
     * This is a last-resort move — not a get-out-of-jail-free card.
     */
    public static RecoveryResult fullRecovery(Context context, GameEngine engine) {
        if (engine.fullRecoveryUsed) return RecoveryResult.FULL_RECOVERY_ALREADY_USED;

        PersistenceManager pm = PersistenceManager.getInstance(context);
        if (pm.getEnergyPoints() < COST_FULL_RECOVERY) return RecoveryResult.INSUFFICIENT_EP;

        pm.spendEnergyPoints(COST_FULL_RECOVERY);
        engine.user.hp      = Math.min(20, engine.user.hp + FULL_HEAL_HP);
        engine.user.stamina = Math.min(10, engine.user.stamina + FULL_HEAL_STAMINA);
        engine.fullRecoveryUsed = true;
        return RecoveryResult.SUCCESS;
    }
}
