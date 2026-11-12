package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.crafting.CraftingManager;
import net.classicremastered.minecraft.crafting.CraftingManager.ItemStack;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.PackedTurret;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class GuiCrafting extends CraftingContainerScreen {

    private int texture;

    private static final int GRID_X = 30;
    private static final int GRID_Y = 17;
    private static final int RESULT_X = 124;
    private static final int RESULT_Y = 35;
    private static final int BOOK_X = 5;
    private static final int BOOK_Y = 34;

    // Separate crafting matrix (3x3)
    private int[] craftMatrix = new int[9];
    private int[] craftCount = new int[9];

    // Output result
    private int resultId = -1;
    private int resultCount = 0;

    // Carried item (on mouse cursor)
    private int carriedId = -1;
    private int carriedCnt = 0;
    private PackedTurret carriedCrate;

    // Drag-paint state
    private boolean draggingRMB = false;
    private boolean[] painted = new boolean[45]; // 0-8=Matrix, 9-35=InvMain, 36-44=InvHotbar

    public GuiCrafting() {
        // Clear matrix
        for (int i = 0; i < 9; i++) {
            craftMatrix[i] = -1;
            craftCount[i] = 0;
        }
    }

    @Override
    public void onOpen() {
        this.texture = minecraft.textureManager.load("/gui/crafting.png");
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++) painted[i] = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (draggingRMB) {
            if (!Mouse.isButtonDown(1)) {
                draggingRMB = false;
                for (int i = 0; i < painted.length; i++) painted[i] = false;
                return;
            }
            // Logic
            int mx = Mouse.getX() * this.width / this.minecraft.width;
            int my = this.height - Mouse.getY() * this.height / this.minecraft.height - 1;

            int slot = getUnifiedSlotAt(mx, my);
            if (slot != -1 && !painted[slot] && carriedId > 0 && carriedCnt > 0) {
                clickUnifiedSlot(slot, 1);
                painted[slot] = true;
            }
        }
    }

    @Override
    public void onClose() {
        // Return items from crafting matrix to inventory
        Player p = minecraft.player;
        if (p != null && p.inventory != null) {
            for (int i = 0; i < 9; i++) {
                if (craftMatrix[i] > 0 && craftCount[i] > 0) {
                    craftCount[i] = returnStack(p, craftMatrix[i], craftCount[i], null);
                    if (craftCount[i] == 0) craftMatrix[i] = -1;
                }
            }

            // Also flush carried item
            if (carriedId > 0 && carriedCnt > 0) {
                carriedCnt = returnStack(p, carriedId, carriedCnt, carriedCrate);
                if (carriedCnt == 0) {
                    carriedId = -1;
                    carriedCrate = null;
                }
            }
        }
        super.onClose();
    }

    private int returnStack(Player player, int id, int count, PackedTurret crate) {
        if (crate != null) {
            if (player.inventory.addCrate(crate)) return 0;
        } else {
            while (count > 0 && player.inventory.addResource(id)) count--;
        }
        if (count > 0 && player.level != null) {
            net.classicremastered.minecraft.Entity dropped;
            if (id >= 256) {
                dropped =
                        new net.classicremastered.minecraft.entity.DroppedItem(
                                        player.level, player.x, player.y, player.z, id - 256, count)
                                .withCrate(crate);
            } else {
                dropped =
                        new net.classicremastered.minecraft.entity.DroppedBlock(
                                player.level, player.x, player.y, player.z, id, count);
            }
            player.level.addEntity(dropped);
            count = 0;
        }
        return count;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        final int x0 = craftingPanelLeft();
        final int y0 = (height - CRAFTING_PANEL_HEIGHT) / 2;

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(x0, y0, 0, 0, CRAFTING_PANEL_WIDTH, CRAFTING_PANEL_HEIGHT);
        fontRenderer.renderNoShadow("Crafting", x0 + 29, y0 + 6, 0x404040);
        fontRenderer.renderNoShadow("Inventory", x0 + 8, y0 + 72, 0x404040);

        Inventory inventory = minecraft.player.inventory;
        int[] position = new int[2];
        for (int slot = 0; slot < painted.length; slot++) {
            getUnifiedSlotPos(slot, position);
            int inventorySlot = slot < 36 ? slot : slot - 36;
            int id = slot < 9 ? craftMatrix[slot] : inventory.getId(inventorySlot);
            int count = slot < 9 ? craftCount[slot] : inventory.getCount(inventorySlot);
            drawRecipeItem(id, count, position[0], position[1]);
        }
        int resultX = x0 + RESULT_X, resultY = y0 + RESULT_Y;
        drawRecipeItem(resultId, resultCount, resultX, resultY);

        int hover = getUnifiedSlotAt(mouseX, mouseY);
        if (hover >= 0) {
            getUnifiedSlotPos(hover, position);
            int sx = position[0], sy = position[1];
            drawBox(sx - 1, sy - 1, sx + 17, sy, 0x80FFFFFF);
            drawBox(sx - 1, sy + 16, sx + 17, sy + 17, 0x80FFFFFF);
            drawBox(sx - 1, sy, sx, sy + 16, 0x80FFFFFF);
            drawBox(sx + 16, sy, sx + 17, sy + 16, 0x80FFFFFF);
        }

        drawRecipeBook(y0, 3, craftMatrix, craftCount);
        drawRecipeBookButton(mouseX, mouseY, x0 + BOOK_X, y0 + BOOK_Y);
        drawRecipeRequirements(
                x0 + GRID_X, y0 + GRID_Y, 3, craftMatrix, craftCount, mouseX, mouseY);

        if (carriedId > 0 && carriedCnt > 0) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            drawRecipeItem(carriedId, carriedCnt, mouseX - 8, mouseY - 8);
        }
    }

    @Override
    protected void drawRecipeItem(int id, int count, int x, int y) {
        if (id <= 0 || count <= 0) return;
        drawSlotIcon(id, x, y);
        drawStackCount(count, x, y);
    }

    @Override
    protected void onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return;
        boolean pickingUp = button == 0 && carriedId <= 0;
        if (button == 0) beginStackDrag(-1);
        final int x0 = craftingPanelLeft();
        final int y0 = (height - CRAFTING_PANEL_HEIGHT) / 2;

        if (button == 0 && clickRecipeBookButton(mouseX, mouseY, x0 + BOOK_X, y0 + BOOK_Y)) return;
        if (clickRecipeBook(
                mouseX, mouseY, button, y0, 3, craftMatrix, craftCount, carriedId > 0)) {
            checkRecipe();
            return;
        }

        // The output does not participate in drag painting.
        int outX = x0 + RESULT_X;
        int outY = y0 + RESULT_Y;
        if (inside(mouseX, mouseY, outX - 5, outY - 5, 26, 26)) {
            handleOutputClick();
            if (pickingUp && carriedId > 0) beginStackDrag(painted.length);
            return;
        }

        int slot = getUnifiedSlotAt(mouseX, mouseY);
        if (slot != -1) {
            boolean isRightClick = (button == 1);

            if (isRightClick) {
                draggingRMB = true;
                for (int i = 0; i < painted.length; i++) painted[i] = false;
                painted[slot] = true;
            }

            clickUnifiedSlot(slot, button);
            if (pickingUp && carriedId > 0) beginStackDrag(slot);
        }
    }

    @Override
    protected void onMouseRelease(int x, int y, int button) {
        int target = getUnifiedSlotAt(x, y);
        if (finishStackDrag(target, button) && carriedId > 0) clickUnifiedSlot(target, 0);
    }

    private void clickUnifiedSlot(int slot, int button) {
        if (slot < craftMatrix.length) {
            if (carriedCrate != null) return;
            clearRecipeRequirements();
            slotClick(craftMatrix, craftCount, slot, button);
            checkRecipe();
        } else {
            inventoryClick(minecraft.player.inventory, slot < 36 ? slot : slot - 36, button);
        }
    }

    private static int cap(int id) {
        return net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id);
    }

    // Handles clicking on a raw array-based slot (Crafting Matrix)
    private void slotClick(int[] ids, int[] counts, int slot, int button) {
        int id = ids[slot];
        int count = counts[slot];

        if (button == 0) { // Left click
            if (carriedId == -1) {
                // Pick up
                if (id != -1) {
                    carriedId = id;
                    carriedCnt = count;
                    ids[slot] = -1;
                    counts[slot] = 0;
                }
            } else {
                // Place or Swap
                if (id == -1) {
                    ids[slot] = carriedId;
                    int moved = Math.min(cap(carriedId), carriedCnt);
                    counts[slot] = moved;
                    carriedCnt -= moved;
                    if (carriedCnt == 0) carriedId = -1;
                } else if (id == carriedId) {
                    // Add matches
                    if (count < cap(id)) { // Stack limit
                        int space = cap(id) - count;
                        int move = Math.min(space, carriedCnt);
                        counts[slot] += move;
                        carriedCnt -= move;
                        if (carriedCnt == 0) carriedId = -1;
                    }
                } else {
                    // Swap
                    if (carriedCnt > cap(carriedId)) return;
                    int tempId = carriedId;
                    int tempCnt = carriedCnt;
                    carriedId = id;
                    carriedCnt = count;
                    ids[slot] = tempId;
                    counts[slot] = tempCnt;
                }
            }
        } else if (button == 1) { // Right click
            if (carriedId == -1) {
                // Split
                if (id != -1) {
                    int half = (count + 1) / 2;
                    carriedId = id;
                    carriedCnt = half;
                    counts[slot] -= half;
                    if (counts[slot] == 0) ids[slot] = -1;
                }
            } else {
                // Place One
                if (id == -1) {
                    ids[slot] = carriedId;
                    counts[slot] = 1;
                    carriedCnt--;
                    if (carriedCnt == 0) carriedId = -1;
                } else if (id == carriedId) {
                    if (count < cap(id)) {
                        counts[slot]++;
                        carriedCnt--;
                        if (carriedCnt == 0) carriedId = -1;
                    }
                }
            }
        }
    }

    private void inventoryClick(Inventory inventory, int slot, int button) {
        if (slot >= inventory.totalSize()) return;
        int id = inventory.getId(slot);
        int count = inventory.getCount(slot);
        PackedTurret crate = inventory.crate(slot);
        if (carriedId <= 0) {
            if (id <= 0 || count <= 0) return;
            carriedId = id;
            carriedCnt = button == 1 ? (count + 1) / 2 : count;
            carriedCrate = crate;
            int remaining = count - carriedCnt;
            inventory.setIdCount(slot, remaining > 0 ? id : -1, remaining);
        } else if (count == 0 || id == carriedId && crate == null && carriedCrate == null) {
            int amount = Math.min(button == 1 ? 1 : carriedCnt, cap(carriedId) - count);
            if (amount <= 0) return;
            putCarried(inventory, slot, count + amount);
            carriedCnt -= amount;
            if (carriedCnt == 0) {
                carriedId = -1;
                carriedCrate = null;
            }
        } else if (button == 0 && carriedCnt <= cap(carriedId)) {
            putCarried(inventory, slot, carriedCnt);
            carriedId = id;
            carriedCnt = count;
            carriedCrate = crate;
        }
    }

    private void putCarried(Inventory inventory, int slot, int count) {
        if (carriedCrate != null) inventory.setCrate(slot, carriedCrate);
        else inventory.setIdCount(slot, carriedId, count);
    }

    private void checkRecipe() {
        ItemStack result = CraftingManager.getInstance().findMatchingRecipe(craftMatrix, 3);
        if (result != null) {
            resultId = result.id;
            resultCount = result.count;
        } else {
            resultId = -1;
            resultCount = 0;
        }
    }

    private void handleOutputClick() {
        if (resultId <= 0 || carriedCrate != null) return;

        // Can we pick it up?
        if (carriedId == -1) {
            carriedId = resultId;
            carriedCnt = resultCount;
            consumeGrid();
            checkRecipe();
        } else if (carriedId == resultId) {
            if (carriedCnt + resultCount
                    <= (resultId >= 256 && Item.items[resultId - 256] != null
                            ? Item.items[resultId - 256].maxStackSize
                            : 64)) {
                carriedCnt += resultCount;
                consumeGrid();
                checkRecipe();
            }
        }
    }

    private void consumeGrid() {
        CraftingManager.consumeIngredients(craftMatrix, craftCount);
    }

    private void drawSlotIcon(int id, int x, int y) {
        id = net.classicremastered.minecraft.level.itemstack.Item.iconId(id);
        if (id > 0 && id < 256 && Block.blocks[id] != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0f);
            GL11.glScalef(10.0F, 10.0F, 10.0F);
            GL11.glTranslatef(0.8F, 0.8F, 8.0F);
            GL11.glRotatef(-30.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-1.5F, 0.5F, 0.5F);
            GL11.glScalef(-1.0F, -1.0F, -1.0F);
            int tex =
                    minecraft.textureManager.load(
                            net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            ShapeRenderer.instance.begin();
            Block.blocks[id].renderFullbright(ShapeRenderer.instance);
            ShapeRenderer.instance.end();
            GL11.glPopMatrix();
        } else {
            int idx = id - 256;
            if (idx >= 0 && idx < Item.items.length && Item.items[idx] != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, 0);
                Item.items[idx].renderIcon(minecraft.textureManager, ShapeRenderer.instance);
                GL11.glPopMatrix();
            }
        }
    }

    private int getUnifiedSlotAt(int mx, int my) {
        int top = (height - CRAFTING_PANEL_HEIGHT) / 2;
        if (isRecipeBookArea(mx, my, top, 3)) return -1;
        int[] position = new int[2];
        for (int slot = 0; slot < painted.length; slot++) {
            getUnifiedSlotPos(slot, position);
            if (inside(mx, my, position[0] - 1, position[1] - 1, CELL, CELL)) return slot;
        }
        return -1;
    }

    private void getUnifiedSlotPos(int slot, int[] out) {
        final int x0 = craftingPanelLeft();
        final int y0 = (height - CRAFTING_PANEL_HEIGHT) / 2;

        if (slot < 9) {
            int gridX = x0 + GRID_X;
            int gridY = y0 + GRID_Y;
            int col = slot % 3;
            int row = slot / 3;
            out[0] = gridX + col * 18;
            out[1] = gridY + row * 18;
        } else if (slot < 36) {
            int i = slot - 9;
            int col = i % 9;
            int row = i / 9;
            out[0] = x0 + 8 + col * 18;
            out[1] = y0 + 84 + row * 18;
        } else if (slot < 45) {
            int i = slot - 36;
            out[0] = x0 + 8 + i * 18;
            out[1] = y0 + 142;
        }
    }
}
