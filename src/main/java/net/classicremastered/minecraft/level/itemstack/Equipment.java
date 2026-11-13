package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.*;

import java.util.*;

/** Stable equipment definitions. Armor slots: head, chest, legs, feet. */
public final class Equipment {
    private static final int[] ARMOR_SLOT_DURABILITY = {11, 16, 15, 13};
    public static final Map<Integer, Gear> GEAR = new LinkedHashMap<>();
    public static Item STICK, LEATHER, STRING, ARROW;
    public static final int[] SWORDS = {21, 22, 2, 38, 39}, AXES = {23, 24, 25, 40, 41};
    public static final int DIAMOND_SHOVEL = 42, GOLDEN_SHOVEL = 43;
    public static final int[] PICKAXES = {211, 212, 3, 213, 214};
    public static final int[] SHOVELS = {215, 216, 4, DIAMOND_SHOVEL, GOLDEN_SHOVEL};
    public static final int[][] ARMOR = {
        {26, 27, 28, 29},
        {30, 31, 32, 33},
        {200, 201, 202, 203},
        {204, 205, 206, 207},
        {217, 218, 219, 220},
        {221, 222, 223, 224}
    };

    private Equipment() {}

    public static final class Gear extends ToolItem {
        public final int damage, slot, protection, price;
        public final String armorMaterial;
        public final int maxDurability;

        Gear(
                int id,
                String name,
                String texture,
                int damage,
                int slot,
                int protection,
                int price,
                String armorMaterial) {
            super(id, name, texture);
            this.damage = damage;
            this.slot = slot;
            this.protection = protection;
            this.price = price;
            this.armorMaterial = armorMaterial;
            this.maxDurability =
                    slot < 0
                            ? 0
                            : ARMOR_SLOT_DURABILITY[slot]
                                    * switch (armorMaterial) {
                                        case "wood" -> 3;
                                        case "leather" -> 5;
                                        case "gold" -> 7;
                                        case "stone" -> 9;
                                        case "iron" -> 15;
                                        case "diamond" -> 33;
                                        default ->
                                                throw new IllegalArgumentException(
                                                        "Unknown armor material: " + armorMaterial);
                                    };
            GEAR.put(id, this);
        }

        @Override
        public void use(Player p, Level level) {
            if (slot < 0 || level.isNetworkMode()) return;
            int selected = p.inventory.selected;
            if (p.inventory.getSelected() != id + 256) return;
            int previous = p.equipment[slot];
            int previousWear = wear(p, slot);
            int wear = p.inventory.armorWear(selected);
            if (!level.creativeMode) {
                int count = p.inventory.getCount(selected);
                if (count > 1) {
                    if (previous > 0 && !p.inventory.addResource(previous, previousWear)) return;
                    p.inventory.removeSelected(1);
                } else {
                    p.inventory.setIdCount(selected, previous, previous > 0 ? 1 : 0);
                    p.inventory.setArmorWear(selected, previousWear);
                }
            }
            equip(p, slot, id + 256, wear);
        }
    }

    public static void register() {
        String[] tiers = {"Wood", "Stone", "Iron"};
        int[] swordPrices = {4, 8, 15}, axePrices = {3, 7, 12};
        for (int t = 0; t < 3; t++) {
            Item sword =
                    new Gear(
                            SWORDS[t],
                            tiers[t] + " Sword",
                            "/items/gear/sword_" + t + ".png",
                            4 + t,
                            -1,
                            0,
                            swordPrices[t],
                            null);
            if (t == 2) Item.SWORD = sword;
            new Gear(
                            AXES[t],
                            tiers[t] + " Axe",
                            "/items/gear/"
                                    + new String[] {"wooden", "stone", "iron"}[t]
                                    + "_axe.png",
                            3 + t,
                            -1,
                            0,
                            axePrices[t],
                            null)
                    .mining(ToolItem.Kind.AXE, ToolItem.Material.values()[t]);
        }
        new Gear(SWORDS[3], "Diamond Sword", "/items/gear/diamond_sword.png", 7, -1, 0, 30, null);
        new Gear(SWORDS[4], "Golden Sword", "/items/gear/golden_sword.png", 4, -1, 0, 12, null);
        new Gear(AXES[3], "Diamond Axe", "/items/gear/diamond_axe.png", 9, -1, 0, 28, null)
                .mining(ToolItem.Kind.AXE, ToolItem.Material.DIAMOND);
        new Gear(AXES[4], "Golden Axe", "/items/gear/golden_axe.png", 7, -1, 0, 10, null)
                .mining(ToolItem.Kind.AXE, ToolItem.Material.GOLDEN);
        new Gear(
                        DIAMOND_SHOVEL,
                        "Diamond Shovel",
                        "/items/gear/diamond_shovel.png",
                        5,
                        -1,
                        0,
                        18,
                        null)
                .mining(ToolItem.Kind.SHOVEL, ToolItem.Material.DIAMOND);
        new Gear(GOLDEN_SHOVEL, "Golden Shovel", "/items/gear/golden_shovel.png", 3, -1, 0, 7, null)
                .mining(ToolItem.Kind.SHOVEL, ToolItem.Material.GOLDEN);
        String[] toolNames = {"Wooden", "Stone", "Iron", "Diamond", "Golden"};
        String[] textures = {"wooden", "stone", "iron", "diamond", "golden"};
        for (int tier = 0; tier < 5; tier++) {
            ToolItem.Material material = ToolItem.Material.values()[tier];
            if (tier != 2)
                new ToolItem(
                        PICKAXES[tier],
                        toolNames[tier] + " Pickaxe",
                        "/items/gear/" + textures[tier] + "_pickaxe.png",
                        ToolItem.Kind.PICKAXE,
                        material);
            if (tier < 2)
                new ToolItem(
                        SHOVELS[tier],
                        toolNames[tier] + " Shovel",
                        "/items/gear/" + textures[tier] + "_shovel.png",
                        ToolItem.Kind.SHOVEL,
                        material);
        }
        String[] slots = {"Helmet", "Chestplate", "Leggings", "Boots"};
        String[] names = {"Leather", "Iron", "Golden", "Diamond", "Wood", "Stone"};
        String[] materials = {"leather", "iron", "gold", "diamond", "wood", "stone"};
        int[][] points = {
            {1, 3, 2, 1}, {2, 6, 5, 2}, {2, 5, 3, 1}, {3, 8, 6, 3}, {1, 1, 1, 1}, {1, 4, 3, 1}
        };
        int[][] prices = {
            {5, 10, 8, 4},
            {10, 24, 18, 8},
            {8, 16, 14, 7},
            {20, 40, 34, 18},
            {2, 4, 3, 2},
            {4, 8, 6, 3}
        };
        for (int t = 0; t < ARMOR.length; t++) {
            for (int s = 0; s < slots.length; s++) {
                String icon =
                        t < 2
                                ? "armor_" + t + "_" + s
                                : names[t].toLowerCase(Locale.ROOT)
                                        + "_"
                                        + slots[s].toLowerCase(Locale.ROOT);
                new Gear(
                        ARMOR[t][s],
                        names[t] + " " + slots[s],
                        "/items/gear/" + icon + ".png",
                        0,
                        s,
                        points[t][s],
                        prices[t][s],
                        materials[t]);
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
        float tool = miningToolDamage(raw);
        if (tool > 0) return Math.round(tool);
        Gear g = gear(raw);
        return g != null && g.damage > 0 ? g.damage : 3;
    }

    public static ToolItem miningTool(int raw) {
        int id = raw - 256;
        if (id >= 0
                && id < Item.items.length
                && Item.items[id] instanceof ToolItem tool
                && (tool.kind() == ToolItem.Kind.PICKAXE || tool.kind() == ToolItem.Kind.SHOVEL)) {
            return tool;
        }
        return null;
    }

    public static float miningToolDamage(int raw) {
        ToolItem tool = miningTool(raw);
        return tool == null ? 0 : tool.attackDamage();
    }

    public static int armorWear(int raw, int wear) {
        Gear gear = gear(raw);
        return gear == null || gear.maxDurability == 0
                ? 0
                : Math.max(0, Math.min(wear, gear.maxDurability - 1));
    }

    public static int wear(Mob mob, int slot) {
        return mob.armorWear == null || slot >= mob.armorWear.length
                ? 0
                : armorWear(mob.equipment[slot], mob.armorWear[slot]);
    }

    public static void equip(Mob mob, int slot, int raw, int wear) {
        mob.equipment[slot] = raw;
        if (slot >= 4) return;
        if (mob.armorWear == null) mob.armorWear = new int[4];
        mob.armorWear[slot] = armorWear(raw, wear);
    }

    public static void damageArmor(Mob mob, int damage) {
        if (damage <= 0
                || mob.equipment == null
                || mob.level == null
                || mob.level.isNetworkMode()) {
            return;
        }
        int wear = Math.max(1, damage / 4);
        boolean broke = false;
        for (int slot = 0; slot < 4; slot++) {
            int raw = mob.equipment[slot];
            Gear gear = gear(raw);
            if (gear == null || gear.slot != slot) continue;
            int next = wear(mob, slot) + wear;
            if (next >= gear.maxDurability) {
                equip(mob, slot, 0, 0);
                broke = true;
            } else {
                equip(mob, slot, raw, next);
            }
        }
        if (broke) mob.level.playSound("random/break", mob, 0.8f, 1.0f);
    }

    public static int points(Mob m) {
        if (m.equipment == null) return 0;
        int p = 0;
        for (int s = 0; s < 4; s++) {
            Gear g = gear(m.equipment[s]);
            if (g != null && g.slot == s) p += g.protection;
        }
        return p;
    }

    public static String description(int raw) {
        float tool = miningToolDamage(raw);
        if (tool > 0) {
            String damage = tool == (int) tool ? Integer.toString((int) tool) : Float.toString(tool);
            return damage + " attack damage";
        }
        Gear g = gear(raw);
        return g == null
                ? ""
                : g.slot >= 0 ? g.protection + " armor points" : g.damage + " attack damage";
    }

    public static int reduce(Mob m, int damage) {
        int numerator = damage * (100 - 4 * points(m)) + m.armorRemainder;
        m.armorRemainder = numerator % 100;
        return numerator / 100;
    }

    public static void save(Mob m, CompoundTag tag) {
        CompoundTag e = new CompoundTag("Equipment");
        for (int s = 0; s < 5; s++) {
            e.putInt("Slot" + s, m.equipment[s]);
            int wear = wear(m, s);
            if (wear > 0) e.putInt("Wear" + s, wear);
        }
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
        if (m.equipment == null) m.equipment = new int[5];
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
            int equipped = g != null && (s == 4 ? g.slot < 0 : g.slot == s) ? raw : 0;
            equip(m, s, equipped, e.getInt("Wear" + s));
        }
        m.equipmentRolled = true;
        m.armorRemainder = Math.max(0, Math.min(99, e.getInt("Remainder")));
    }
}
