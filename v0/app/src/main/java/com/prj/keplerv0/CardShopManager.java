package com.prj.keplerv0;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

/**
 * CardShopManager — handles Energy Point costs and purchases for cards.
 *
 * Cost Tiers (proportional to rarity / constellation significance):
 *   COMMON  (zodiac)      →  100 EP
 *   RARE    (major / famous) → 250 EP
 *   MYTHIC  (future)         → 600 EP  (reserved)
 *
 * Purchasing is an *alternative* to the JoinDots trace unlock.
 * Both paths lead to the same fully-unlocked card state.
 */
public class CardShopManager {

    public enum Rarity { COMMON, RARE, MYTHIC }

    /** Cost in Energy Points per rarity tier. */
    public static final Map<Rarity, Integer> TIER_COST = new HashMap<>();

    static {
        TIER_COST.put(Rarity.COMMON, 100);
        TIER_COST.put(Rarity.RARE,   250);
        TIER_COST.put(Rarity.MYTHIC, 600);
    }

    /** Maps each card name to its rarity. Update when new cards are added. */
    private static final Map<String, Rarity> CARD_RARITY = new HashMap<>();

    static {
        // Zodiac — COMMON
        CARD_RARITY.put("Aries",       Rarity.COMMON);
        CARD_RARITY.put("Taurus",      Rarity.COMMON);
        CARD_RARITY.put("Gemini",      Rarity.COMMON);
        CARD_RARITY.put("Cancer",      Rarity.COMMON);
        CARD_RARITY.put("Leo",         Rarity.COMMON);
        CARD_RARITY.put("Virgo",       Rarity.COMMON);
        CARD_RARITY.put("Libra",       Rarity.COMMON);
        CARD_RARITY.put("Scorpius",    Rarity.COMMON);
        CARD_RARITY.put("Sagittarius", Rarity.COMMON);
        CARD_RARITY.put("Capricornus", Rarity.COMMON);
        CARD_RARITY.put("Aquarius",    Rarity.COMMON);
        CARD_RARITY.put("Pisces",      Rarity.COMMON);

        // Major constellations — RARE
        CARD_RARITY.put("Orion",       Rarity.RARE);
        CARD_RARITY.put("Ursa Major",  Rarity.RARE);
        CARD_RARITY.put("Cassiopeia",  Rarity.RARE);
        CARD_RARITY.put("Lyra",        Rarity.RARE);
        CARD_RARITY.put("Cygnus",      Rarity.RARE);
    }

    /** Returns the rarity of a card, defaulting to COMMON if unknown. */
    public static Rarity getRarity(String cardName) {
        Rarity r = CARD_RARITY.get(cardName);
        return r != null ? r : Rarity.COMMON;
    }

    /** Returns the EP cost to purchase a given card. */
    public static int getCost(String cardName) {
        return TIER_COST.get(getRarity(cardName));
    }

    /**
     * Attempts to buy a card with Energy Points.
     *
     * @return BuyResult indicating success or the reason for failure.
     */
    public static BuyResult buyCard(Context context, String cardName) {
        PersistenceManager pm = PersistenceManager.getInstance(context);

        if (pm.isFullyUnlocked(cardName)) {
            return BuyResult.ALREADY_OWNED;
        }

        int cost = getCost(cardName);
        int balance = pm.getEnergyPoints();

        if (balance < cost) {
            return BuyResult.INSUFFICIENT_EP;
        }

        boolean spent = pm.spendEnergyPoints(cost);
        if (!spent) return BuyResult.INSUFFICIENT_EP; // Race-condition guard

        pm.unlockCardFully(cardName, "PURCHASE");
        return BuyResult.SUCCESS;
    }

    public enum BuyResult {
        SUCCESS,
        ALREADY_OWNED,
        INSUFFICIENT_EP
    }
}
