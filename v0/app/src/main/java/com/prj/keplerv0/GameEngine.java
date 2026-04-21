package com.prj.keplerv0;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameEngine {
    public static class Player {
        public int hp = 20;
        public int energy = 0; // Starts at 0, updated by mana curve
        public Card activeCard;
        public List<Card> deck = new ArrayList<>();
        public int poisonTurns = 0;
        public boolean hasShield = false;
        public int tempDefenseBonus = 0;
        public int nextAttackBonus = 0;
        public boolean dodgeNext = false;
        public int defenseReduction = 0;
    }

    public Player user = new Player();
    public Player ai = new Player();
    public boolean isUserTurn = true;
    public int turnCount = 1;
    public boolean isMultiplayer = false;

    public interface GameUpdateListener {
        void onUpdate();
        void onGameOver(String winner);
        void onLog(String message);
    }

    private GameUpdateListener listener;

    public GameEngine(GameUpdateListener listener) {
        this.listener = listener;
    }

    public void startTurn() {
        Player p = isUserTurn ? user : ai;
        
        // Mana Curve: Gain +2 energy for first 2 turns, then +3 afterwards
        int energyGain = (turnCount <= 2) ? 2 : 3;
        p.energy = Math.min(p.energy + energyGain, 10);
        
        // Apply start-of-turn status effects
        if (p.poisonTurns > 0) {
            p.hp -= 2;
            p.poisonTurns--;
            listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " took 2 poison damage.");
        }
        
        if (p.hp <= 0) {
            // Let the UI/onUpdate handle game over checks
            return;
        }
        
        if (!isUserTurn && !isMultiplayer) {
            performAiTurn();
        }
        listener.onUpdate();
    }

    public void playAbility(Ability ability) {
        Player attacker = isUserTurn ? user : ai;
        Player defender = isUserTurn ? ai : user;

        if (attacker.energy < ability.energyCost) {
            listener.onLog("Not enough energy!");
            return;
        }

        attacker.energy -= ability.energyCost;
        listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " used " + ability.name);

        if (ability.type == Ability.Type.ATTACK) {
            if (defender.dodgeNext) {
                listener.onLog((isUserTurn ? (isMultiplayer ? "Opponent" : "AI") : "User") + " dodged the attack!");
                defender.dodgeNext = false;
            } else {
                int baseAtk = attacker.activeCard.attack + attacker.nextAttackBonus;
                int totalAtk = baseAtk + ability.value;
                int targetDef = Math.max(0, defender.activeCard.defense + defender.tempDefenseBonus - defender.defenseReduction);
                
                if (ability.effect == Ability.Effect.IGNORE_DEF) targetDef = 0;

                // Engaging formula: damage reduction scales with defense (never 0)
                float damageMultiplier = 10.0f / (10.0f + targetDef);
                
                // Shield cuts incoming damage in half
                if (defender.hasShield) {
                    damageMultiplier *= 0.5f;
                    defender.hasShield = false;
                }

                int damage = Math.max(1, (int)(totalAtk * damageMultiplier));
                
                defender.hp -= damage;
                attacker.nextAttackBonus = 0; 

                if (ability.effect == Ability.Effect.POISON) defender.poisonTurns = 3;
                if (ability.effect == Ability.Effect.SELF_DAMAGE) attacker.hp -= 2;
            }
        } else {
            if (ability.effect == Ability.Effect.SHIELD) {
                attacker.hasShield = true;
            } else if (ability.effect == Ability.Effect.HEAL) {
                attacker.hp = Math.min(20, attacker.hp + ability.value);
            } else if (ability.effect == Ability.Effect.DODGE) {
                attacker.dodgeNext = true;
            } else {
                attacker.tempDefenseBonus = ability.value;
            }
        }

        if (defender.hp > 0) {
            // AUTOMATIC TURN TRANSITION
            endTurn();
        } else {
            // Force UI update to trigger game over logic if someone died
            listener.onUpdate();
        }
    }

    public void endTurn() {
        if (isUserTurn) {
            user.tempDefenseBonus = 0;
        } else {
            ai.tempDefenseBonus = 0;
        }
        
        isUserTurn = !isUserTurn;
        if (isUserTurn) turnCount++;
        startTurn();
    }

    public boolean swapCard(String cardName) {
        Player p = isUserTurn ? user : ai;
        if (p.energy < 1) {
            listener.onLog("Not enough energy to swap!");
            return false;
        }
        for (Card c : p.deck) {
            if (c.name.equals(cardName) && !c.name.equals(p.activeCard.name)) {
                p.energy -= 1;
                p.activeCard = c;
                // Reset temporary buffs when swapping
                p.nextAttackBonus = 0; 
                p.tempDefenseBonus = 0;
                p.hasShield = false;
                p.dodgeNext = false;
                listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " swapped to " + cardName);
                listener.onUpdate();
                return true;
            }
        }
        return false;
    }

    private void performAiTurn() {
        Ability chosen = null;
        if (ai.hp < 15) {
            for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.energyCost <= ai.energy) {
                    chosen = a;
                    break;
                }
            }
        }

        if (chosen == null) {
            for (Ability a : ai.activeCard.attackAbilities) {
                if (a.energyCost <= ai.energy) {
                    if (chosen == null || a.value > chosen.value) chosen = a;
                }
            }
        }

        if (chosen != null) {
            playAbility(chosen);
        } else {
            endTurn(); // End turn if no move possible
        }
    }

    /**
     * Returns a weakened (50 % ATK/DEF) version of the named card for partial unlocks.
     * Returns null if the card name is not found in the library.
     */
    public static Card getWeakenedCard(String name) {
        for (Card c : getLibrary()) {
            if (c.name.equals(name)) {
                Card weak = new Card("★½ " + c.name, c.attack / 2, c.defense / 2, c.energyCost);
                for (Ability a : c.attackAbilities) {
                    weak.addAbility(new Ability(a.name, a.type, a.value / 2, a.energyCost, a.effect));
                }
                for (Ability a : c.defenseAbilities) {
                    weak.addAbility(new Ability(a.name, a.type, a.value / 2, a.energyCost, a.effect));
                }
                return weak;
            }
        }
        return null;
    }

    public static List<Card> getLibrary() {
        List<Card> lib = new ArrayList<>();
        
        Card aries = new Card("Aries", 9, 5, 0);
        aries.addAbility(new Ability("Horn Charge", Ability.Type.ATTACK, Ability.Element.FIRE, 6, 2, Ability.Effect.NONE));
        aries.addAbility(new Ability("Berserker Rush", Ability.Type.ATTACK, Ability.Element.FIRE, 4, 1, Ability.Effect.SELF_DAMAGE));
        aries.addAbility(new Ability("War Instinct", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE));
        aries.addAbility(new Ability("Momentum Shield", Ability.Type.DEFENSE, Ability.Element.FIRE, 2, 2, Ability.Effect.SHIELD));
        lib.add(aries);

        Card taurus = new Card("Taurus", 6, 10, 0);
        taurus.addAbility(new Ability("Earth Slam", Ability.Type.ATTACK, Ability.Element.EARTH, 5, 2, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Stampede", Ability.Type.ATTACK, Ability.Element.EARTH, 4, 3, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Iron Hide", Ability.Type.DEFENSE, Ability.Element.EARTH, 6, 2, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Fortify", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE));
        lib.add(taurus);

        Card gemini = new Card("Gemini", 7, 6, 0);
        gemini.addAbility(new Ability("Dual Strike", Ability.Type.ATTACK, Ability.Element.AIR, 4, 2, Ability.Effect.NONE));
        gemini.addAbility(new Ability("Mirror Attack", Ability.Type.ATTACK, Ability.Element.AIR, 2, 2, Ability.Effect.NONE));
        gemini.addAbility(new Ability("Illusion Split", Ability.Type.DEFENSE, Ability.Element.LIGHT, 0, 3, Ability.Effect.DODGE));
        gemini.addAbility(new Ability("Swap Fate", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 4, 2, Ability.Effect.NONE));
        lib.add(gemini);

        Card cancer = new Card("Cancer", 5, 9, 0);
        cancer.addAbility(new Ability("Crushing Claw", Ability.Type.ATTACK, Ability.Element.WATER, 4, 2, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Tide Pull", Ability.Type.ATTACK, Ability.Element.WATER, 2, 2, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Shell Shield", Ability.Type.DEFENSE, Ability.Element.WATER, 7, 3, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Regenerate", Ability.Type.DEFENSE, Ability.Element.LIGHT, 5, 2, Ability.Effect.HEAL));
        lib.add(cancer);

        Card leo = new Card("Leo", 8, 9, 0);
        leo.addAbility(new Ability("Solar Claw", Ability.Type.ATTACK, Ability.Element.FIRE, 6, 3, Ability.Effect.NONE));
        leo.addAbility(new Ability("Roar", Ability.Type.ATTACK, Ability.Element.AIR, 0, 2, Ability.Effect.NONE));
        leo.addAbility(new Ability("Nemean Hide", Ability.Type.DEFENSE, Ability.Element.EARTH, 0, 4, Ability.Effect.SHIELD));
        leo.addAbility(new Ability("Royal Guard", Ability.Type.DEFENSE, Ability.Element.LIGHT, 4, 2, Ability.Effect.NONE));
        lib.add(leo);

        Card virgo = new Card("Virgo", 6, 8, 0);
        virgo.addAbility(new Ability("Purity Strike", Ability.Type.ATTACK, Ability.Element.LIGHT, 5, 2, Ability.Effect.NONE));
        virgo.addAbility(new Ability("Harvest", Ability.Type.ATTACK, Ability.Element.EARTH, 3, 2, Ability.Effect.HEAL));
        virgo.addAbility(new Ability("Cleanse", Ability.Type.DEFENSE, Ability.Element.LIGHT, 0, 1, Ability.Effect.NONE));
        virgo.addAbility(new Ability("Blessing", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 5, 2, Ability.Effect.NONE));
        lib.add(virgo);

        Card libra = new Card("Libra", 7, 7, 0);
        libra.addAbility(new Ability("Balance Strike", Ability.Type.ATTACK, Ability.Element.AIR, 7, 3, Ability.Effect.NONE));
        libra.addAbility(new Ability("Karma Cut", Ability.Type.ATTACK, Ability.Element.NEUTRAL, 4, 2, Ability.Effect.NONE));
        libra.addAbility(new Ability("Equalize", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 7, 2, Ability.Effect.NONE));
        libra.addAbility(new Ability("Reflect", Ability.Type.DEFENSE, Ability.Element.LIGHT, 3, 2, Ability.Effect.NONE));
        lib.add(libra);

        Card scorpio = new Card("Scorpius", 9, 5, 0);
        scorpio.addAbility(new Ability("Venom Strike", Ability.Type.ATTACK, Ability.Element.DARK, 4, 3, Ability.Effect.POISON));
        scorpio.addAbility(new Ability("Ambush", Ability.Type.ATTACK, Ability.Element.DARK, 7, 4, Ability.Effect.NONE));
        scorpio.addAbility(new Ability("Exoskeleton", Ability.Type.DEFENSE, Ability.Element.EARTH, 4, 2, Ability.Effect.NONE));
        scorpio.addAbility(new Ability("Shadow Evade", Ability.Type.DEFENSE, Ability.Element.DARK, 0, 3, Ability.Effect.DODGE));
        lib.add(scorpio);

        Card sagittarius = new Card("Sagittarius", 8, 6, 0);
        sagittarius.addAbility(new Ability("Piercing Arrow", Ability.Type.ATTACK, Ability.Element.FIRE, 0, 3, Ability.Effect.IGNORE_DEF));
        sagittarius.addAbility(new Ability("Starfall", Ability.Type.ATTACK, Ability.Element.LIGHT, 5, 2, Ability.Effect.NONE));
        sagittarius.addAbility(new Ability("Evasion", Ability.Type.DEFENSE, Ability.Element.AIR, 5, 2, Ability.Effect.NONE));
        sagittarius.addAbility(new Ability("Focus", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE));
        lib.add(sagittarius);

        Card capricorn = new Card("Capricornus", 7, 9, 0);
        capricorn.addAbility(new Ability("Mountain Strike", Ability.Type.ATTACK, Ability.Element.EARTH, 6, 3, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Tidal Crush", Ability.Type.ATTACK, Ability.Element.WATER, 4, 2, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Endurance", Ability.Type.DEFENSE, Ability.Element.EARTH, 5, 2, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Adapt", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 2, 1, Ability.Effect.NONE));
        lib.add(capricorn);

        Card aquarius = new Card("Aquarius", 6, 8, 0);
        aquarius.addAbility(new Ability("Flow Surge", Ability.Type.ATTACK, Ability.Element.WATER, 5, 2, Ability.Effect.NONE));
        aquarius.addAbility(new Ability("Energy Drain", Ability.Type.ATTACK, Ability.Element.DARK, 2, 3, Ability.Effect.NONE));
        aquarius.addAbility(new Ability("Healing Rain", Ability.Type.DEFENSE, Ability.Element.WATER, 4, 2, Ability.Effect.HEAL));
        aquarius.addAbility(new Ability("Barrier Flow", Ability.Type.DEFENSE, Ability.Element.WATER, 5, 2, Ability.Effect.NONE));
        lib.add(aquarius);

        Card pisces = new Card("Pisces", 6, 7, 0);
        pisces.addAbility(new Ability("Dream Strike", Ability.Type.ATTACK, Ability.Element.LIGHT, 4, 2, Ability.Effect.NONE));
        pisces.addAbility(new Ability("Current Flow", Ability.Type.ATTACK, Ability.Element.WATER, 3, 3, Ability.Effect.DODGE));
        pisces.addAbility(new Ability("Fluid Form", Ability.Type.DEFENSE, Ability.Element.WATER, 0, 3, Ability.Effect.DODGE));
        pisces.addAbility(new Ability("Escape", Ability.Type.DEFENSE, Ability.Element.AIR, 0, 4, Ability.Effect.DODGE));
        lib.add(pisces);

        // --- Major / Famous Constellations ---

        Card orion = new Card("Orion", 10, 4, 0);
        orion.addAbility(new Ability("Hunter's Shot",         Ability.Type.ATTACK,  8, 3, Ability.Effect.IGNORE_DEF));
        orion.addAbility(new Ability("Constellation Barrage", Ability.Type.ATTACK,  5, 2, Ability.Effect.NONE));
        orion.addAbility(new Ability("Celestial Cloak",       Ability.Type.DEFENSE, 0, 2, Ability.Effect.DODGE));
        orion.addAbility(new Ability("Hunter's Instinct",     Ability.Type.DEFENSE, 4, 1, Ability.Effect.NONE));
        lib.add(orion);

        Card ursaMajor = new Card("Ursa Major", 6, 11, 0);
        ursaMajor.addAbility(new Ability("Bear Swipe", Ability.Type.ATTACK,  5, 2, Ability.Effect.NONE));
        ursaMajor.addAbility(new Ability("Starry Maw", Ability.Type.ATTACK,  4, 3, Ability.Effect.NONE));
        ursaMajor.addAbility(new Ability("Arctic Fur", Ability.Type.DEFENSE, 7, 2, Ability.Effect.NONE));
        ursaMajor.addAbility(new Ability("Hibernate",  Ability.Type.DEFENSE, 6, 3, Ability.Effect.HEAL));
        lib.add(ursaMajor);

        Card cassiopeia = new Card("Cassiopeia", 7, 7, 0);
        cassiopeia.addAbility(new Ability("Vain's Wrath", Ability.Type.ATTACK,  5, 2, Ability.Effect.POISON));
        cassiopeia.addAbility(new Ability("Royal Cut",    Ability.Type.ATTACK,  6, 3, Ability.Effect.NONE));
        cassiopeia.addAbility(new Ability("Queen's Parry",Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        cassiopeia.addAbility(new Ability("Regal Evasion",Ability.Type.DEFENSE, 0, 2, Ability.Effect.DODGE));
        lib.add(cassiopeia);

        Card lyra = new Card("Lyra", 5, 8, 0);
        lyra.addAbility(new Ability("Resonant Strike",  Ability.Type.ATTACK,  4, 2, Ability.Effect.NONE));
        lyra.addAbility(new Ability("Discordant Blast", Ability.Type.ATTACK,  3, 1, Ability.Effect.SELF_DAMAGE));
        lyra.addAbility(new Ability("Orphic Hymn",      Ability.Type.DEFENSE, 7, 2, Ability.Effect.HEAL));
        lyra.addAbility(new Ability("Harmonic Shield",  Ability.Type.DEFENSE, 0, 2, Ability.Effect.SHIELD));
        lib.add(lyra);

        Card cygnus = new Card("Cygnus", 8, 6, 0);
        cygnus.addAbility(new Ability("Wing Slash",     Ability.Type.ATTACK,  6, 2, Ability.Effect.NONE));
        cygnus.addAbility(new Ability("Northern Cross", Ability.Type.ATTACK,  4, 2, Ability.Effect.NONE));
        cygnus.addAbility(new Ability("Swan Dive",      Ability.Type.DEFENSE, 0, 2, Ability.Effect.DODGE));
        cygnus.addAbility(new Ability("Feather Veil",   Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        lib.add(cygnus);

        return lib;
    }
}
