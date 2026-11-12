package net.classicremastered.minecraft.level.itemstack;

import java.util.*;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.nbt.*;

/** Stable equipment definitions. Armor slots: head, chest, legs, feet. */
public final class Equipment {
    public static final Map<Integer, Gear> GEAR = new LinkedHashMap<>();
    public static Item STICK, LEATHER, STRING, ARROW;
    public static final int[] SWORDS = {21, 22, 2, 38, 39}, AXES = {23, 24, 25, 40, 41};
    public static final int DIAMOND_SHOVEL = 42, GOLDEN_SHOVEL = 43;
    public static final int[][] ARMOR = { { 26, 27, 28, 29 }, { 30, 31, 32, 33 },
            { 200, 201, 202, 203 }, { 204, 205, 206, 207 } };

    private Equipment() {
    }
    public static final class Gear extends ToolItem {
        public final int damage, slot, protection, price;
        public final String armorMaterial;

        Gear(int id, String name, String texture, int damage, int slot, int protection, int price,
                String armorMaterial) {
            super(id, name, texture);
            this.damage = damage;
            this.slot = slot;
            this.protection = protection;
            this.price = price;
            this.armorMaterial = armorMaterial;
            GEAR.put(id, this);
        }

        @Override
        public void use(Player p, Level level) {
            if (slot < 0 || level.isNetworkMode())
                return;
            int selected = p.inventory.selected;
            if (p.inventory.getSelected() != id + 256)
                return;
            int previous = p.equipment[slot];
            p.equipment[slot] = id + 256;
            if (!level.creativeMode)
                p.inventory.setIdCount(selected, previous, previous > 0 ? 1 : 0);
        }
    }
    public static void register() {
        String[] tiers = {"Wood", "Stone", "Iron"};
        int[] swordPrices = {4, 8, 15}, axePrices = {3, 7, 12};
        for (int t = 0; t < 3; t++) {
            Item sword = new Gear(SWORDS[t], tiers[t] + " Sword", "/items/gear/sword_" + t + ".png", 4 + t, -1, 0,
                    swordPrices[t], null);
            if (t == 2)
                Item.SWORD = sword;
            new Gear(AXES[t], tiers[t] + " Axe", "/items/gear/axe_" + t + ".png", 3 + t, -1, 0,
                    axePrices[t], null);
        }
        new Gear(SWORDS[3], "Diamond Sword", "/items/gear/diamond_sword.png", 7, -1, 0, 30, null);
        new Gear(SWORDS[4], "Golden Sword", "/items/gear/golden_sword.png", 4, -1, 0, 12, null);
        new Gear(AXES[3], "Diamond Axe", "/items/gear/diamond_axe.png", 9, -1, 0, 28, null);
        new Gear(AXES[4], "Golden Axe", "/items/gear/golden_axe.png", 7, -1, 0, 10, null);
        new Gear(DIAMOND_SHOVEL, "Diamond Shovel", "/items/gear/diamond_shovel.png", 5, -1, 0, 18,
                null);
        new Gear(GOLDEN_SHOVEL, "Golden Shovel", "/items/gear/golden_shovel.png", 3, -1, 0, 7, null);
        String[] slots = {"Helmet", "Chestplate", "Leggings", "Boots"};
        String[] names = { "Leather", "Iron", "Golden", "Diamond" };
        String[] materials = { "leather", "iron", "gold", "diamond" };
        int[][] points = { { 1, 3, 2, 1 }, { 2, 6, 5, 2 }, { 2, 5, 3, 1 }, { 3, 8, 6, 3 } };
        int[][] prices = { { 5, 10, 8, 4 }, { 10, 24, 18, 8 }, { 8, 16, 14, 7 }, { 20, 40, 34, 18 } };
        for (int t = 0; t < ARMOR.length; t++) {
            for (int s = 0; s < slots.length; s++) {
                String icon = t < 2 ? "armor_" + t + "_" + s
                        : names[t].toLowerCase(Locale.ROOT) + "_" + slots[s].toLowerCase(Locale.ROOT);
                new Gear(ARMOR[t][s], names[t] + " " + slots[s], "/items/gear/" + icon + ".png", 0, s,
                        points[t][s], prices[t][s], materials[t]);
            }
        }
        STICK = new Item(34, "Stick", "/items/gear/stick.png");
        LEATHER = new Item(35, "Leather", "/items/gear/leather.png");
        STRING = new Item(36, "String", "/items/gear/string.png");
        ARROW = new Item(37, "Arrow", "/items/gear/arrows.png");
    }

    public static Gear gear(int raw) {
        return GEAR.get(raw - 256);
    }

    public static int damage(int raw) {
        Gear g = gear(raw);
        return g != null && g.damage > 0 ? g.damage : 3;
    }

    public static int points(Mob m) {
        if (m.equipment == null)
            return 0;
        int p = 0;
        for (int s = 0; s < 4; s++) {
            Gear g = gear(m.equipment[s]);
            if (g != null && g.slot == s)
                p += g.protection;
        }
        return p;
    }

    public static String description(int raw) {
        Gear g = gear(raw);
        return g == null ? "" : g.slot >= 0 ? g.protection + " armor points" : g.damage + " attack damage";
    }

    public static int reduce(Mob m, int damage) {
        int numerator = damage * (100 - 4 * points(m)) + m.armorRemainder;
        m.armorRemainder = numerator % 100;
        return numerator / 100;
    }

    public static void save(Mob m, CompoundTag tag) {
        CompoundTag e = new CompoundTag("Equipment");
        for (int s = 0; s < 5; s++)
            e.putInt("Slot" + s, m.equipment[s]);
        e.putInt("Legacy", m.legacyArmor ? 1 : 0);
        if (m instanceof net.classicremastered.minecraft.mob.HumanoidMob h) {
            e.putInt("Helmet", h.helmet ? 1 : 0);
            e.putInt("Chest", h.armor ? 1 : 0);
        }
        e.putInt("Rolled", m.equipmentRolled ? 1 : 0);
        e.putInt("Remainder", m.armorRemainder);
        tag.put(e);
    }

    public static void load(Mob m, CompoundTag tag) {
        ItemRegistry.bootstrap();
        if (m.equipment == null)
            m.equipment = new int[5];
        if (tag == null || !tag.hasKey("Equipment")) {
            m.equipmentRolled = true;
            m.legacyArmor = true;
            return;
        }
        CompoundTag e = tag.getCompound("Equipment");
        m.legacyArmor = e.getInt("Legacy") != 0;
        if (m.legacyArmor && m instanceof net.classicremastered.minecraft.mob.HumanoidMob h) {
            h.helmet = e.getInt("Helmet") != 0;
            h.armor = e.getInt("Chest") != 0;
        }
        for (int s = 0; s < 5; s++) {
            int raw = e.getInt("Slot" + s);
            Gear g = gear(raw);
            m.equipment[s] = g != null && (s == 4 ? g.slot < 0 : g.slot == s) ? raw : 0;
        }
        m.equipmentRolled = true;
        m.armorRemainder = Math.max(0, Math.min(99, e.getInt("Remainder")));
    }
}
