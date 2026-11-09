// MobEggColors.java
package net.classicremastered.minecraft.level.itemstack;

import java.util.HashMap;
import java.util.Map;

public final class MobEggColors {
    public static class EggColor {
        public final int base, spots;

        EggColor(int base, int spots) {
            this.base = base;
            this.spots = spots;
        }
    }

    private static final Map<String, EggColor> colors = new HashMap<>();

    static {
        colors.put("Zombie", new EggColor(0x00AFAF, 0x799C65)); // green base, gray spots
        colors.put("Skeleton", new EggColor(0xC1C1C1, 0x494949)); // white/gray
        colors.put("Creeper", new EggColor(0x0DA70B, 0x000000)); // green/black
        colors.put("Spider", new EggColor(0x342D27, 0xA80E0E)); // brown/red
        colors.put("Pig", new EggColor(0xF0A5A2, 0xDB635F)); // pink/red
        colors.put("Sheep", new EggColor(0xFFFFFF, 0xEAEAEA)); // white/light gray
        colors.put("Chicken", new EggColor(0xA1A1A1, 0xFF0000)); // gray/red comb
        colors.put("Ironmock", new EggColor(0xB8B8B8, 0xB32020));
        colors.put("ZombieBuilder", new EggColor(0x799C65, 0x888888));
        colors.put("Enderman", new EggColor(0x161616, 0xCC00FA));
        colors.put("Husk", new EggColor(0x797061, 0xE6CC94));
        colors.put("Cow", new EggColor(0x443626, 0xA1A1A1));
        colors.put("Bee", new EggColor(0xEDC343, 0x43241B));
        colors.put("IronGolem", new EggColor(0xD8D5C4, 0x628544));
        colors.put("Villager", new EggColor(0x563C33, 0xBD8B72));
        colors.put("BabyVillager", new EggColor(0x896348, 0xECC7A4));
        colors.put("BabyZombie", new EggColor(0x328B79, 0xC5DD72));
        colors.put("BabySkeleton", new EggColor(0xDFDCCD, 0x747B86));
        colors.put("TNTThrower", new EggColor(0xBD3428, 0xDAD9CE));
        colors.put("ScaryMindZombie", new EggColor(0x342744, 0xC0334B));
        colors.put("SignMob", new EggColor(0xAD8953, 0x38240E));
        colors.put("LeggedMonster", new EggColor(0x40505D, 0xB5CE79));
        colors.put("LongNeckMan", new EggColor(0x23212C, 0xBEAE96));
        colors.put("PlayerClone", new EggColor(0x248B9C, 0x70533C));
        colors.put("BlueForestGolem", new EggColor(0x23425C, 0x51DFD9));
        colors.put("HouseVandalizer", new EggColor(0x533A32, 0xD28E45));
        colors.put("StickFigure", new EggColor(0x171717, 0xEEEEEE));
        colors.put("ShulkerMan", new EggColor(0x7954A2, 0xDAC9AA));
        colors.put("Rana", new EggColor(0x68A348, 0xE5B4A0));
    }

    public static EggColor get(String mobName) {
        for (Map.Entry<String, EggColor> entry : colors.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(mobName)) return entry.getValue();
        }
        return new EggColor(0xAAAAAA, 0x555555);
    }

    public static String texture(String mobName) {
        String path =
                "/items/spawn_eggs/"
                        + (mobName == null ? "unknown" : mobName.toLowerCase(java.util.Locale.ROOT))
                        + ".png";
        return MobEggColors.class.getResource(path) == null
                ? "/items/spawn_eggs/unknown.png"
                : path;
    }
}
