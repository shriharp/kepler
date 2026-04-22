package com.prj.keplerv0;

public class Ability {
    public enum Type { ATTACK, DEFENSE }
    public enum Effect { NONE, POISON, SHIELD, HEAL, SELF_DAMAGE, IGNORE_DEF, DODGE }
    public enum Element { FIRE, WATER, EARTH, AIR, LIGHT, DARK, NEUTRAL }

    public String name;
    public Type type;
    public Element element;
    public int value;
    public int staminaCost;
    public Effect effect;
    public int baseCooldown;
    public int currentCooldown;

    public Ability(String name, Type type, Element element, int value, int staminaCost, Effect effect, int baseCooldown) {
        this.name = name;
        this.type = type;
        this.element = element;
        this.value = value;
        this.staminaCost = staminaCost;
        this.effect = effect;
        this.baseCooldown = baseCooldown;
        this.currentCooldown = 0;
    }
}
