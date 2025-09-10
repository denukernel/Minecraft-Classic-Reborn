// MobEggColors.java
package net.classicremastered.minecraft.level.itemstack;

import java.util.HashMap;
import java.util.Map;

public final class MobEggColors {
    public static class EggColor {
        public final int base, spots;
        EggColor(int base, int spots) { this.base = base; this.spots = spots; }
    }

    private static final Map<String, EggColor> colors = new HashMap<>();

    static {
        colors.put("Zombie",   new EggColor(0x00AFAF, 0x799C65)); // green base, gray spots
        colors.put("Skeleton", new EggColor(0xC1C1C1, 0x494949)); // white/gray
        colors.put("Creeper",  new EggColor(0x0DA70B, 0x000000)); // green/black
        colors.put("Spider",   new EggColor(0x342D27, 0xA80E0E)); // brown/red
        colors.put("Pig",      new EggColor(0xF0A5A2, 0xDB635F)); // pink/red
        colors.put("Sheep",    new EggColor(0xFFFFFF, 0xEAEAEA)); // white/light gray
        colors.put("Chicken",  new EggColor(0xA1A1A1, 0xFF0000)); // gray/red comb
        // add more as you like
    }

    public static EggColor get(String mobName) {
        return colors.getOrDefault(mobName, new EggColor(0xAAAAAA, 0x555555)); // default gray
    }
}
