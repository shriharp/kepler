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
        
        // Mana Curve: Gain energy equal to turnCount, capped at 3
        int energyGain = Math.min(turnCount, 3);
        p.energy = Math.min(p.energy + energyGain, 10);
        
        // Apply start-of-turn status effects
        if (p.poisonTurns > 0) {
            p.hp -= 2;
            p.poisonTurns--;
            listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " took 2 poison damage.");
        }
        
        if (p.hp <= 0) {
            listener.onGameOver(isUserTurn ? (isMultiplayer ? "Opponent" : "AI") : "User");
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

        if (defender.hp <= 0) {
            listener.onGameOver(isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI"));
        } else {
            // AUTOMATIC TURN TRANSITION
            endTurn();
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

    public static List<Card> getLibrary() {
        List<Card> lib = new ArrayList<>();
        
        Card aries = new Card("Aries", 9, 5, 0);
        aries.addAbility(new Ability("Horn Charge", Ability.Type.ATTACK, 6, 2, Ability.Effect.NONE));
        aries.addAbility(new Ability("Berserker Rush", Ability.Type.ATTACK, 4, 1, Ability.Effect.SELF_DAMAGE));
        aries.addAbility(new Ability("War Instinct", Ability.Type.DEFENSE, 3, 1, Ability.Effect.NONE));
        aries.addAbility(new Ability("Momentum Shield", Ability.Type.DEFENSE, 2, 2, Ability.Effect.SHIELD));
        lib.add(aries);

        Card taurus = new Card("Taurus", 6, 10, 0);
        taurus.addAbility(new Ability("Earth Slam", Ability.Type.ATTACK, 5, 2, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Stampede", Ability.Type.ATTACK, 4, 3, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Iron Hide", Ability.Type.DEFENSE, 6, 2, Ability.Effect.NONE));
        taurus.addAbility(new Ability("Fortify", Ability.Type.DEFENSE, 3, 1, Ability.Effect.NONE));
        lib.add(taurus);

        Card gemini = new Card("Gemini", 7, 6, 0);
        gemini.addAbility(new Ability("Dual Strike", Ability.Type.ATTACK, 4, 2, Ability.Effect.NONE));
        gemini.addAbility(new Ability("Mirror Attack", Ability.Type.ATTACK, 2, 2, Ability.Effect.NONE));
        gemini.addAbility(new Ability("Illusion Split", Ability.Type.DEFENSE, 0, 3, Ability.Effect.DODGE));
        gemini.addAbility(new Ability("Swap Fate", Ability.Type.DEFENSE, 4, 2, Ability.Effect.NONE));
        lib.add(gemini);

        Card cancer = new Card("Cancer", 5, 9, 0);
        cancer.addAbility(new Ability("Crushing Claw", Ability.Type.ATTACK, 4, 2, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Tide Pull", Ability.Type.ATTACK, 2, 2, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Shell Shield", Ability.Type.DEFENSE, 7, 3, Ability.Effect.NONE));
        cancer.addAbility(new Ability("Regenerate", Ability.Type.DEFENSE, 5, 2, Ability.Effect.HEAL));
        lib.add(cancer);

        Card leo = new Card("Leo", 8, 9, 0);
        leo.addAbility(new Ability("Solar Claw", Ability.Type.ATTACK, 6, 3, Ability.Effect.NONE));
        leo.addAbility(new Ability("Roar", Ability.Type.ATTACK, 0, 2, Ability.Effect.NONE));
        leo.addAbility(new Ability("Nemean Hide", Ability.Type.DEFENSE, 0, 4, Ability.Effect.SHIELD));
        leo.addAbility(new Ability("Royal Guard", Ability.Type.DEFENSE, 4, 2, Ability.Effect.NONE));
        lib.add(leo);

        Card virgo = new Card("Virgo", 6, 8, 0);
        virgo.addAbility(new Ability("Purity Strike", Ability.Type.ATTACK, 5, 2, Ability.Effect.NONE));
        virgo.addAbility(new Ability("Harvest", Ability.Type.ATTACK, 3, 2, Ability.Effect.HEAL));
        virgo.addAbility(new Ability("Cleanse", Ability.Type.DEFENSE, 0, 1, Ability.Effect.NONE));
        virgo.addAbility(new Ability("Blessing", Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        lib.add(virgo);

        Card libra = new Card("Libra", 7, 7, 0);
        libra.addAbility(new Ability("Balance Strike", Ability.Type.ATTACK, 7, 3, Ability.Effect.NONE));
        libra.addAbility(new Ability("Karma Cut", Ability.Type.ATTACK, 4, 2, Ability.Effect.NONE));
        libra.addAbility(new Ability("Equalize", Ability.Type.DEFENSE, 7, 2, Ability.Effect.NONE));
        libra.addAbility(new Ability("Reflect", Ability.Type.DEFENSE, 3, 2, Ability.Effect.NONE));
        lib.add(libra);

        Card scorpio = new Card("Scorpius", 9, 5, 0);
        scorpio.addAbility(new Ability("Venom Strike", Ability.Type.ATTACK, 4, 3, Ability.Effect.POISON));
        scorpio.addAbility(new Ability("Ambush", Ability.Type.ATTACK, 7, 4, Ability.Effect.NONE));
        scorpio.addAbility(new Ability("Exoskeleton", Ability.Type.DEFENSE, 4, 2, Ability.Effect.NONE));
        scorpio.addAbility(new Ability("Shadow Evade", Ability.Type.DEFENSE, 0, 3, Ability.Effect.DODGE));
        lib.add(scorpio);

        Card sagittarius = new Card("Sagittarius", 8, 6, 0);
        sagittarius.addAbility(new Ability("Piercing Arrow", Ability.Type.ATTACK, 0, 3, Ability.Effect.IGNORE_DEF));
        sagittarius.addAbility(new Ability("Starfall", Ability.Type.ATTACK, 5, 2, Ability.Effect.NONE));
        sagittarius.addAbility(new Ability("Evasion", Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        sagittarius.addAbility(new Ability("Focus", Ability.Type.DEFENSE, 3, 1, Ability.Effect.NONE));
        lib.add(sagittarius);

        Card capricorn = new Card("Capricornus", 7, 9, 0);
        capricorn.addAbility(new Ability("Mountain Strike", Ability.Type.ATTACK, 6, 3, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Tidal Crush", Ability.Type.ATTACK, 4, 2, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Endurance", Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        capricorn.addAbility(new Ability("Adapt", Ability.Type.DEFENSE, 2, 1, Ability.Effect.NONE));
        lib.add(capricorn);

        Card aquarius = new Card("Aquarius", 6, 8, 0);
        aquarius.addAbility(new Ability("Flow Surge", Ability.Type.ATTACK, 5, 2, Ability.Effect.NONE));
        aquarius.addAbility(new Ability("Energy Drain", Ability.Type.ATTACK, 2, 3, Ability.Effect.NONE));
        aquarius.addAbility(new Ability("Healing Rain", Ability.Type.DEFENSE, 4, 2, Ability.Effect.HEAL));
        aquarius.addAbility(new Ability("Barrier Flow", Ability.Type.DEFENSE, 5, 2, Ability.Effect.NONE));
        lib.add(aquarius);

        Card pisces = new Card("Pisces", 6, 7, 0);
        pisces.addAbility(new Ability("Dream Strike", Ability.Type.ATTACK, 4, 2, Ability.Effect.NONE));
        pisces.addAbility(new Ability("Current Flow", Ability.Type.ATTACK, 3, 3, Ability.Effect.DODGE));
        pisces.addAbility(new Ability("Fluid Form", Ability.Type.DEFENSE, 0, 3, Ability.Effect.DODGE));
        pisces.addAbility(new Ability("Escape", Ability.Type.DEFENSE, 0, 4, Ability.Effect.DODGE));
        lib.add(pisces);

        return lib;
    }
}
