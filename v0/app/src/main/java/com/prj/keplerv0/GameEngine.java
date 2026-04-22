package com.prj.keplerv0;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameEngine {
    public enum ElementalStatus { NONE, BURNING, DRENCHED, DUST, GUST, LIGHT, DARK }

    public static class Player {
        public int hp = 20;
        public int stamina = 0; // Starts at 0, updated by mana curve
        public Card activeCard;
        public List<Card> deck = new ArrayList<>();
        public int poisonTurns = 0;
        public boolean hasShield = false;
        public int tempDefenseBonus = 0;
        public int nextAttackBonus = 0;
        public boolean dodgeNext = false;
        public int defenseReduction = 0;
        public ElementalStatus elementalStatus = ElementalStatus.NONE;
    }

    public Player user = new Player();
    public Player ai = new Player();
    public boolean isUserTurn = true;
    public int turnCount = 1;
    public boolean isMultiplayer = false;
    /** True once the player has used the 200 EP Full Recovery — limited to once per battle. */
    public boolean fullRecoveryUsed = false;

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
        
        // Mana Curve: Gain +2 stamina for first 2 turns, then +3 afterwards
        int energyGain = (turnCount <= 2) ? 2 : 3;
        p.stamina = Math.min(p.stamina + energyGain, 10);
        
        // Cooldown decrements
        for(Card c : p.deck) {
            for(Ability a : c.attackAbilities) if(a.currentCooldown > 0) a.currentCooldown--;
            for(Ability a : c.defenseAbilities) if(a.currentCooldown > 0) a.currentCooldown--;
        }

        // Apply start-of-turn status effects
        if (p.poisonTurns > 0) {
            p.hp -= 2;
            p.poisonTurns--;
            listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " took 2 poison damage.");
        }
        
        if (p.elementalStatus == ElementalStatus.BURNING) {
            p.hp -= 1;
            listener.onLog((isUserTurn ? "User" : (isMultiplayer ? "Opponent" : "AI")) + " took 1 burn damage.");
        }
        
        if (p.hp <= 0) {
            return;
        }
        
        if (!isUserTurn && !isMultiplayer) {
            listener.onLog("AI is thinking...");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    performAiTurn();
                }
            }, 2000);
        }
        listener.onUpdate();
    }

    public void playAbility(Ability ability) {
        Player attacker = isUserTurn ? user : ai;
        Player defender = isUserTurn ? ai : user;

        if (attacker.stamina < ability.staminaCost) {
            listener.onLog("Not enough stamina!");
            return;
        }
        if (ability.currentCooldown > 0) {
            listener.onLog("Ability is on cooldown!");
            return;
        }

        attacker.stamina -= ability.staminaCost;
        ability.currentCooldown = ability.baseCooldown;
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

                float damageMultiplier = 10.0f / (10.0f + targetDef);
                
                if (defender.hasShield) {
                    damageMultiplier *= 0.5f;
                    defender.hasShield = false;
                }

                int damage = Math.max(1, (int)(totalAtk * damageMultiplier));
                
                // Elemental Chemistry Reactions
                if (ability.element == Ability.Element.FIRE && defender.elementalStatus == ElementalStatus.DRENCHED) {
                    damage = (int)(damage * 1.5f);
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("VAPORIZE! 1.5x Damage!");
                } else if (ability.element == Ability.Element.WATER && defender.elementalStatus == ElementalStatus.BURNING) {
                    damage = (int)(damage * 1.5f);
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("VAPORIZE! 1.5x Damage!");
                } else if (ability.element == Ability.Element.AIR && defender.elementalStatus == ElementalStatus.DUST) {
                    defender.dodgeNext = true;
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("SANDSTORM! Target blinded!");
                } else if (ability.element == Ability.Element.EARTH && defender.elementalStatus == ElementalStatus.GUST) {
                    defender.dodgeNext = true;
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("SANDSTORM! Target blinded!");
                } else if (ability.element == Ability.Element.EARTH && defender.elementalStatus == ElementalStatus.DRENCHED) {
                    attacker.stamina = Math.min(10, attacker.stamina + 1); // Cost +1 to them indirectly by refunding us
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("MUD! Stamina drained!");
                } else if (ability.element == Ability.Element.WATER && defender.elementalStatus == ElementalStatus.DUST) {
                    attacker.stamina = Math.min(10, attacker.stamina + 1);
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("MUD! Stamina drained!");
                } else if (ability.element == Ability.Element.AIR && defender.elementalStatus == ElementalStatus.BURNING) {
                    damage += 4;
                    listener.onLog("FIRESTORM! Massive Burst!");
                } else if (ability.element == Ability.Element.FIRE && defender.elementalStatus == ElementalStatus.GUST) {
                    damage += 4;
                    defender.elementalStatus = ElementalStatus.BURNING;
                    listener.onLog("FIRESTORM! Massive Burst!");
                } else if ((ability.element == Ability.Element.LIGHT && defender.elementalStatus == ElementalStatus.DARK) || (ability.element == Ability.Element.DARK && defender.elementalStatus == ElementalStatus.LIGHT)) {
                    damage = totalAtk; // True damage
                    defender.elementalStatus = ElementalStatus.NONE;
                    listener.onLog("ECLIPSE! True Damage!");
                } else {
                    // Apply new status if no reaction
                    if (ability.element == Ability.Element.FIRE) defender.elementalStatus = ElementalStatus.BURNING;
                    else if (ability.element == Ability.Element.WATER) defender.elementalStatus = ElementalStatus.DRENCHED;
                    else if (ability.element == Ability.Element.EARTH) defender.elementalStatus = ElementalStatus.DUST;
                    else if (ability.element == Ability.Element.AIR) defender.elementalStatus = ElementalStatus.GUST;
                    else if (ability.element == Ability.Element.LIGHT) defender.elementalStatus = ElementalStatus.LIGHT;
                    else if (ability.element == Ability.Element.DARK) defender.elementalStatus = ElementalStatus.DARK;
                }

                defender.hp -= damage;
                attacker.nextAttackBonus = 0; 

                if (ability.effect == Ability.Effect.POISON) defender.poisonTurns = 3;
                if (ability.effect == Ability.Effect.SELF_DAMAGE) attacker.hp -= 2;
            }
        } else {
            // Defense Cleansing
            boolean cleansed = false;
            if (attacker.elementalStatus == ElementalStatus.BURNING && (ability.element == Ability.Element.WATER || ability.element == Ability.Element.EARTH)) cleansed = true;
            else if (attacker.elementalStatus == ElementalStatus.DRENCHED && (ability.element == Ability.Element.FIRE || ability.element == Ability.Element.EARTH)) cleansed = true;
            else if (attacker.elementalStatus == ElementalStatus.DUST && (ability.element == Ability.Element.WATER || ability.element == Ability.Element.AIR)) cleansed = true;
            else if (attacker.elementalStatus == ElementalStatus.GUST && ability.element == Ability.Element.EARTH) cleansed = true;

            if (cleansed) {
                attacker.elementalStatus = ElementalStatus.NONE;
                attacker.hp = Math.min(20, attacker.hp + 1);
                listener.onLog("CLEANSED! +1 HP");
            }

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
            endTurn();
        } else {
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
        if (p.stamina < 1) {
            listener.onLog("Not enough stamina to swap!");
            return false;
        }
        for (Card c : p.deck) {
            if (c.name.equals(cardName) && !c.name.equals(p.activeCard.name)) {
                p.stamina -= 1;
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

    public void initSinglePlayerMatch(List<Card> userDeck, int difficultyLevel) {
        this.isMultiplayer = false;
        this.user.deck = new ArrayList<>(userDeck);
        if (!this.user.deck.isEmpty()) {
            this.user.activeCard = this.user.deck.get(0);
        }
        
        List<Card> library = getLibrary();
        this.ai.deck = new ArrayList<>();
        Random rng = new Random();
        
        // Build a random 3-card deck for AI from library
        while(this.ai.deck.size() < 3 && !library.isEmpty()) {
            int idx = rng.nextInt(library.size());
            Card c = library.get(idx);
            if (!this.ai.deck.contains(c)) {
                this.ai.deck.add(c);
            }
        }
        if (!this.ai.deck.isEmpty()) {
            this.ai.activeCard = this.ai.deck.get(0);
        }
        
        // Easy = normal start, Hard = AI starts with 2 extra stamina advantages
        this.ai.stamina = (difficultyLevel >= 2) ? 2 : 0; 

        this.turnCount = 1;
        this.isUserTurn = true;
        this.startTurn();
    }

    private void performAiTurn() {
        // 1. Evaluate Swap Condition
        if (ai.stamina >= 1 && ai.activeCard != null && ai.deck.size() > 1) {
            boolean hasAffordableAttack = false;
            for(Ability a : ai.activeCard.attackAbilities) if(a.staminaCost <= ai.stamina && a.currentCooldown == 0) hasAffordableAttack = true;
            for(Ability a : ai.activeCard.defenseAbilities) if(a.staminaCost <= ai.stamina && a.currentCooldown == 0 && ai.hp < 15) hasAffordableAttack = true;

            if (!hasAffordableAttack) {
                // No good moves but we have stamina. Swap to a card with affordable moves.
                for (Card c : ai.deck) {
                    if (c != ai.activeCard) {
                        for(Ability a : c.attackAbilities) {
                           if(a.staminaCost <= (ai.stamina - 1) && a.currentCooldown == 0) {
                               swapCard(c.name);
                               break; // Swapped successfully
                           }
                        }
                    }
                    if (ai.activeCard == c) break;
                }
            }
        }

        Ability chosen = null;
        
        // 2. Smart Defense & Cleansing Check
        if (ai.elementalStatus == ElementalStatus.BURNING) {
            for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0 && (a.element == Ability.Element.WATER || a.element == Ability.Element.EARTH)) {
                    chosen = a; break;
                }
            }
        } else if (ai.elementalStatus == ElementalStatus.DRENCHED) {
             for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0 && (a.element == Ability.Element.FIRE || a.element == Ability.Element.EARTH)) {
                    chosen = a; break;
                }
            }
        } else if (ai.elementalStatus == ElementalStatus.DUST) {
             for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0 && (a.element == Ability.Element.WATER || a.element == Ability.Element.AIR)) {
                    chosen = a; break;
                }
            }
        } else if (ai.elementalStatus == ElementalStatus.GUST) {
             for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0 && a.element == Ability.Element.EARTH) {
                    chosen = a; break;
                }
            }
        }

        // Basic panic defense if heavily damaged
        if (chosen == null && ai.hp < 15) {
            for (Ability a : ai.activeCard.defenseAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0) {
                    chosen = a;
                    // Prefer shield or heal
                    if(a.effect == Ability.Effect.SHIELD || a.effect == Ability.Effect.HEAL) break;
                }
            }
        }

        // 3. Elemental Attack Combos
        if (chosen == null) {
            for (Ability a : ai.activeCard.attackAbilities) {
                if (a.staminaCost <= ai.stamina && a.currentCooldown == 0) {
                    boolean isCombo = false;
                    if (a.element == Ability.Element.FIRE && user.elementalStatus == ElementalStatus.DRENCHED) isCombo = true;
                    if (a.element == Ability.Element.WATER && user.elementalStatus == ElementalStatus.BURNING) isCombo = true;
                    if (a.element == Ability.Element.AIR && user.elementalStatus == ElementalStatus.DUST) isCombo = true;
                    if (a.element == Ability.Element.EARTH && user.elementalStatus == ElementalStatus.GUST) isCombo = true;
                    if (a.element == Ability.Element.LIGHT && user.elementalStatus == ElementalStatus.DARK) isCombo = true;
                    if (a.element == Ability.Element.DARK && user.elementalStatus == ElementalStatus.LIGHT) isCombo = true;

                    if (isCombo) {
                        chosen = a; break; // Guarantee taking the combo strike
                    }
                    if (chosen == null || a.value > chosen.value) chosen = a;
                }
            }
        }

        // 4. Stamina Conservation
        // Skip weak attacks to save up for a nuke if healthy
        if (chosen != null && chosen.type == Ability.Type.ATTACK && chosen.value <= 4 && ai.stamina < 4) {
            boolean haveBetterExpensive = false;
            for (Ability a : ai.activeCard.attackAbilities) {
                if (a.staminaCost > ai.stamina && a.value >= 6) haveBetterExpensive = true;
            }
            if (haveBetterExpensive && ai.hp > 10) {
                chosen = null; 
            }
        }

        if (chosen != null) {
            playAbility(chosen);
        } else {
            endTurn(); // End turn to stockpile stamina
        }
    }

    public static Card getWeakenedCard(String name) {
        for (Card c : getLibrary()) {
            if (c.name.equals(name)) {
                Card weak = new Card("★½ " + c.name, c.attack / 2, c.defense / 2, c.staminaCost);
                for (Ability a : c.attackAbilities) {
                    weak.addAbility(new Ability(a.name, a.type, a.element, a.value / 2, a.staminaCost, a.effect, a.baseCooldown));
                }
                for (Ability a : c.defenseAbilities) {
                    weak.addAbility(new Ability(a.name, a.type, a.element, a.value / 2, a.staminaCost, a.effect, a.baseCooldown));
                }
                return weak;
            }
        }
        return null;
    }

    public static List<Card> getLibrary() {
        List<Card> lib = new ArrayList<>();
        
        Card aries = new Card("Aries", 9, 5, 0);
        aries.addAbility(new Ability("Horn Charge", Ability.Type.ATTACK, Ability.Element.FIRE, 6, 2, Ability.Effect.NONE, 0));
        aries.addAbility(new Ability("Berserker Rush", Ability.Type.ATTACK, Ability.Element.FIRE, 4, 1, Ability.Effect.SELF_DAMAGE, 1));
        aries.addAbility(new Ability("War Instinct", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE, 0));
        aries.addAbility(new Ability("Momentum Shield", Ability.Type.DEFENSE, Ability.Element.FIRE, 2, 2, Ability.Effect.SHIELD, 2));
        lib.add(aries);

        Card taurus = new Card("Taurus", 6, 10, 0);
        taurus.addAbility(new Ability("Earth Slam", Ability.Type.ATTACK, Ability.Element.EARTH, 5, 2, Ability.Effect.NONE, 0));
        taurus.addAbility(new Ability("Stampede", Ability.Type.ATTACK, Ability.Element.EARTH, 4, 3, Ability.Effect.NONE, 0));
        taurus.addAbility(new Ability("Iron Hide", Ability.Type.DEFENSE, Ability.Element.EARTH, 6, 2, Ability.Effect.NONE, 0));
        taurus.addAbility(new Ability("Fortify", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE, 0));
        lib.add(taurus);

        Card gemini = new Card("Gemini", 7, 6, 0);
        gemini.addAbility(new Ability("Dual Strike", Ability.Type.ATTACK, Ability.Element.AIR, 4, 2, Ability.Effect.NONE, 0));
        gemini.addAbility(new Ability("Mirror Attack", Ability.Type.ATTACK, Ability.Element.AIR, 2, 2, Ability.Effect.NONE, 0));
        gemini.addAbility(new Ability("Illusion Split", Ability.Type.DEFENSE, Ability.Element.LIGHT, 0, 3, Ability.Effect.DODGE, 2));
        gemini.addAbility(new Ability("Swap Fate", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 4, 2, Ability.Effect.NONE, 0));
        lib.add(gemini);

        Card cancer = new Card("Cancer", 5, 9, 0);
        cancer.addAbility(new Ability("Crushing Claw", Ability.Type.ATTACK, Ability.Element.WATER, 4, 2, Ability.Effect.NONE, 0));
        cancer.addAbility(new Ability("Tide Pull", Ability.Type.ATTACK, Ability.Element.WATER, 2, 2, Ability.Effect.NONE, 0));
        cancer.addAbility(new Ability("Shell Shield", Ability.Type.DEFENSE, Ability.Element.WATER, 7, 3, Ability.Effect.NONE, 0));
        cancer.addAbility(new Ability("Regenerate", Ability.Type.DEFENSE, Ability.Element.LIGHT, 5, 2, Ability.Effect.HEAL, 2));
        lib.add(cancer);

        Card leo = new Card("Leo", 8, 9, 0);
        leo.addAbility(new Ability("Solar Claw", Ability.Type.ATTACK, Ability.Element.FIRE, 6, 3, Ability.Effect.NONE, 0));
        leo.addAbility(new Ability("Roar", Ability.Type.ATTACK, Ability.Element.AIR, 0, 2, Ability.Effect.NONE, 0));
        leo.addAbility(new Ability("Nemean Hide", Ability.Type.DEFENSE, Ability.Element.EARTH, 0, 4, Ability.Effect.SHIELD, 2));
        leo.addAbility(new Ability("Royal Guard", Ability.Type.DEFENSE, Ability.Element.LIGHT, 4, 2, Ability.Effect.NONE, 0));
        lib.add(leo);

        Card virgo = new Card("Virgo", 6, 8, 0);
        virgo.addAbility(new Ability("Purity Strike", Ability.Type.ATTACK, Ability.Element.LIGHT, 5, 2, Ability.Effect.NONE, 0));
        virgo.addAbility(new Ability("Harvest", Ability.Type.ATTACK, Ability.Element.EARTH, 3, 2, Ability.Effect.HEAL, 2));
        virgo.addAbility(new Ability("Cleanse", Ability.Type.DEFENSE, Ability.Element.LIGHT, 0, 1, Ability.Effect.NONE, 0));
        virgo.addAbility(new Ability("Blessing", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 5, 2, Ability.Effect.NONE, 0));
        lib.add(virgo);

        Card libra = new Card("Libra", 7, 7, 0);
        libra.addAbility(new Ability("Balance Strike", Ability.Type.ATTACK, Ability.Element.AIR, 7, 3, Ability.Effect.NONE, 0));
        libra.addAbility(new Ability("Karma Cut", Ability.Type.ATTACK, Ability.Element.NEUTRAL, 4, 2, Ability.Effect.NONE, 0));
        libra.addAbility(new Ability("Equalize", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 7, 2, Ability.Effect.NONE, 0));
        libra.addAbility(new Ability("Reflect", Ability.Type.DEFENSE, Ability.Element.LIGHT, 3, 2, Ability.Effect.NONE, 0));
        lib.add(libra);

        Card scorpio = new Card("Scorpius", 9, 5, 0);
        scorpio.addAbility(new Ability("Venom Strike", Ability.Type.ATTACK, Ability.Element.DARK, 4, 3, Ability.Effect.POISON, 1));
        scorpio.addAbility(new Ability("Ambush", Ability.Type.ATTACK, Ability.Element.DARK, 7, 4, Ability.Effect.NONE, 0));
        scorpio.addAbility(new Ability("Exoskeleton", Ability.Type.DEFENSE, Ability.Element.EARTH, 4, 2, Ability.Effect.NONE, 0));
        scorpio.addAbility(new Ability("Shadow Evade", Ability.Type.DEFENSE, Ability.Element.DARK, 0, 3, Ability.Effect.DODGE, 2));
        lib.add(scorpio);

        Card sagittarius = new Card("Sagittarius", 8, 6, 0);
        sagittarius.addAbility(new Ability("Piercing Arrow", Ability.Type.ATTACK, Ability.Element.FIRE, 0, 3, Ability.Effect.IGNORE_DEF, 1));
        sagittarius.addAbility(new Ability("Starfall", Ability.Type.ATTACK, Ability.Element.LIGHT, 5, 2, Ability.Effect.NONE, 0));
        sagittarius.addAbility(new Ability("Evasion", Ability.Type.DEFENSE, Ability.Element.AIR, 5, 2, Ability.Effect.NONE, 0));
        sagittarius.addAbility(new Ability("Focus", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 3, 1, Ability.Effect.NONE, 0));
        lib.add(sagittarius);

        Card capricorn = new Card("Capricornus", 7, 9, 0);
        capricorn.addAbility(new Ability("Mountain Strike", Ability.Type.ATTACK, Ability.Element.EARTH, 6, 3, Ability.Effect.NONE, 0));
        capricorn.addAbility(new Ability("Tidal Crush", Ability.Type.ATTACK, Ability.Element.WATER, 4, 2, Ability.Effect.NONE, 0));
        capricorn.addAbility(new Ability("Endurance", Ability.Type.DEFENSE, Ability.Element.EARTH, 5, 2, Ability.Effect.NONE, 0));
        capricorn.addAbility(new Ability("Adapt", Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 2, 1, Ability.Effect.NONE, 0));
        lib.add(capricorn);

        Card aquarius = new Card("Aquarius", 6, 8, 0);
        aquarius.addAbility(new Ability("Flow Surge", Ability.Type.ATTACK, Ability.Element.WATER, 5, 2, Ability.Effect.NONE, 0));
        aquarius.addAbility(new Ability("Stamina Drain", Ability.Type.ATTACK, Ability.Element.DARK, 2, 3, Ability.Effect.NONE, 0));
        aquarius.addAbility(new Ability("Healing Rain", Ability.Type.DEFENSE, Ability.Element.WATER, 4, 2, Ability.Effect.HEAL, 2));
        aquarius.addAbility(new Ability("Barrier Flow", Ability.Type.DEFENSE, Ability.Element.WATER, 5, 2, Ability.Effect.NONE, 0));
        lib.add(aquarius);

        Card pisces = new Card("Pisces", 6, 7, 0);
        pisces.addAbility(new Ability("Dream Strike", Ability.Type.ATTACK, Ability.Element.LIGHT, 4, 2, Ability.Effect.NONE, 0));
        pisces.addAbility(new Ability("Current Flow", Ability.Type.ATTACK, Ability.Element.WATER, 3, 3, Ability.Effect.DODGE, 2));
        pisces.addAbility(new Ability("Fluid Form", Ability.Type.DEFENSE, Ability.Element.WATER, 0, 3, Ability.Effect.DODGE, 2));
        pisces.addAbility(new Ability("Escape", Ability.Type.DEFENSE, Ability.Element.AIR, 0, 4, Ability.Effect.DODGE, 2));
        lib.add(pisces);

        // --- Major / Famous Constellations ---

        Card orion = new Card("Orion", 10, 4, 0);
        orion.addAbility(new Ability("Hunter's Shot",         Ability.Type.ATTACK,  Ability.Element.FIRE, 8, 3, Ability.Effect.IGNORE_DEF, 1));
        orion.addAbility(new Ability("Constellation Barrage", Ability.Type.ATTACK,  Ability.Element.LIGHT, 5, 2, Ability.Effect.NONE, 0));
        orion.addAbility(new Ability("Celestial Cloak",       Ability.Type.DEFENSE, Ability.Element.DARK, 0, 2, Ability.Effect.DODGE, 2));
        orion.addAbility(new Ability("Hunter's Instinct",     Ability.Type.DEFENSE, Ability.Element.NEUTRAL, 4, 1, Ability.Effect.NONE, 0));
        lib.add(orion);

        Card ursaMajor = new Card("Ursa Major", 6, 11, 0);
        ursaMajor.addAbility(new Ability("Bear Swipe", Ability.Type.ATTACK,  Ability.Element.EARTH, 5, 2, Ability.Effect.NONE, 0));
        ursaMajor.addAbility(new Ability("Starry Maw", Ability.Type.ATTACK,  Ability.Element.DARK, 4, 3, Ability.Effect.NONE, 0));
        ursaMajor.addAbility(new Ability("Arctic Fur", Ability.Type.DEFENSE, Ability.Element.WATER, 7, 2, Ability.Effect.NONE, 0));
        ursaMajor.addAbility(new Ability("Hibernate",  Ability.Type.DEFENSE, Ability.Element.EARTH, 6, 3, Ability.Effect.HEAL, 2));
        lib.add(ursaMajor);

        Card cassiopeia = new Card("Cassiopeia", 7, 7, 0);
        cassiopeia.addAbility(new Ability("Vain's Wrath", Ability.Type.ATTACK,  Ability.Element.DARK, 5, 2, Ability.Effect.POISON, 1));
        cassiopeia.addAbility(new Ability("Royal Cut",    Ability.Type.ATTACK,  Ability.Element.LIGHT, 6, 3, Ability.Effect.NONE, 0));
        cassiopeia.addAbility(new Ability("Queen's Parry",Ability.Type.DEFENSE, Ability.Element.AIR, 5, 2, Ability.Effect.NONE, 0));
        cassiopeia.addAbility(new Ability("Regal Evasion",Ability.Type.DEFENSE, Ability.Element.LIGHT, 0, 2, Ability.Effect.DODGE, 2));
        lib.add(cassiopeia);

        Card lyra = new Card("Lyra", 5, 8, 0);
        lyra.addAbility(new Ability("Resonant Strike",  Ability.Type.ATTACK,  Ability.Element.AIR, 4, 2, Ability.Effect.NONE, 0));
        lyra.addAbility(new Ability("Discordant Blast", Ability.Type.ATTACK,  Ability.Element.DARK, 3, 1, Ability.Effect.SELF_DAMAGE, 1));
        lyra.addAbility(new Ability("Orphic Hymn",      Ability.Type.DEFENSE, Ability.Element.LIGHT, 7, 2, Ability.Effect.HEAL, 2));
        lyra.addAbility(new Ability("Harmonic Shield",  Ability.Type.DEFENSE, Ability.Element.AIR, 0, 2, Ability.Effect.SHIELD, 2));
        lib.add(lyra);

        Card cygnus = new Card("Cygnus", 8, 6, 0);
        cygnus.addAbility(new Ability("Wing Slash",     Ability.Type.ATTACK,  Ability.Element.AIR, 6, 2, Ability.Effect.NONE, 0));
        cygnus.addAbility(new Ability("Northern Cross", Ability.Type.ATTACK,  Ability.Element.LIGHT, 4, 2, Ability.Effect.NONE, 0));
        cygnus.addAbility(new Ability("Swan Dive",      Ability.Type.DEFENSE, Ability.Element.WATER, 0, 2, Ability.Effect.DODGE, 2));
        cygnus.addAbility(new Ability("Feather Veil",   Ability.Type.DEFENSE, Ability.Element.AIR, 5, 2, Ability.Effect.NONE, 0));
        lib.add(cygnus);

        return lib;
    }
}
