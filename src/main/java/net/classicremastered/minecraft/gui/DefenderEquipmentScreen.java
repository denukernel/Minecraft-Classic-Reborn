package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.render.EquipmentRenderer;

import org.lwjgl.opengl.GL11;

public final class DefenderEquipmentScreen extends InventoryScreen {
    private static final int EQUIPMENT_START = 36;
    private static final String[] LABELS = {"Head", "Chest", "Legs", "Feet", "Weapon"};
    private final Defender defender;
    private final HumanoidModel preview = new HumanoidModel();
    private String message = "Click or drag gear to equip.";
    private int pressedSlot = -1;

    public DefenderEquipmentScreen(Defender defender) {
        this.defender = defender;
    }

    private int left() {
        return (width - 300) / 2;
    }

    private int top() {
        return (height - 226) / 2;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        buttons.clear();
        buttons.add(new Button(0, left() + 225, top() + 201, 65, 20, "Done"));
    }

    @Override
    public void tick() {
        if (!defender.canControl(minecraft.player)) minecraft.setCurrentScreen(null);
    }

    @Override
    protected void onButtonClick(Button button) {
        minecraft.setCurrentScreen(null);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button != 0 || !defender.canControl(minecraft.player)) return;
        pressedSlot = -1;
        int slot = equipmentInventorySlotAt(mx, my);
        if (slot < 0) {
            for (Object object : buttons) {
                Button done = (Button) object;
                if (inside(mx, my, done.x, done.y, done.width, done.height)) {
                    playButtonClickSound();
                    onButtonClick(done);
                    return;
                }
            }
        } else if (hasCarriedStack()) {
            if (slot < EQUIPMENT_START) placeCarriedStackInSlot(slot);
        } else {
            pressedSlot = slot;
        }
    }

    @Override
    protected void onMouseRelease(int mx, int my, int button) {
        if (button != 0) return;
        int source = pressedSlot;
        pressedSlot = -1;
        int target = equipmentInventorySlotAt(mx, my);
        if (source < 0 || target < 0 || !defender.canControl(minecraft.player)) return;

        if (source == target) {
            if (source >= EQUIPMENT_START) {
                message =
                        defender.unequip(minecraft.player, source - EQUIPMENT_START)
                                ? "Equipment returned."
                                : "Empty slot or inventory full.";
            } else {
                equip(source);
            }
        } else if (source < EQUIPMENT_START && target < EQUIPMENT_START) {
            pickUpAll(minecraft.player.inventory, source);
            placeCarriedStackInSlot(target);
        } else if (source < EQUIPMENT_START) {
            if (gearSlot(minecraft.player.inventory.getId(source)) == target - EQUIPMENT_START) {
                equip(source);
            } else {
                message = "Choose matching gear.";
            }
        } else if (target < EQUIPMENT_START) {
            moveEquipmentToInventory(source - EQUIPMENT_START, target);
        }
    }

    private void equip(int inventorySlot) {
        message =
                defender.equip(minecraft.player, inventorySlot)
                        ? "Defender equipped."
                        : "Choose gear and leave room for swaps.";
    }

    private void moveEquipmentToInventory(int gear, int target) {
        var inventory = minecraft.player.inventory;
        int equipped = defender.equipment[gear];
        if (equipped <= 0) return;
        int destination = inventory.getId(target);
        int count = inventory.getCount(target);
        if (count > 0 && (count != 1 || gearSlot(destination) != gear)) {
            message = "Choose an empty slot or matching gear.";
            return;
        }
        inventory.setIdCount(target, equipped, 1);
        defender.equipment[gear] = count == 0 ? 0 : destination;
        message = "Equipment returned.";
    }

    private int gearSlot(int id) {
        var gear = Equipment.gear(id);
        return gear == null ? -1 : gear.slot >= 0 ? gear.slot : 4;
    }

    private int equipmentInventorySlotAt(int mx, int my) {
        for (int slot = 0; slot < LABELS.length; slot++) {
            if (inside(mx, my, left() + 12 + slot * 39, top() + 53, 18, 18))
                return EQUIPMENT_START + slot;
        }
        int limit = Math.min(EQUIPMENT_START, minecraft.player.inventory.totalSize());
        for (int slot = 0; slot < limit; slot++) {
            if (inside(mx, my, left() + 12 + slot % 9 * 20, top() + 100 + slot / 9 * 22, 18, 18))
                return slot;
        }
        return -1;
    }

    @Override
    public void onClose() {
        pressedSlot = -1;
        super.onClose();
    }

    private void slot(int raw, int count, int x, int y) {
        drawBox(x - 1, y - 1, x + 18, y + 18, 0xFF10171D);
        drawBox(x, y, x + 17, y + 17, 0xFF53616C);
        if (raw > 0 && count > 0) {
            drawIdIcon(raw, x, y);
            drawStackCount(count, x, y);
        }
    }

    @Override
    public void render(int mx, int my) {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
        drawBox(0, 0, width, height, 0xB0000000);
        drawBox(left(), top(), left() + 300, top() + 226, 0xFF26333D);
        drawString(fontRenderer, "Defender", left() + 12, top() + 10, 0xFFFFFF);
        drawString(
                fontRenderer,
                "Health: " + defender.health + " / 20",
                left() + 12,
                top() + 25,
                0x9FE5BA);
        for (int s = 0; s < 5; s++) {
            int x = left() + 12 + s * 39;
            drawString(fontRenderer, LABELS[s], x, top() + 41, 0xCFD8E0);
            slot(defender.equipment[s], 1, x, top() + 53);
        }
        drawString(fontRenderer, "Your inventory", left() + 12, top() + 85, 0xFFFFFF);
        for (int s = 0; s < Math.min(36, minecraft.player.inventory.totalSize()); s++)
            slot(
                    minecraft.player.inventory.getId(s),
                    minecraft.player.inventory.getCount(s),
                    left() + 12 + s % 9 * 20,
                    top() + 100 + s / 9 * 22);
        drawString(fontRenderer, message, left() + 8, top() + 188, 0xFFE6A0);
        drawString(
                fontRenderer,
                "Armor: " + Equipment.points(defender),
                left() + 12,
                top() + 208,
                0xCFD8E0);
        drawPreview();
        for (Object object : buttons) {
            Button b = (Button) object;
            drawBox(b.x, b.y, b.x + b.width, b.y + b.height, 0xFF53616C);
            drawCenteredString(fontRenderer, b.text, b.x + b.width / 2, b.y + 6, 0xFFFFFF);
        }
        if (hasCarriedStack()) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            renderCarriedStack(mx, my);
        } else if (pressedSlot >= 0 && equipmentInventorySlotAt(mx, my) != pressedSlot) {
            var inventory = minecraft.player.inventory;
            int id =
                    pressedSlot < EQUIPMENT_START
                            ? inventory.getId(pressedSlot)
                            : defender.equipment[pressedSlot - EQUIPMENT_START];
            if (id > 0) {
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                drawRecipeItem(
                        id,
                        pressedSlot < EQUIPMENT_START ? inventory.getCount(pressedSlot) : 1,
                        mx - 8,
                        my - 8);
            }
        }
    }

    private void drawPreview() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            minecraft.renderer.setLighting(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glTranslatef(left() + 250, top() + 52, 80);
            GL11.glScalef(33, 33, 33);
            GL11.glRotatef(155, 0, 1, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load("/char.png"));
            preview.attackOffset = 0;
            preview.render(0, 0, 0, 0, 0, .0625f);
            EquipmentRenderer.armor(defender, preview, minecraft.textureManager, .0625f, 1);
            EquipmentRenderer.held(defender, preview, minecraft.textureManager, .0625f, 1);
        } finally {
            minecraft.renderer.setLighting(false);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
