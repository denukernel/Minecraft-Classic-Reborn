package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.crafting.CraftingManager;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.render.ShapeRenderer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class InventoryScreen extends CraftingContainerScreen {
    // textures
    private int texture;
    private int terrainAtlas; // /terrain.png

    // carried stack (cursor)
    private int carriedId = -1;

    protected final boolean hasCarriedStack() {
        return carriedId > 0 && carriedCnt > 0;
    }

    protected final void discardCarriedStack() {
        carriedCrate = null;
        carriedWear = 0;
        carriedId = -1;
        carriedCnt = 0;
        draggingRMB = false;
    }

    private net.classicremastered.minecraft.level.itemstack.PackedTurret carriedCrate;
    private int carriedWear;
    private int carriedCnt = 0;

    // drag-paint (RMB) state
    private boolean draggingRMB = false;
    private boolean[] painted = new boolean[9]; // resized on open to inv size

    // pagination for main (non-hotbar) slots (kept harmless; hotbar-only storage
    // for now)
    private static final int HOTBAR_SIZE = 9;
    private static final int ROWS_VISIBLE = 3; // classic: 3 rows visible (27)
    private int page = 0; // 0..(pages-1)
    private int totalPages = 1; // computed per open

    // GUI constants
    protected static final int CREATIVE_WIDTH = 195;
    protected static final int CREATIVE_HEIGHT = 136;
    private static final int CRAFT_X = 98;
    private static final int CRAFT_Y = 18;
    private static final int RESULT_X = 154;
    private static final int RESULT_Y = 28;
    private static final int BOOK_X = 104;
    private static final int BOOK_Y = 61;
    private static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private final int[] craftingIds = {-1, -1, -1, -1};
    private final int[] craftingCounts = new int[4];

    protected boolean creativeInventoryLayout() {
        return false;
    }

    protected int panelLeft() {
        return creativeInventoryLayout() ? (width - CREATIVE_WIDTH) / 2 : craftingPanelLeft();
    }

    protected final int panelTop() {
        return (height - (creativeInventoryLayout() ? CREATIVE_HEIGHT : CRAFTING_PANEL_HEIGHT)) / 2;
    }

    private int gridTop() {
        return panelTop() + (creativeInventoryLayout() ? 54 : 84);
    }

    private int hotbarTop() {
        return gridTop() + 58;
    }

    private CraftingManager.ItemStack craftResult() {
        return CraftingManager.getInstance().findMatchingRecipe(craftingIds, 2);
    }

    private void clickCraftSlot(int slot, int button) {
        int id = craftingIds[slot], count = craftingCounts[slot];
        if (carriedId <= 0) {
            if (id <= 0) return;
            carriedId = id;
            carriedCnt = button == 1 ? (count + 1) / 2 : count;
            craftingCounts[slot] -= carriedCnt;
        } else if (carriedCrate != null || carriedWear > 0) {
            return;
        } else if (id <= 0 || id == carriedId) {
            int moved = Math.min(button == 1 ? 1 : carriedCnt, maxStackFor(carriedId) - count);
            if (moved <= 0) return;
            craftingIds[slot] = carriedId;
            craftingCounts[slot] += moved;
            carriedCnt -= moved;
            if (carriedCnt == 0) carriedId = -1;
        } else if (button == 0 && carriedCnt <= maxStackFor(carriedId)) {
            craftingIds[slot] = carriedId;
            craftingCounts[slot] = carriedCnt;
            carriedId = id;
            carriedCnt = count;
        }
        if (craftingCounts[slot] == 0) craftingIds[slot] = -1;
    }

    private void takeCraftResult() {
        var result = craftResult();
        if (result == null
                || carriedId > 0 && carriedId != result.id
                || carriedCrate != null
                || carriedCnt + result.count > maxStackFor(result.id)) return;
        carriedId = result.id;
        carriedCnt += result.count;
        CraftingManager.consumeIngredients(craftingIds, craftingCounts);
    }

    @Override
    protected void drawRecipeItem(int id, int count, int x, int y) {
        if (id <= 0) return;
        drawIdIcon(id, x, y);
        drawStackCount(count, x, y);
    }

    private int armorX(int slot) {
        return panelLeft() + (creativeInventoryLayout() ? (slot < 2 ? 54 : 108) : 8);
    }

    private int armorY(int slot) {
        return panelTop() + (creativeInventoryLayout() ? 6 + (slot % 2) * 27 : 8 + slot * CELL);
    }

    private int armorSlotAt(int mx, int my) {
        for (int slot = 0; slot < ARMOR_SLOTS.length; slot++) {
            if (inside(mx, my, armorX(slot), armorY(slot), 16, 16)) return slot;
        }
        return -1;
    }

    // drag-paint band lock
    private boolean paintHotbarBand =
            false; // true = painting hotbar only; false = main grid (unused with 9 slots)

    @Override
    public void onOpen() {
        this.texture =
                this.minecraft.textureManager.load(
                        creativeInventoryLayout()
                                ? "/gui/creative-inventory.png"
                                : "/gui/inventory.png");
        this.terrainAtlas =
                this.minecraft.textureManager.load(
                        net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE);

        Inventory inv = this.minecraft.player.inventory;

        int n = inv.totalSize(); // hotbar + grid
        if (painted.length != n) painted = new boolean[n];

        carriedId = -1;
        carriedCnt = 0;
        carriedWear = 0;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++) painted[i] = false;

        int main = Math.max(0, n - HOTBAR_SIZE);
        int perPage = ROWS_VISIBLE * 9; // 27
        totalPages = Math.max(1, (main + perPage - 1) / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
    }

    // ---------- helpers ----------

    @Override
    protected void onResize() {
        // Slot positions use the live screen dimensions.
    }

    private static int maxStackFor(int id) {
        return Item.stackLimit(id);
    }

    private static boolean sameId(int a, int b) {
        return a > 0 && a == b;
    }

    private void putCarried(Inventory inventory, int slot, int count) {
        if (carriedCrate != null) inventory.setCrate(slot, carriedCrate);
        else {
            inventory.setIdCount(slot, carriedId, count);
            inventory.setArmorWear(slot, carriedWear);
            carriedWear = 0;
        }
    }

    protected final void pickUpAll(Inventory inv, int slot) {
        carriedCrate = inv.crate(slot);
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);
        if (id <= 0 || cnt <= 0) return;
        carriedWear = inv.armorWear(slot);
        carriedId = id;
        carriedCnt = cnt;
        inv.setIdCount(slot, -1, 0);
    }

    private void pickUpHalf(Inventory inv, int slot) {
        carriedCrate = inv.crate(slot);
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);
        if (id <= 0 || cnt <= 0) return;
        carriedWear = inv.armorWear(slot);
        int half = (cnt + 1) / 2;
        carriedId = id;
        carriedCnt = half;
        inv.setIdCount(slot, (cnt - half) > 0 ? id : -1, Math.max(0, cnt - half));
    }

    protected final void placeCarriedStackInSlot(int slot) {
        Inventory inventory = minecraft.player.inventory;
        if (slot >= 0 && slot < inventory.totalSize()) placeAllOrMerge(inventory, slot);
    }

    private void placeAllOrMerge(Inventory inv, int slot) {
        if (carriedId <= 0 || carriedCnt <= 0) return;

        int dstId = inv.getId(slot);
        int dstCnt = inv.getCount(slot);
        int cap = maxStackFor(carriedId);

        if (dstId <= 0) {
            int moved = Math.min(cap, carriedCnt);
            putCarried(inv, slot, moved);
            carriedCnt -= moved;
            if (carriedCnt == 0) {
                carriedCrate = null;
                carriedId = -1;
            }
            return;
        }
        var previousCrate = inv.crate(slot);
        int previousWear = inv.armorWear(slot);
        if (dstId == carriedId && cap > 1 && carriedCrate == null && previousCrate == null) {
            int put = Math.min(Math.max(0, cap - dstCnt), carriedCnt);
            if (put <= 0) return;
            inv.setIdCount(slot, dstId, dstCnt + put);
            carriedCnt -= put;
            if (carriedCnt == 0) carriedId = -1;
            return;
        }
        if (carriedCnt > cap) return;
        putCarried(inv, slot, carriedCnt);
        carriedCrate = previousCrate;
        carriedWear = previousWear;
        carriedId = dstId;
        carriedCnt = dstCnt;
    }

    private void giveOneInto(Inventory inv, int slot, int id) {
        if (id <= 0 || carriedCnt <= 0) return;
        int dstId = inv.getId(slot);
        int dstCnt = inv.getCount(slot);

        if (dstId <= 0) {
            putCarried(inv, slot, 1);
            if (--carriedCnt <= 0) {
                carriedId = -1;
                carriedCrate = null;
            }
            return;
        }
        if (dstId == id && dstCnt < maxStackFor(id)) {
            inv.setIdCount(slot, dstId, dstCnt + 1);
            if (--carriedCnt <= 0) carriedId = -1;
        }
    }

    // slot → screen coords (HOTBAR ONLY)
    // slot → screen coords (global index)
    private void slotXY(int slot, int[] out) {
        int x0 = panelLeft();
        if (slot < HOTBAR_SIZE) { // hotbar
            out[0] = x0 + inventoryGridX() + slot * CELL;
            out[1] = hotbarTop();
            return;
        }
        // grid (paged 27)
        int perPage = ROWS_VISIBLE * 9; // 27
        int base = HOTBAR_SIZE + page * perPage;
        int vis = slot - base;
        if (vis < 0) vis = 0;
        int col = vis % 9;
        int row = vis / 9; // 0..ROWS_VISIBLE-1
        out[0] = x0 + inventoryGridX() + col * CELL;
        out[1] = gridTop() + row * CELL;
    }

    protected int inventoryGridX() {
        return creativeInventoryLayout() ? 9 : 8;
    }

    protected final int inventorySlotAt(int mx, int my) {
        if (!creativeInventoryLayout() && isRecipeBookArea(mx, my, panelTop(), 2)) return -1;
        int relX = mx - panelLeft() - inventoryGridX();
        if (relX < 0 || relX >= HOTBAR_SIZE * CELL || relX % CELL >= 16) return -1;
        int column = relX / CELL;
        if (my >= hotbarTop() && my < hotbarTop() + 16) return column;
        int relY = my - gridTop();
        if (relY < 0 || relY >= ROWS_VISIBLE * CELL || relY % CELL >= 16) return -1;
        return HOTBAR_SIZE + (page * ROWS_VISIBLE + relY / CELL) * HOTBAR_SIZE + column;
    }

    // page buttons rects (kept for UI; no storage beyond hotbar yet)
    private int[] leftBtnRect() {
        int x0 = panelLeft(), y0 = panelTop();
        return new int[] {
            x0 + inventoryGridX() + 130, y0 + 6, x0 + inventoryGridX() + 142, y0 + 18
        };
    }

    private int[] rightBtnRect() {
        int x0 = panelLeft(), y0 = panelTop();
        return new int[] {
            x0 + inventoryGridX() + 148, y0 + 6, x0 + inventoryGridX() + 160, y0 + 18
        };
    }

    private boolean inRect(int x, int y, int[] r) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // ---------- render ----------
    @Override
    public void render(int mouseX, int mouseY) {
        final int x0 = panelLeft();
        final int y0 = panelTop();

        boolean creative = creativeInventoryLayout();
        drawInventoryBackground();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(
                x0,
                y0,
                0,
                0,
                creative ? CREATIVE_WIDTH : CRAFTING_PANEL_WIDTH,
                creative ? CREATIVE_HEIGHT : CRAFTING_PANEL_HEIGHT);

        if (creative) {
            drawPlayerPreview(x0 + 72, y0 + 5, 34, 45, 20, mouseX, mouseY);
        } else {
            drawPlayerPreview(x0 + 25, y0 + 7, 50, 72, 30, mouseX, mouseY);
            fontRenderer.renderNoShadow("Crafting", x0 + 97, y0 + 6, 0x404040);
            for (int i = 0; i < craftingIds.length; i++) {
                drawRecipeItem(
                        craftingIds[i],
                        craftingCounts[i],
                        x0 + CRAFT_X + i % 2 * CELL,
                        y0 + CRAFT_Y + i / 2 * CELL);
            }
            var result = craftResult();
            if (result != null)
                drawRecipeItem(result.id, result.count, x0 + RESULT_X, y0 + RESULT_Y);
        }
        for (int slot = 0; slot < ARMOR_SLOTS.length; slot++) {
            int sx = armorX(slot), sy = armorY(slot);
            int id = minecraft.player.equipment[slot];
            if (id > 0) {
                drawIdIcon(id, sx, sy);
                drawDurabilityBar(id, Equipment.wear(minecraft.player, slot), sx, sy);
            } else {
                drawSprite("/gui/armor_slot_" + ARMOR_SLOTS[slot] + ".png", sx, sy, 16, 16);
            }
        }
        int hover = renderPlayerInventory(mouseX, mouseY);
        Inventory inv = minecraft.player.inventory;
        int n = inv.totalSize();
        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int tooltipRaw = hover >= 0 && hover < n ? inv.getId(hover) : -1;
        int gearSlot = armorSlotAt(mouseX, mouseY);
        if (gearSlot >= 0) tooltipRaw = minecraft.player.equipment[gearSlot];
        if (!creative) {
            drawRecipeBook(y0, 2, craftingIds, craftingCounts);
            drawRecipeRequirements(
                    x0 + CRAFT_X, y0 + CRAFT_Y, 2, craftingIds, craftingCounts, mouseX, mouseY);
            drawRecipeBookButton(mouseX, mouseY, x0 + BOOK_X, y0 + BOOK_Y);
        }
        if (carriedId <= 0 && !hasCatalogCursor() && tooltipRaw > 0) {
            ItemTooltip.draw(
                    this,
                    tooltipRaw,
                    gearSlot < 0 && hover >= 0 ? inv.crate(hover) : null,
                    gearSlot >= 0
                            ? Equipment.wear(minecraft.player, gearSlot)
                            : hover >= 0 ? inv.armorWear(hover) : 0,
                    mouseX,
                    mouseY);
        }
        renderInventoryExtras(mouseX, mouseY);
        renderCarriedStack(mouseX, mouseY);
        if (depthWas) GL11.glEnable(GL11.GL_DEPTH_TEST);
        super.render(mouseX, mouseY);
    }

    protected final int renderPlayerInventory(int mouseX, int mouseY) {
        final Inventory inv = this.minecraft.player.inventory;
        final int n = inv.totalSize();

        final int perPage = ROWS_VISIBLE * 9; // 27
        final int base = HOTBAR_SIZE + page * perPage;
        final int end = Math.min(n, base + perPage);

        // draw grid (paged)
        for (int i = base; i < end; i++) {
            drawSlotIcon(inv, i);
        }
        // draw hotbar (always)
        for (int i = 0; i < Math.min(HOTBAR_SIZE, n); i++) {
            drawSlotIcon(inv, i);
        }

        // overlays (counts/hover)
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        for (int i = base; i < end; i++) drawCount(inv, i);
        for (int i = 0; i < Math.min(HOTBAR_SIZE, n); i++) drawCount(inv, i);

        int hover = inventorySlotAt(mouseX, mouseY);
        if (hover >= 0 && hover < n) {
            int[] xy = new int[2];
            slotXY(hover, xy);
            int sx = xy[0], sy = xy[1];
            drawBox(sx - 1, sy - 1, sx + 17, sy, 0x80FFFFFF);
            drawBox(sx - 1, sy + 16, sx + 17, sy + 17, 0x80FFFFFF);
            drawBox(sx - 1, sy, sx, sy + 16, 0x80FFFFFF);
            drawBox(sx + 16, sy, sx + 17, sy + 16, 0x80FFFFFF);
        }

        GL11.glPopAttrib();
        return hover >= 0 && hover < n ? hover : -1;
    }

    protected void drawInventoryBackground() {}

    protected void renderInventoryExtras(int mouseX, int mouseY) {}

    protected final void renderCarriedStack(int mouseX, int mouseY) {
        if (hasCarriedStack()) {
            int cx = mouseX - 8, cy = mouseY - 8;
            drawIdIcon(carriedId, cx, cy);
            drawDurabilityBar(carriedId, carriedWear, cx, cy);
            drawStackCount(carriedCnt, cx, cy);
        }
    }

    private net.classicremastered.minecraft.model.HumanoidModel preview;

    private void drawPlayerPreview(int x, int y, int w, int h, int scale, int mx, int my) {
        if (preview == null) {
            preview = new net.classicremastered.minecraft.model.HumanoidModel();
        }
        // Match the modern inventory curve, with signs adapted to the classic GUI transform.
        float horizontal = (float) Math.atan((x + w * .5f - mx) / 40f);
        float vertical = (float) Math.atan((y + h * .5f - my) / 40f);
        float yaw = -horizontal * 20f;
        float pitch = -vertical * 20f;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            minecraft.renderer.setLighting(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glColor4f(1, 1, 1, 1);
            // The top-down GUI projection reverses winding. Inherited world culling
            // otherwise removes the front faces and exposes the back of the skin.
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glTranslatef(x + w * .5f, y + h - 2 - scale * 1.5f, 50);
            GL11.glScalef(scale, scale, scale);
            GL11.glRotatef(180 + yaw, 0, 1, 0);
            minecraft.player.bindTexture(minecraft.textureManager);
            preview.attackOffset = 0;
            preview.render(0, 0, 0, yaw, pitch, .0625f);
            net.classicremastered.minecraft.render.EquipmentRenderer.armor(
                    minecraft.player, preview, minecraft.textureManager, .0625f, 1);
        } finally {
            minecraft.renderer.setLighting(false);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawSlotIcon(Inventory inv, int slot) {
        int n = inv.totalSize();
        if (slot < 0 || slot >= n) return;
        int id = inv.getId(slot);
        if (id <= 0) return;
        int[] xy = new int[2];
        slotXY(slot, xy);
        drawIdIcon(id, xy[0], xy[1]);
        drawDurabilityBar(id, inv.armorWear(slot), xy[0], xy[1]);
    }

    private void drawCount(Inventory inv, int slot) {
        int n = inv.totalSize();
        if (slot < 0 || slot >= n) return;
        int c = inv.getCount(slot);
        if (c > 1) {
            int[] xy = new int[2];
            slotXY(slot, xy);
            drawStackCount(c, xy[0], xy[1]);
        }
    }

    // Draw a block as a tiny 3D cube (BlockSelect transform) or a 2D item icon
    protected void drawIdIcon(int id, int sx, int sy) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            drawIcon(id, sx, sy);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawIcon(int id, int sx, int sy) {
        id = net.classicremastered.minecraft.level.itemstack.Item.iconId(id);
        GL11.glColor4f(1, 1, 1, 1);
        if (id < 256 && Block.blocks[id] != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(sx, sy, 0f);
            GL11.glScalef(10.0F, 10.0F, 10.0F);
            GL11.glTranslatef(0.8F, 0.8F, 8.0F);
            GL11.glRotatef(-30.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-1.5F, 0.5F, 0.5F);
            GL11.glScalef(-1.0F, -1.0F, -1.0F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrainAtlas);
            ShapeRenderer.instance.begin();
            Block.blocks[id].renderFullbright(ShapeRenderer.instance);
            ShapeRenderer.instance.end();
            GL11.glPopMatrix();
        } else {
            int itemId = id - 256;
            if (itemId >= 0 && itemId < Item.items.length) {
                Item it = Item.items[itemId];
                if (it != null) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(sx, sy, 0);
                    it.renderIcon(this.minecraft.textureManager, ShapeRenderer.instance);
                    GL11.glPopMatrix();
                }
            }
        }
    }

    // Return the carried stack to the inventory (merge, then empty slots)
    private void flushCarriedBackIntoInventory() {
        if (carriedId <= 0 || carriedCnt <= 0) return;

        Inventory inv = this.minecraft.player.inventory;
        final int n = inv.totalSize();
        final int cap = maxStackFor(carriedId);

        // 1) Merge into existing stacks of the same id
        for (int i = 0; i < n && carriedCnt > 0; i++) {
            int id = inv.getId(i);
            int cnt = inv.getCount(i);
            if (id == carriedId && cnt < cap) {
                int put = Math.min(cap - cnt, carriedCnt);
                inv.setIdCount(i, id, cnt + put);
                carriedCnt -= put;
            }
        }

        // 2) Fill empty slots (may span multiple empties)
        for (int i = 0; i < n && carriedCnt > 0; i++) {
            int id = inv.getId(i);
            int cnt = inv.getCount(i);
            if (id <= 0 || cnt <= 0) {
                int put = Math.min(cap, carriedCnt);
                putCarried(inv, i, put);
                carriedCnt -= put;
            }
        }

        if (carriedCnt > 0) {
            if (carriedId >= 256)
                this.minecraft.level.addEntity(
                        new net.classicremastered.minecraft.entity.DroppedItem(
                                        this.minecraft.level,
                                        minecraft.player.x,
                                        minecraft.player.y,
                                        minecraft.player.z,
                                        carriedId - 256,
                                        carriedCnt)
                                .withCrate(carriedCrate)
                                .withArmorWear(carriedWear));
            else
                for (int i = 0; i < carriedCnt; i++)
                    this.minecraft.level.addEntity(
                            new net.classicremastered.minecraft.entity.DroppedBlock(
                                    this.minecraft.level,
                                    minecraft.player.x,
                                    minecraft.player.y,
                                    minecraft.player.z,
                                    carriedId));
            carriedCnt = 0;
        }
        carriedCrate = null;
        carriedWear = 0;
        // Done: clear cursor + drag state
        carriedId = -1;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++) painted[i] = false;
    }

    // ---------- input ----------
    protected boolean hasCatalogCursor() {
        return false;
    }

    protected final void drawInventoryPageButtons() {
        if (totalPages <= 1) return;
        int[] previous = leftBtnRect(), next = rightBtnRect();
        fontRenderer.renderNoShadow("<", previous[0] + 3, previous[1] + 2, 0x404040);
        fontRenderer.renderNoShadow(">", next[0] + 3, next[1] + 2, 0x404040);
    }

    protected final boolean clickInventoryPage(int x, int y) {
        if (totalPages <= 1) return false;
        if (inRect(x, y, leftBtnRect())) {
            page = (page + totalPages - 1) % totalPages;
            playButtonClickSound();
            return true;
        }
        if (inRect(x, y, rightBtnRect())) {
            page = (page + 1) % totalPages;
            playButtonClickSound();
            return true;
        }
        return false;
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button == 0 && !creativeInventoryLayout()) {
            int source = hasCarriedStack() ? -1 : dragSlotAt(mx, my, true);
            handleInventoryClick(mx, my, button);
            beginStackDrag(hasCarriedStack() ? source : -1);
        } else {
            handleInventoryClick(mx, my, button);
        }
    }

    @Override
    protected void onMouseRelease(int x, int y, int button) {
        int target = isOutsideInventory(x, y) ? OUTSIDE_SLOT : dragSlotAt(x, y, false);
        if (finishStackDrag(target, button) && hasCarriedStack()) handleInventoryClick(x, y, 0);
    }

    protected final boolean isOutsideInventory(int x, int y) {
        return !inside(
                        x,
                        y,
                        panelLeft(),
                        panelTop(),
                        creativeInventoryLayout() ? CREATIVE_WIDTH : CRAFTING_PANEL_WIDTH,
                        creativeInventoryLayout() ? CREATIVE_HEIGHT : CRAFTING_PANEL_HEIGHT)
                && (creativeInventoryLayout() || !isRecipeBookArea(x, y, panelTop(), 2));
    }

    protected final void dropCarriedStack(int button) {
        if (!hasCarriedStack() || button < 0 || button > 1) return;
        int count = button == 0 ? carriedCnt : 1;
        if (minecraft.player.dropStack(carriedId, count, carriedCrate, carriedWear)) {
            carriedCnt -= count;
            carriedWear = 0;
            if (carriedCnt == 0) discardCarriedStack();
        }
    }

    private int craftSlotAt(int x, int y, int panelX, int panelY) {
        int startX = panelX + CRAFT_X - 1, startY = panelY + CRAFT_Y - 1;
        if (!inside(x, y, startX, startY, 2 * CELL, 2 * CELL)) return -1;
        return (x - startX) / CELL + (y - startY) / CELL * 2;
    }

    private int dragSlotAt(int x, int y, boolean includeResult) {
        int size = minecraft.player.inventory.totalSize();
        if (isRecipeBookArea(x, y, panelTop(), 2)) return -1;
        int slot = inventorySlotAt(x, y);
        if (slot >= 0 && slot < size) return slot;
        int craftSlot = craftSlotAt(x, y, panelLeft(), panelTop());
        if (craftSlot >= 0) return size + craftSlot;
        int armorSlot = armorSlotAt(x, y);
        if (armorSlot >= 0) return size + craftingIds.length + armorSlot;
        if (includeResult
                && inside(
                        x, y, panelLeft() + RESULT_X - 1, panelTop() + RESULT_Y - 1, CELL, CELL)) {
            return size + craftingIds.length + ARMOR_SLOTS.length;
        }
        return -1;
    }

    private void handleInventoryClick(int mx, int my, int button) {
        if (isOutsideInventory(mx, my)) {
            dropCarriedStack(button);
            return;
        }
        int gx = panelLeft(), gy = panelTop();
        if (!creativeInventoryLayout()) {
            if (button == 0 && clickRecipeBookButton(mx, my, gx + BOOK_X, gy + BOOK_Y)) return;
            if (clickRecipeBook(
                    mx, my, button, gy, 2, craftingIds, craftingCounts, hasCarriedStack())) return;
            if (button == 0 || button == 1) {
                int craftSlot = craftSlotAt(mx, my, gx, gy);
                if (craftSlot >= 0) {
                    clearRecipeRequirements();
                    clickCraftSlot(craftSlot, button);
                    return;
                }
                if (inside(mx, my, gx + RESULT_X - 1, gy + RESULT_Y - 1, CELL, CELL)) {
                    takeCraftResult();
                    return;
                }
            }
        }
        if (button == 0 && clickInventoryPage(mx, my)) return;

        int gearSlot = armorSlotAt(mx, my);
        if (gearSlot >= 0) {
            var gear = Equipment.gear(carriedId);
            if (carriedId <= 0 || gear != null && gear.slot == gearSlot && carriedCnt == 1) {
                int old = minecraft.player.equipment[gearSlot];
                int oldWear = Equipment.wear(minecraft.player, gearSlot);
                Equipment.equip(
                        minecraft.player, gearSlot, carriedId > 0 ? carriedId : 0, carriedWear);
                carriedWear = oldWear;
                carriedId = old > 0 ? old : -1;
                carriedCnt = old > 0 ? 1 : 0;
            }
            return;
        }
        clickPlayerInventorySlot(mx, my, button);
        super.onMouseClick(mx, my, button);
    }

    protected final void clickPlayerInventorySlot(int mx, int my, int button) {
        Inventory inv = minecraft.player.inventory;
        int n = inv.totalSize();
        int slot = inventorySlotAt(mx, my);
        if (slot < 0 || slot >= n) return;

        if (button == 0) {
            if (carriedId <= 0) pickUpAll(inv, slot);
            else placeAllOrMerge(inv, slot);
        } else if (button == 1) {
            if (carriedId <= 0) pickUpHalf(inv, slot);
            else giveOneInto(inv, slot, carriedId);

            draggingRMB = true;
            paintHotbarBand = inv.isHotbarIndex(slot);

            if (painted.length != n) {
                boolean[] np = new boolean[n];
                System.arraycopy(painted, 0, np, 0, Math.min(painted.length, n));
                painted = np;
            }
            for (int i = 0; i < painted.length; i++) painted[i] = false;
            painted[slot] = true;
        }
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
            int mx = Mouse.getX() * this.width / this.minecraft.width;
            int my = this.height - Mouse.getY() * this.height / this.minecraft.height - 1;

            Inventory inv = this.minecraft.player.inventory;
            int slot = inventorySlotAt(mx, my);
            int n = inv.totalSize();

            if (slot >= 0 && slot < n) {
                if (inv.isHotbarIndex(slot) != paintHotbarBand) return; // wrong band
                if (!painted[slot] && carriedId > 0 && carriedCnt > 0) {
                    giveOneInto(inv, slot, carriedId);
                    painted[slot] = true;
                }
            }
        }
    }

    @Override
    public void onClose() {
        flushCarriedBackIntoInventory();
        for (int i = 0; i < craftingIds.length; i++) {
            carriedId = craftingIds[i];
            carriedCnt = craftingCounts[i];
            craftingIds[i] = -1;
            craftingCounts[i] = 0;
            flushCarriedBackIntoInventory();
        }
        super.onClose();
    }

    @Override
    protected void onKeyPress(char ch, int key) {
        // Close on Inventory key or ESC
        if (key == this.minecraft.settings.inventoryKey.key || key == 1) {
            flushCarriedBackIntoInventory(); // <- add this
            this.minecraft.setCurrentScreen(null);
            this.minecraft.grabMouse();
            return;
        }
        super.onKeyPress(ch, key);
    }
}
