package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.mob.Companion;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Defender.Skill;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.WorkerModel;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.render.EquipmentRenderer;

import org.lwjgl.opengl.GL11;

public final class DefenderEquipmentScreen extends InventoryScreen {
    private static final int EQUIPMENT_START = 36;
    private static final String[] LABELS = {"Head", "Chest", "Legs", "Feet", "Weapon"};
    private static final int PACK_START = EQUIPMENT_START + LABELS.length;
    private static final int PANEL_WIDTH = 312;
    private static final int PANEL_HEIGHT = 232;
    private static final int LABEL_COLOR = 0xFF404040;
    private final Companion companion;
    private final Mob mob;
    private final Defender defender;
    private final HumanoidModel preview;
    private String message = "Drag gear or stacks to transfer.";
    private int pressedSlot = -1;

    public DefenderEquipmentScreen(Companion companion) {
        this.companion = companion;
        mob = companion.mob();
        defender = mob instanceof Defender fighter ? fighter : null;
        preview = defender == null ? new WorkerModel() : new HumanoidModel();
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        onResize();
    }

    @Override
    protected void onResize() {
        buttons.clear();
        buttons.add(new Button(0, left() + 237, top() + 207, 65, 20, "Done"));
        if (defender == null)
            return;
        for (Skill skill : Skill.values()) {
            buttons.add(new Button(skill.ordinal() + 1, left() + 196, top() + 86 + skill.ordinal() * 20, 106,
                    19, ""));
        }
    }

    @Override
    public void tick() {
        if (!companion.canControl(minecraft.player))
            minecraft.setCurrentScreen(null);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 0) {
            minecraft.setCurrentScreen(null);
            return;
        }
        Skill skill = Skill.values()[button.id - 1];
        message = defender.upgrade(minecraft.player, skill)
                ? skill.label + " upgraded."
                : defender.skillMaxed(skill) ? "Maximum rank reached." : "You need more coins.";
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button != 0 || !companion.canControl(minecraft.player))
            return;
        pressedSlot = -1;
        int slot = equipmentInventorySlotAt(mx, my);
        if (slot < 0) {
            for (Object object : buttons) {
                Button control = (Button) object;
                if (inside(mx, my, control.x, control.y, control.width, control.height)) {
                    playButtonClickSound();
                    onButtonClick(control);
                    return;
                }
            }
        } else if (hasCarriedStack()) {
            if (!isEquipment(slot))
                placeCarriedStackInSlot(inventory(slot), inventoryIndex(slot));
        } else {
            pressedSlot = slot;
        }
    }

    @Override
    protected void onMouseRelease(int mx, int my, int button) {
        if (button != 0)
            return;
        int source = pressedSlot;
        pressedSlot = -1;
        int target = equipmentInventorySlotAt(mx, my);
        if (source < 0 || target < 0 || !companion.canControl(minecraft.player))
            return;

        if (source == target) {
            if (isEquipment(source)) {
                message = companion.unequip(minecraft.player, source - EQUIPMENT_START)
                        ? "Equipment returned."
                        : "Empty slot or inventory full.";
            } else if (source < EQUIPMENT_START && Defender.equipmentSlot(stackId(source)) >= 0) {
                equip(source);
            } else {
                pickUpAll(inventory(source), inventoryIndex(source));
            }
        } else if (!isEquipment(source) && !isEquipment(target)) {
            pickUpAll(inventory(source), inventoryIndex(source));
            placeCarriedStackInSlot(inventory(target), inventoryIndex(target));
        } else if (!isEquipment(source)) {
            if (Defender.equipmentSlot(stackId(source)) == target - EQUIPMENT_START) {
                equip(source);
            } else {
                message = "Choose matching gear.";
            }
        } else if (!isEquipment(target)) {
            moveEquipmentToInventory(source - EQUIPMENT_START, target);
        }
    }

    private void equip(int slot) {
        message = companion.equip(minecraft.player, inventory(slot), inventoryIndex(slot))
                ? "Equipment updated."
                : "Choose gear and leave room for swaps.";
    }

    private void moveEquipmentToInventory(int gear, int target) {
        var inventory = inventory(target);
        target = inventoryIndex(target);
        int equipped = mob.equipment[gear];
        if (equipped <= 0)
            return;
        int destination = inventory.getId(target);
        int count = inventory.getCount(target);
        if (count > 0 && (count != 1 || Defender.equipmentSlot(destination) != gear)) {
            message = "Choose an empty slot or matching gear.";
            return;
        }
        int incomingWear = inventory.armorWear(target);
        inventory.setIdCount(target, equipped, 1);
        inventory.setArmorWear(target, Equipment.wear(mob, gear));
        Equipment.equip(mob, gear, count == 0 ? 0 : destination, incomingWear);
        message = "Equipment moved.";
    }

    private static boolean isEquipment(int slot) {
        return slot >= EQUIPMENT_START && slot < PACK_START;
    }

    private Inventory inventory(int slot) {
        return slot >= PACK_START ? companion.pack() : minecraft.player.inventory;
    }

    private static int inventoryIndex(int slot) {
        return slot >= PACK_START ? slot - PACK_START : slot;
    }

    private int stackId(int slot) {
        return isEquipment(slot)
                ? mob.equipment[slot - EQUIPMENT_START]
                : inventory(slot).getId(inventoryIndex(slot));
    }

    private int equipmentInventorySlotAt(int mx, int my) {
        for (int slot = 0; slot < LABELS.length; slot++) {
            if (inside(mx, my, left() + 12 + slot * 36, top() + 51, 18, 18))
                return EQUIPMENT_START + slot;
        }
        for (int slot = 0; slot < Companion.PACK_SIZE; slot++) {
            if (inside(mx, my, left() + 12 + slot * 20, top() + 89, 18, 18))
                return PACK_START + slot;
        }
        int limit = Math.min(EQUIPMENT_START, minecraft.player.inventory.totalSize());
        for (int slot = 0; slot < limit; slot++) {
            if (inside(mx, my, left() + 12 + slot % 9 * 20, top() + 127 + slot / 9 * 20, 18, 18))
                return slot;
        }
        return -1;
    }

    @Override
    public void onClose() {
        pressedSlot = -1;
        super.onClose();
    }

    private void slot(int raw, int count, int wear, int x, int y) {
        drawBox(x - 1, y - 1, x + 18, y + 18, 0xFFFFFFFF);
        drawBox(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        drawBox(x, y, x + 17, y + 17, 0xFF8B8B8B);
        if (raw > 0 && count > 0) {
            drawIdIcon(raw, x, y);
            drawDurabilityBar(raw, wear, x, y);
            drawStackCount(count, x, y);
        }
    }

    @Override
    public void render(int mx, int my) {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
        int left = left(), top = top(), right = left + PANEL_WIDTH, bottom = top + PANEL_HEIGHT;
        drawBox(0, 0, width, height, 0xA0000000);
        drawBox(left, top, right, bottom, 0xFF171717);
        drawBox(left + 1, top + 1, right - 1, bottom - 1, 0xFF686868);
        drawBox(left + 2, top + 2, right - 3, bottom - 3, 0xFFFFFFFF);
        drawBox(left + 3, top + 3, right - 3, bottom - 3, 0xFFC6C6C6);
        label(mob.customName == null ? MobRegistry.displayName(mob.getRegistryId()) : mob.customName,
                left + 12, top + 10);
        label("Health " + mob.health + " / " + companion.maxHealth(), left + 12, top + 25);
        for (int s = 0; s < LABELS.length; s++) {
            int x = left() + 12 + s * 36;
            label(defender == null && s == LABELS.length - 1 ? "Tool" : LABELS[s], x, top + 39);
            slot(mob.equipment[s], 1, Equipment.wear(mob, s), x, top + 51);
        }
        label("Companion pack", left + 12, top + 77);
        drawInventory(companion.pack(), Companion.PACK_SIZE, 89);
        label("Your inventory", left + 12, top + 115);
        drawInventory(minecraft.player.inventory, EQUIPMENT_START, 127);
        label("Armor " + Equipment.points(mob), left + 114, top + 25);
        drawBox(left + 196, top + 8, right - 10, top + 69, 0xFF737373);
        drawBox(left + 197, top + 9, right - 11, top + 68, 0xFF343434);
        drawPreview();
        if (defender != null) {
            label("Skills", left + 196, top + 74);
            String coins = minecraft.player.coins + " coins";
            label(coins, right - 11 - fontRenderer.getWidth(coins), top + 74);
        } else {
            label("Work status", left + 196, top + 76);
            drawWrapped(companion.activity(), left + 196, top + 89, 105);
        }
        String status = defender != null && equipmentInventorySlotAt(mx, my) >= PACK_START
                ? "Pack food, arrows, water, or ender pearls."
                : message;
        for (Object object : buttons) {
            Button b = (Button) object;
            boolean hovered = inside(mx, my, b.x, b.y, b.width, b.height);
            String label = b.text;
            String detail = null;
            boolean available = true;
            if (b.id != 0) {
                Skill skill = Skill.values()[b.id - 1];
                int rank = defender.skillLevel(skill);
                int cost = defender.upgradeCost(skill);
                boolean maxed = defender.skillMaxed(skill);
                available = !maxed && (cost == 0 || minecraft.player.coins >= cost);
                boolean creative = minecraft.level.creativeMode;
                int limit = defender.skillLimit(skill);
                String rankLabel = limit == 1 && !maxed
                        ? ""
                        : " " + (limit == Integer.MAX_VALUE || rank > limit
                                ? Integer.toString(rank)
                                : rank + "/" + limit);
                label = skill.label;
                detail = (rankLabel + (maxed ? "" : creative ? "  Free" : "  " + cost + "c")).trim();
                if (hovered)
                    status = skill.upgradeDescription(maxed ? rank - 1 : rank);
            }
            drawBox(b.x, b.y, b.x + b.width, b.y + b.height, 0xFF181818);
            drawBox(b.x + 1, b.y + 1, b.x + b.width - 1, b.y + b.height - 1,
                    available ? 0xFF9BAD91 : 0xFF999999);
            drawBox(b.x + 2, b.y + 2, b.x + b.width - 2, b.y + b.height - 2,
                    available ? hovered ? 0xFF569743 : 0xFF447A35 : 0xFF686868);
            drawCenteredString(fontRenderer, label, b.x + b.width / 2, b.y + (detail == null ? 6 : 2),
                    available ? 0xFFFFFF : 0xD6D6D6);
            if (detail != null) {
                drawCenteredString(fontRenderer, detail, b.x + b.width / 2, b.y + 11, 0xFFE6A0);
            }
        }
        drawWrapped(status, left + 12, top + 210, 219);
        if (hasCarriedStack()) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            renderCarriedStack(mx, my);
        } else if (pressedSlot >= 0 && equipmentInventorySlotAt(mx, my) != pressedSlot) {
            int id = stackId(pressedSlot);
            if (id > 0) {
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                drawRecipeItem(id,
                        isEquipment(pressedSlot)
                                ? 1
                                : inventory(pressedSlot).getCount(inventoryIndex(pressedSlot)),
                        mx - 8, my - 8);
            }
        } else {
            int hovered = equipmentInventorySlotAt(mx, my);
            if (isEquipment(hovered)) {
                int slot = hovered - EQUIPMENT_START;
                int raw = mob.equipment[slot];
                if (raw > 0)
                    ItemTooltip.draw(this, raw, null, Equipment.wear(mob, slot), mx, my);
            } else if (hovered >= 0) {
                var inventory = inventory(hovered);
                int index = inventoryIndex(hovered);
                if (inventory.getId(index) > 0) {
                    ItemTooltip.draw(this, inventory.getId(index), inventory.crate(index),
                            inventory.armorWear(index), mx, my);
                }
            }
        }
    }

    private void label(String text, int x, int y) {
        fontRenderer.renderNoShadow(text, x, y, LABEL_COLOR);
    }

    private void drawWrapped(String text, int x, int y, int maxWidth) {
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fontRenderer.getWidth(next) > maxWidth) {
                label(line, x, y);
                y += 10;
                next = word;
            }
            line = next;
        }
        label(line, x, y);
    }

    private void drawInventory(Inventory inventory, int limit, int startY) {
        for (int s = 0; s < Math.min(limit, inventory.totalSize()); s++) {
            slot(inventory.getId(s), inventory.getCount(s), inventory.armorWear(s), left() + 12 + s % 9 * 20,
                    top() + startY + s / 9 * 20);
        }
    }

    private void drawPreview() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            if (minecraft.renderer != null)
                minecraft.renderer.setLighting(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glTranslatef(left() + 250, top() + 35, 80);
            GL11.glScalef(20, 20, 20);
            GL11.glRotatef(defender != null && defender.heldItem() == Item.GRAVITYGUN.id + 256 ? 125 : 155, 0,
                    1, 0);
            mob.bindTexture(minecraft.textureManager);
            preview.preAnimate(mob);
            preview.attackOffset = 0;
            float pitch = defender != null && (defender.bowDrawTicks() >= 0 || defender.isUsingBucket()
                    || defender.isThrowingPearl()) ? defender.renderHeadPitch(1) : 0;
            preview.setRotationAngles(0, 0, 0, 0, pitch, .0625f);
            if (defender != null)
                defender.renderHandAction(preview, 0, pitch, .0625f, 1);
            else
                preview.render(0, 0, 1, 0, pitch, .0625f);
            EquipmentRenderer.armor(mob, preview, minecraft.textureManager, .0625f, 1);
            EquipmentRenderer.held(mob, preview, minecraft.textureManager, .0625f, 1);
        } finally {
            if (minecraft.renderer != null)
                minecraft.renderer.setLighting(false);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
