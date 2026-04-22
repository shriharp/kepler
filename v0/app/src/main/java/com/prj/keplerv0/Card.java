package com.prj.keplerv0;

import java.util.ArrayList;
import java.util.List;

public class Card {
    public String name;
    public int attack;
    public int defense;
    public int staminaCost;
    public List<Ability> attackAbilities;
    public List<Ability> defenseAbilities;

    public Card(String name, int attack, int defense, int staminaCost) {
        this.name = name;
        this.attack = attack;
        this.defense = defense;
        this.staminaCost = staminaCost;
        this.attackAbilities = new ArrayList<>();
        this.defenseAbilities = new ArrayList<>();
    }

    public void addAbility(Ability ability) {
        if (ability.type == Ability.Type.ATTACK) {
            attackAbilities.add(ability);
        } else {
            defenseAbilities.add(ability);
        }
    }
}
