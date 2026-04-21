package com.prj.keplerv0;

public class Ability {
    public enum Type { ATTACK, DEFENSE }
    public enum Effect { NONE, POISON, SHIELD, HEAL, SELF_DAMAGE, IGNORE_DEF, DODGE }
    public enum Element { FIRE, WATER, EARTH, AIR, LIGHT, DARK, NEUTRAL }

    public String name;
    public Type type;
    public Element element;
    public int value;
    public int energyCost;
    public Effect effect;

    public Ability(String name, Type type, Element element, int value, int energyCost, Effect effect) {
        this.name = name;
        this.type = type;
        this.element = element;
        this.value = value;
        this.energyCost = energyCost;
        this.effect = effect;
    }
}
