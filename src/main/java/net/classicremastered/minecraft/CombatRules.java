package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.mob.*;
import java.util.Random;

public final class CombatRules {
    private CombatRules() {
    }

    public static Difficulty difficulty(Level l) {
        return l != null && l.minecraft != null && l.minecraft.settings != null
                ? l.minecraft.settings.difficulty
                : Difficulty.NORMAL;
    }

    public static int cap(Level l) {
        return switch (difficulty(l)) {
            case PEACEFUL, EASY -> 24;
            case NORMAL -> 28;
            case HARD -> 32;
        };
    }

    public static int damage(Level l, int damage) {
        return switch (difficulty(l)) {
            case PEACEFUL -> 0;
            case EASY -> Math.max(1, (damage + 1) / 2);
            case NORMAL -> damage;
            case HARD -> Math.max(1, (damage * 3 + 1) / 2);
        };
    }

    public static void roll(Mob m) {
        net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();
        if (m.equipment == null)
            m.equipment = new int[5];
        if (m.equipmentRolled || m.level == null || m.level.isNetworkMode())
            return;
        m.equipmentRolled = true;
        if (!(m instanceof Zombie || m instanceof Skeleton))
            return;
        if (m instanceof HumanoidMob h) {
            h.helmet = false;
            h.armor = false;
        }
        Difficulty d = difficulty(m.level);
        if (d == Difficulty.PEACEFUL || d == Difficulty.EASY)
            return;
        Random r = m.level.random;
        boolean hard = d == Difficulty.HARD;
        if (r.nextFloat() < (hard ? .4f : .2f)) {
            int start = r.nextInt(4);
            for (int i = 0; i < 4; i++) {
                int slot = (start + i) % 4, t = r.nextFloat() < (hard ? .6f : .25f) ? 1 : 0;
                m.equipment[slot] = 256 + Equipment.ARMOR[t][slot];
                if (r.nextFloat() >= (hard ? .5f : .25f))
                    break;
            }
        }
        if (m.getClass() == Zombie.class && r.nextFloat() < (hard ? .25f : .1f))
            m.equipment[4] = 256 + Equipment.SWORDS[hard ? 2 : 1];
    }
}
