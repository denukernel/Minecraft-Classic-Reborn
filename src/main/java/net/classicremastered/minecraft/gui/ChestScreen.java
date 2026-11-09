package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.ChestController;
import net.classicremastered.minecraft.level.RedstoneEngine;
import net.classicremastered.minecraft.level.itemstack.Item;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ChestScreen extends InventoryScreen {
    private final int x, y, z;
    private int carriedId = -1, carriedCount;
    private ChestController.View view;
    private boolean opened;

    public ChestScreen(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        ChestController.join(minecraft.level, new RedstoneEngine.Pos(x, y, z));
        view = ChestController.view(minecraft.level, new RedstoneEngine.Pos(x, y, z));
        if (ChestController.canOpen(minecraft.level, view)) {
            ChestController.open(minecraft.level, view);
            opened = true;
        }
    }

    private int chestSlots() {
        return view == null ? 27 : view.size();
    }

    private int rows() {
        return chestSlots() / 9;
    }

    private int left() {
        return (width - 176) / 2;
    }

    private int top() {
        return (height - (rows() * 18 + 112)) / 2;
    }

    private int slotX(int slot) {
        return left() + 8 + slot % 9 * 18;
    }

    private int slotY(int slot) {
        if (slot < chestSlots()) return top() + 18 + slot / 9 * 18;
        if (slot < chestSlots() + 27)
            return top() + rows() * 18 + 30 + (slot - chestSlots()) / 9 * 18;
        return top() + rows() * 18 + 88;
    }

    private int playerSlot(int slot) {
        return slot < chestSlots() + 27 ? slot - chestSlots() + 9 : slot - chestSlots() - 27;
    }

    private int id(int slot) {
        return slot < chestSlots()
                ? view.state(slot).items[slot % 27]
                : minecraft.player.inventory.getId(playerSlot(slot));
    }

    private int count(int slot) {
        return slot < chestSlots()
                ? view.state(slot).counts[slot % 27]
                : minecraft.player.inventory.getCount(playerSlot(slot));
    }

    private void set(int slot, int id, int count) {
        if (slot < chestSlots()) {
            view.state(slot).items[slot % 27] = count > 0 ? id : 0;
            view.state(slot).counts[slot % 27] = count;
        } else minecraft.player.inventory.setIdCount(playerSlot(slot), id, count);
    }

    private int limit(int id) {
        if (id >= 256 && id - 256 < Item.items.length && Item.items[id - 256] != null)
            return Math.min(64, Item.items[id - 256].maxStackSize);
        return 64;
    }

    private int slotAt(int mx, int my) {
        for (int slot = 0; slot < chestSlots() + 36; slot++)
            if (mx >= slotX(slot)
                    && mx < slotX(slot) + 16
                    && my >= slotY(slot)
                    && my < slotY(slot) + 16) return slot;
        return -1;
    }

    private void drawStack(int id, int count, int sx, int sy) {
        if (id <= 0 || count <= 0) return;
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        drawIdIcon(id, sx, sy);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (count > 1) {
            String amount = Integer.toString(count);
            drawString(
                    fontRenderer,
                    amount,
                    sx + 17 - fontRenderer.getWidth(amount),
                    sy + 9,
                    0xFFFFFF);
        }
    }

    @Override
    public void render(int mx, int my) {
        if (!ChestController.valid(minecraft.level, view)) return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        drawBox(0, 0, width, height, 0x90000000);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load("/gui/chest.png"));
        int upper = rows() * 18 + 17;
        drawImage(left(), top(), 0, 0, 176, upper);
        drawImage(left(), top() + upper, 0, 126, 176, 95);
        drawString(
                fontRenderer,
                chestSlots() == 54 ? "Large Chest" : "Chest",
                left() + 8,
                top() + 6,
                0xFFFFFF);
        drawString(fontRenderer, "Inventory", left() + 8, top() + rows() * 18 + 19, 0xFFFFFF);
        int hovered = slotAt(mx, my);
        for (int slot = 0; slot < chestSlots() + 36; slot++) {
            if (slot == hovered)
                drawBox(slotX(slot), slotY(slot), slotX(slot) + 16, slotY(slot) + 16, 0x80FFFFFF);
            drawStack(id(slot), count(slot), slotX(slot), slotY(slot));
        }
        if (carriedCount > 0) drawStack(carriedId, carriedCount, mx - 8, my - 8);
        else if (hovered >= 0 && count(hovered) > 0) {
            String name = BlockSelectScreen.displayName(id(hovered));
            int tooltipX = Math.min(mx + 10, width - fontRenderer.getWidth(name) - 10);
            drawBox(
                    tooltipX - 3,
                    my - 15,
                    tooltipX + fontRenderer.getWidth(name) + 3,
                    my - 2,
                    0xF0100010);
            drawString(fontRenderer, name, tooltipX, my - 12, 0xFFFFFF);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        int slot = slotAt(mx, my);
        if (slot < 0 || button > 1) return;
        boolean shift =
                Keyboard.isCreated()
                        && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        clickSlot(slot, button, shift);
    }

    private void clickSlot(int slot, int button, boolean shift) {
        if (!ChestController.valid(minecraft.level, view)
                || !ChestController.canOpen(minecraft.level, view)) return;
        int id = id(slot), count = count(slot);
        if (shift) {
            quickMove(slot);
        } else if (carriedCount == 0) {
            if (count == 0) return;
            carriedId = id;
            carriedCount = button == 1 ? (count + 1) / 2 : count;
            set(slot, id, count - carriedCount);
        } else if (count == 0 || id == carriedId) {
            int amount = Math.min(button == 1 ? 1 : carriedCount, limit(carriedId) - count);
            if (amount > 0) {
                set(slot, carriedId, count + amount);
                carriedCount -= amount;
            }
        } else if (button == 0 && carriedCount <= limit(carriedId)) {
            set(slot, carriedId, carriedCount);
            carriedId = id;
            carriedCount = count;
        }
        if (carriedCount == 0) carriedId = -1;
    }

    private void quickMove(int slot) {
        int id = id(slot), remaining = count(slot);
        if (remaining == 0) return;
        int start = slot < chestSlots() ? chestSlots() : 0;
        int end = slot < chestSlots() ? chestSlots() + 36 : chestSlots();
        for (int pass = 0; pass < 2; pass++)
            for (int target = start; target < end && remaining > 0; target++) {
                int count = count(target);
                if ((pass == 0 && count > 0 && id(target) == id) || (pass == 1 && count == 0)) {
                    int amount = Math.min(remaining, Math.max(0, limit(id) - count));
                    set(target, id, count + amount);
                    remaining -= amount;
                }
            }
        set(slot, id, remaining);
    }

    @Override
    public void tick() {
        if (!ChestController.valid(minecraft.level, view)
                || !ChestController.canOpen(minecraft.level, view)
                || minecraft.player.distanceTo(x + .5f, y + .5f, z + .5f) > 8) {
            minecraft.setCurrentScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (opened) ChestController.close(minecraft.level, view);
        opened = false;
        for (int pass = 0; pass < 2; pass++)
            for (int slot = 0;
                    slot < minecraft.player.inventory.totalSize() && carriedCount > 0;
                    slot++) {
                int id = minecraft.player.inventory.getId(slot);
                int count = minecraft.player.inventory.getCount(slot);
                if ((pass == 0 && id == carriedId && count > 0) || (pass == 1 && count == 0)) {
                    int amount = Math.min(carriedCount, Math.max(0, limit(carriedId) - count));
                    minecraft.player.inventory.setIdCount(slot, carriedId, count + amount);
                    carriedCount -= amount;
                }
            }
        if (carriedCount > 0) {
            if (carriedId < 256)
                minecraft.level.addEntity(
                        new DroppedBlock(
                                minecraft.level,
                                minecraft.player.x,
                                minecraft.player.y,
                                minecraft.player.z,
                                carriedId,
                                carriedCount));
            else
                minecraft.level.addEntity(
                        new DroppedItem(
                                minecraft.level,
                                minecraft.player.x,
                                minecraft.player.y,
                                minecraft.player.z,
                                carriedId - 256,
                                carriedCount));
        }
        carriedCount = 0;
        carriedId = -1;
        super.onClose();
    }
}
