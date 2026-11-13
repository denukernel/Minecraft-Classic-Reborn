package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.MobWandItem;
import net.classicremastered.minecraft.level.itemstack.PackedTurret;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.itemstack.TurretUpgrade;
import net.classicremastered.minecraft.mob.TurretVariant;
import org.lwjgl.opengl.GL11;

final class ItemTooltip {
    private ItemTooltip() {}

    static void draw(GuiScreen screen, int raw, PackedTurret crate, int x, int y) {
        draw(screen, raw, crate, 0, x, y);
    }

    static void draw(GuiScreen screen, int raw, PackedTurret crate, int wear, int x, int y) {
        String name = BlockSelectScreen.displayName(raw);
        String detail = description(raw, crate);
        String durability = durabilityText(raw, wear);
        var font = screen.fontRenderer;
        int width =
                Math.max(
                        font.getWidth(durability),
                        Math.max(font.getWidth(name), font.getWidth(detail)));
        int height = 11 + (detail.isEmpty() ? 0 : 12) + (durability.isEmpty() ? 0 : 12);
        int left = Math.max(3, Math.min(screen.width - width - 5, x + 9));
        int top = Math.max(3, Math.min(screen.height - height - 4, y + 10));
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Screen.drawBox(left - 3, top - 3, left + width + 3, top + height, 0xF0100010);
            Screen.drawString(font, name, left, top, 0xFFFF80);
            if (!detail.isEmpty()) Screen.drawString(font, detail, left, top + 12, 0xFFFFFF);
            if (!durability.isEmpty())
                Screen.drawString(font, durability, left, top + height - 11, 0xFFFFFF);
        } finally {
            GL11.glPopAttrib();
        }
    }

    static String durabilityText(int raw, int wear) {
        var gear = Equipment.gear(raw);
        if (gear == null || gear.maxDurability <= 0) return "";
        int remaining = gear.maxDurability - Equipment.armorWear(raw, wear);
        return "Durability " + remaining + "/" + gear.maxDurability;
    }

    static String description(int raw, PackedTurret crate) {
        if (crate != null) {
            return TurretVariant.fromSave(crate.variantId()).label + " | HP " + crate.health()
                    + " | Ammo " + crate.ammo() + (crate.enabled() ? " | On" : " | Off");
        }
        int id = raw - 256;
        var variant = TurretVariant.fromItem(raw);
        if (variant != null && Item.items[variant.ammo] != null) {
            return "Uses " + Item.items[variant.ammo].name;
        }
        TurretUpgrade upgrade = TurretUpgrade.fromItem(raw);
        if (upgrade != null)
            return upgrade.description();
        if (id == TurretItems.BOLT)
            return "Crossbow, Predictable, and Rover turret ammunition";
        if (id == 64)
            return "Legacy Arrow Shotgun Turret ammunition";
        if (id == 98)
            return "Bullet and Bullet Shotgun turret ammunition";
        if (id == 65)
            return "Harpoon Turret ammunition";
        if (id == 66)
            return "Converts eligible hostiles into defenders";
        if (id >= 0 && id < Item.items.length && Item.items[id] instanceof MobWandItem wand)
            return wand.instructions();
        return Equipment.description(raw);
    }
}
