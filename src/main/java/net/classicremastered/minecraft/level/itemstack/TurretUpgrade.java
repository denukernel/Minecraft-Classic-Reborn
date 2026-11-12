package net.classicremastered.minecraft.level.itemstack;

/** The order is the persisted upgrade bit index. Append new entries. */
public enum TurretUpgrade {
    HEALTH_I(53, "Health I", "+25% max health"),
    HEALTH_II(54, "Health II", "+50% max health", HEALTH_I),
    HEALTH_III(55, "Health III", "+75% max health", HEALTH_II),
    HEALTH_IV(56, "Health IV", "+100% max health", HEALTH_III),
    RELOAD_I(57, "Reload Time I", "15% faster reload"),
    RELOAD_II(58, "Reload Time II", "50% faster reload", RELOAD_I),
    STORAGE(59, "Ammunition Storage", "Doubles ammunition capacity"),
    RANGE_I(70, "Long Range I", "+25% targeting range"),
    RANGE_II(71, "Long Range II", "+50% targeting range", RANGE_I),
    ARMOR_I(84, "Impact Armor I", "20% less damage from attacks and blasts"),
    ARMOR_II(85, "Impact Armor II", "40% less damage from attacks and blasts", ARMOR_I),
    POWER_I(86, "Projectile Power I", "+1 damage per damaging projectile"),
    POWER_II(87, "Projectile Power II", "+2 damage per damaging projectile", POWER_I),
    RECYCLER_I(88, "Ammo Recycler I", "25% chance to save ammunition per shot"),
    RECYCLER_II(89, "Ammo Recycler II", "50% chance to save ammunition per shot", RECYCLER_I),
    TRACKING_I(90, "Fast Tracking I", "50% faster aiming"),
    TRACKING_II(91, "Fast Tracking II", "100% faster aiming", TRACKING_I),
    REPAIR(92, "Field Repair", "Repairs one health every five seconds"),
    FIRE_SEAL(93, "Fire Seal", "Blocks fire and lava damage"),
    WATER_SEAL(94, "Water Seal", "Prevents drowning"),
    SHOCK_ABSORBER(95, "Shock Absorber", "Prevents fall damage");

    private static final TurretUpgrade[] UPGRADES = values();
    public static final int COUNT = UPGRADES.length;
    public static final int SAVE_MASK = (1 << COUNT) - 1;

    public final int item;
    public final String label;
    public final String effect;
    public final TurretUpgrade previous;

    TurretUpgrade(int item, String label, String effect) {
        this(item, label, effect, null);
    }

    TurretUpgrade(int item, String label, String effect, TurretUpgrade previous) {
        this.item = item;
        this.label = label;
        this.effect = effect;
        this.previous = previous;
    }

    public static TurretUpgrade at(int index) {
        return index >= 0 && index < COUNT ? UPGRADES[index] : null;
    }

    public static TurretUpgrade fromItem(int raw) {
        for (TurretUpgrade upgrade : UPGRADES) {
            if (upgrade.item + 256 == raw)
                return upgrade;
        }
        return null;
    }

    public String description() {
        return previous == null ? effect : effect + " | Requires " + previous.label;
    }
}
