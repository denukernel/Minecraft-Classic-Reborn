package net.classicremastered.minecraft.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.classicremastered.minecraft.crafting.CraftingManager;
import net.classicremastered.minecraft.crafting.Recipe;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.render.ShapeRenderer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class InventoryScreen extends GuiScreen {
    // textures
    protected int texture; // /gui/inventory.png
    private int terrainAtlas; // /terrain.png

    // carried stack (cursor)
    private int carriedId = -1;
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
    private static final int GUI_W = 176;
    private static final int GUI_H = 166;
    private static final int CELL = 18;
    private static final int BOOK_W = 86;
    private static final int RECIPE_PAGE = 24;
    private static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private final int[] craftingIds = {-1, -1, -1, -1};
    private final int[] craftingCounts = new int[4];
    private boolean recipeBook;
    private boolean craftableOnly;
    private int recipePage;

    private int panelLeft() {
        return (width - GUI_W + (recipeBook && width >= GUI_W + BOOK_W + 8 ? BOOK_W + 4 : 0)) / 2;
    }

    private CraftingManager.ItemStack craftResult() {
        return CraftingManager.getInstance().findMatchingRecipe(craftingIds, 2);
    }

    private List<Recipe> bookRecipes() {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : CraftingManager.getInstance().getRecipes()) {
            if (recipe.width <= 2
                    && recipe.height <= 2
                    && (!craftableOnly || ingredientSlots(recipe) != null)) result.add(recipe);
        }
        recipePage = Math.min(recipePage, Math.max(0, (result.size() - 1) / RECIPE_PAGE));
        return result;
    }

    private int[] ingredientSlots(Recipe recipe) {
        Inventory inv = minecraft.player.inventory;
        int[] used = new int[inv.totalSize()];
        int[] slots = new int[recipe.ingredients.length];
        Arrays.fill(slots, -1);
        for (int i = 0; i < slots.length; i++) {
            int id = recipe.ingredients[i];
            if (id <= 0) continue;
            for (int slot = 0; slot < used.length; slot++) {
                if (Item.inventoryId(inv.getId(slot)) == id && inv.getCount(slot) > used[slot]) {
                    slots[i] = slot;
                    used[slot]++;
                    break;
                }
            }
            if (slots[i] < 0) return null;
        }
        return slots;
    }

    private void selectRecipe(Recipe recipe) {
        if (carriedId > 0) return;
        for (int id : craftingIds) if (id > 0) return;
        int[] slots = ingredientSlots(recipe);
        if (slots == null) return;
        Inventory inv = minecraft.player.inventory;
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot < 0) continue;
            int grid = i % recipe.width + i / recipe.width * 2;
            int id = inv.getId(slot), remaining = inv.getCount(slot) - 1;
            craftingIds[grid] = id;
            craftingCounts[grid] = 1;
            inv.setIdCount(slot, remaining == 0 ? -1 : id, remaining);
        }
    }

    private void clickCraftSlot(int slot, int button) {
        int id = craftingIds[slot], count = craftingCounts[slot];
        if (carriedId <= 0) {
            if (id <= 0) return;
            carriedId = id;
            carriedCnt = button == 1 ? (count + 1) / 2 : count;
            craftingCounts[slot] -= carriedCnt;
        } else if (id <= 0 || id == carriedId) {
            int moved = Math.min(button == 1 ? 1 : carriedCnt, maxStackFor(carriedId) - count);
            if (moved == 0) return;
            craftingIds[slot] = carriedId;
            craftingCounts[slot] += moved;
            carriedCnt -= moved;
            if (carriedCnt == 0) carriedId = -1;
        } else if (button == 0) {
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
                || carriedCnt + result.count > maxStackFor(result.id)) return;
        carriedId = result.id;
        carriedCnt += result.count;
        CraftingManager.consumeIngredients(craftingIds, craftingCounts);
    }

    private void drawArmorSilhouette(int slot, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(
                    GL11.GL_TEXTURE_2D,
                    minecraft.textureManager.load("/gui/armor_slot_" + ARMOR_SLOTS[slot] + ".png"));
            ShapeRenderer shape = ShapeRenderer.instance;
            shape.begin();
            shape.vertexUV(x, y + 16, 0, 0, 1);
            shape.vertexUV(x + 16, y + 16, 0, 1, 1);
            shape.vertexUV(x + 16, y, 0, 1, 0);
            shape.vertexUV(x, y, 0, 0, 0);
            shape.end();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawCraftSlot(int x, int y, int id, int count) {
        drawBox(x - 1, y - 1, x + 17, y + 17, 0xFF494949);
        drawBox(x, y, x + 16, y + 16, 0xFF909090);
        if (id <= 0) return;
        drawIdIcon(id, x, y);
        if (count > 1) drawString(fontRenderer, Integer.toString(count), x + 8, y + 9, 0xFFFFFF);
    }

    private int bookLeft() {
        return width >= GUI_W + BOOK_W + 8 ? panelLeft() - BOOK_W - 4 : panelLeft();
    }

    private void drawRecipeBook(int y) {
        int x = bookLeft();
        drawBox(x, y, x + BOOK_W, y + GUI_H, 0xF0303030);
        drawString(
                fontRenderer, craftableOnly ? "Craftable" : "All recipes", x + 6, y + 6, 0xFFFFFF);
        List<Recipe> recipes = bookRecipes();
        for (int i = 0; i < RECIPE_PAGE && recipePage * RECIPE_PAGE + i < recipes.size(); i++) {
            Recipe recipe = recipes.get(recipePage * RECIPE_PAGE + i);
            drawCraftSlot(
                    x + 6 + i % 4 * CELL,
                    y + 20 + i / 4 * CELL,
                    recipe.resultId,
                    recipe.resultCount);
        }
        drawString(fontRenderer, "<    " + (recipePage + 1) + "    >", x + 8, y + 135, 0xFFFFFF);
    }

    private boolean clickRecipeBook(int mx, int my, int y) {
        int x = bookLeft();
        if (!recipeBook || mx < x || mx >= x + BOOK_W || my < y || my >= y + GUI_H) return false;
        if (my < y + 18) {
            craftableOnly = !craftableOnly;
            recipePage = 0;
        } else if (my >= y + 130) {
            recipePage = Math.max(0, recipePage + (mx < x + BOOK_W / 2 ? -1 : 1));
            bookRecipes();
        } else if (mx >= x + 6 && mx < x + 78 && my >= y + 20 && my < y + 128) {
            int index = recipePage * RECIPE_PAGE + (mx - x - 6) / CELL + (my - y - 20) / CELL * 4;
            List<Recipe> recipes = bookRecipes();
            if (index < recipes.size()) selectRecipe(recipes.get(index));
        }
        return true;
    }

    // drag-paint band lock
    private boolean paintHotbarBand =
            false; // true = painting hotbar only; false = main grid (unused with 9 slots)

    @Override
    public void onOpen() {
        this.texture = this.minecraft.textureManager.load("/gui/inventory.png");
        this.terrainAtlas =
                this.minecraft.textureManager.load(
                        net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE);

        Inventory inv = this.minecraft.player.inventory;

        int n = inv.totalSize(); // hotbar + grid
        if (painted.length != n) painted = new boolean[n];

        carriedId = -1;
        carriedCnt = 0;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++) painted[i] = false;

        int main = Math.max(0, n - HOTBAR_SIZE);
        int perPage = ROWS_VISIBLE * 9; // 27
        totalPages = Math.max(1, (main + perPage - 1) / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
    }

    // ---------- helpers ----------
    private static int maxStackFor(int id) {
        if (id >= 256) {
            int itemId = id - 256;
            if (itemId >= 0 && itemId < Item.items.length && Item.items[itemId] != null) {
                return Item.items[itemId].maxStackSize;
            }
            return 99;
        }
        return 99;
    }

    private static boolean sameId(int a, int b) {
        return a > 0 && a == b;
    }

    private void pickUpAll(Inventory inv, int slot) {
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);
        if (id <= 0 || cnt <= 0) return;
        carriedId = id;
        carriedCnt = cnt;
        inv.setIdCount(slot, -1, 0);
    }

    private void pickUpHalf(Inventory inv, int slot) {
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);
        if (id <= 0 || cnt <= 0) return;
        int half = (cnt + 1) / 2;
        carriedId = id;
        carriedCnt = half;
        inv.setIdCount(slot, (cnt - half) > 0 ? id : -1, Math.max(0, cnt - half));
    }

    private void placeAllOrMerge(Inventory inv, int slot) {
        if (carriedId <= 0 || carriedCnt <= 0) return;

        int dstId = inv.getId(slot);
        int dstCnt = inv.getCount(slot);
        int cap = maxStackFor(carriedId);

        if (dstId <= 0) {
            inv.setIdCount(slot, carriedId, carriedCnt);
            carriedId = -1;
            carriedCnt = 0;
            return;
        }
        if (dstId == carriedId) {
            int can = Math.max(0, cap - dstCnt);
            if (can > 0) {
                int put = Math.min(can, carriedCnt);
                inv.setIdCount(slot, dstId, dstCnt + put);
                carriedCnt -= put;
                if (carriedCnt <= 0) carriedId = -1;
                return;
            }
        }
        inv.setIdCount(slot, carriedId, carriedCnt);
        carriedId = dstId;
        carriedCnt = dstCnt;
    }

    private void giveOneInto(Inventory inv, int slot, int id) {
        if (id <= 0 || carriedCnt <= 0) return;
        int dstId = inv.getId(slot);
        int dstCnt = inv.getCount(slot);

        if (dstId <= 0) {
            inv.setIdCount(slot, id, 1);
            if (--carriedCnt <= 0) carriedId = -1;
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
        int y0 = (this.height - GUI_H) / 2;

        if (slot < HOTBAR_SIZE) { // hotbar
            out[0] = x0 + 8 + slot * CELL;
            out[1] = y0 + 142;
            return;
        }
        // grid (paged 27)
        int perPage = ROWS_VISIBLE * 9; // 27
        int base = HOTBAR_SIZE + page * perPage;
        int vis = slot - base;
        if (vis < 0) vis = 0;
        int col = vis % 9;
        int row = vis / 9; // 0..ROWS_VISIBLE-1
        out[0] = x0 + 8 + col * CELL;
        out[1] = y0 + 84 + row * CELL;
    }

    // mouse → global slot index, or -1
    private int slotAt(int mx, int my) {
        int x0 = panelLeft();
        int y0 = (this.height - GUI_H) / 2;

        // hotbar band
        int hbY = y0 + 142;
        if (my >= hbY && my < hbY + 16) {
            int rel = mx - (x0 + 8);
            if (rel >= 0) {
                int col = rel / CELL;
                if (col >= 0 && col < HOTBAR_SIZE) return col; // 0..8
            }
        }

        // grid band (ROWS_VISIBLE rows)
        int perPage = ROWS_VISIBLE * 9;
        int base = HOTBAR_SIZE + page * perPage;

        int topY = y0 + 84, botY = topY + ROWS_VISIBLE * CELL;
        if (my >= topY && my < botY) {
            int relX = mx - (x0 + 8);
            if (relX >= 0) {
                int col = relX / CELL;
                int row = (my - topY) / CELL;
                if (col >= 0 && col < 9 && row >= 0 && row < ROWS_VISIBLE) {
                    return base + row * 9 + col;
                }
            }
        }
        return -1;
    }

    // page buttons rects (kept for UI; no storage beyond hotbar yet)
    private int[] leftBtnRect() {
        int x0 = panelLeft(), y0 = (this.height - GUI_H) / 2;
        return new int[] {x0 + GUI_W - 38, y0 + 6, x0 + GUI_W - 26, y0 + 18};
    }

    private int[] rightBtnRect() {
        int x0 = panelLeft(), y0 = (this.height - GUI_H) / 2;
        return new int[] {x0 + GUI_W - 20, y0 + 6, x0 + GUI_W - 8, y0 + 18};
    }

    private boolean inRect(int x, int y, int[] r) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // ---------- render ----------
    @Override
    public void render(int mouseX, int mouseY) {
        final int x0 = panelLeft();
        final int y0 = (this.height - GUI_H) / 2;

        // --- background ---
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(x0, y0, 0, 0, GUI_W, GUI_H);

        // Draw tabs if in creative mode
        if (this.minecraft != null && !this.minecraft.gamemode.isSurvival()) {
            int cx1 = x0 + 10;
            int cx2 = x0 + 65;
            int cy1 = y0 - 15;
            int cy2 = y0;

            int sx1 = x0 + 70;
            int sx2 = x0 + 125;
            int sy1 = y0 - 15;
            int sy2 = y0;

            // Draw Blocks tab (inactive)
            boolean hoverCreative = mouseX >= cx1 && mouseX < cx2 && mouseY >= cy1 && mouseY < cy2;
            int cBg = hoverCreative ? 0x60FFFFFF : 0x30FFFFFF;
            drawBox(cx1, cy1, cx2, cy2, cBg);
            drawCenteredString(this.fontRenderer, "Blocks", cx1 + 27, cy1 + 4, 14737632);

            // Draw Survival tab (active)
            drawBox(sx1, sy1, sx2, sy2, -1878719232); // matching GUI background
            drawCenteredString(this.fontRenderer, "Survival", sx1 + 27, sy1 + 4, 16777120);
        }

        drawPlayerPreview(x0 + 31, y0 + 17, 61, 65, mouseX, mouseY);
        for (int slot = 0; slot < 4; slot++) {
            int sx = x0 + 8, sy = y0 + 8 + slot * 18;
            drawBox(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF444444);
            if (minecraft.player.equipment[slot] > 0)
                drawIdIcon(minecraft.player.equipment[slot], sx, sy);
            else drawArmorSilhouette(slot, sx, sy);
        }
        drawString(fontRenderer, "Crafting", x0 + 98, y0 + 8, 0xFFFFFF);
        for (int i = 0; i < 4; i++)
            drawCraftSlot(
                    x0 + 98 + (i % 2) * CELL,
                    y0 + 19 + (i / 2) * CELL,
                    craftingIds[i],
                    craftingCounts[i]);
        drawString(fontRenderer, ">", x0 + 135, y0 + 34, 0xFFFFFF);
        var result = craftResult();
        drawCraftSlot(
                x0 + 151,
                y0 + 29,
                result == null ? -1 : result.id,
                result == null ? 0 : result.count);
        drawBox(x0 + 98, y0 + 61, x0 + 167, y0 + 76, 0xFF555555);
        drawCenteredString(
                fontRenderer, recipeBook ? "Close book" : "Recipes", x0 + 132, y0 + 65, 0xFFFFFF);
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
        final boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        for (int i = base; i < end; i++) drawCount(inv, i);
        for (int i = 0; i < Math.min(HOTBAR_SIZE, n); i++) drawCount(inv, i);

        if (recipeBook) drawRecipeBook(y0);
        int hover = slotAt(mouseX, mouseY);
        if (hover >= 0 && hover < n) {
            int[] xy = new int[2];
            slotXY(hover, xy);
            int sx = xy[0], sy = xy[1];
            drawBox(sx - 1, sy - 1, sx + 17, sy, 0x80FFFFFF);
            drawBox(sx - 1, sy + 16, sx + 17, sy + 17, 0x80FFFFFF);
            drawBox(sx - 1, sy, sx, sy + 16, 0x80FFFFFF);
            drawBox(sx + 16, sy, sx + 17, sy + 16, 0x80FFFFFF);
        }

        int tooltipRaw = hover >= 0 && hover < n ? inv.getId(hover) : -1;
        int armorX = panelLeft() + 8, armorY = (height - GUI_H) / 2 + 8;
        if (mouseX >= armorX && mouseX < armorX + 16 && mouseY >= armorY && mouseY < armorY + 72)
            tooltipRaw = minecraft.player.equipment[(mouseY - armorY) / 18];
        if (carriedId <= 0 && tooltipRaw > 0) {
            String name = BlockSelectScreen.displayName(tooltipRaw),
                    detail =
                            net.classicremastered.minecraft.level.itemstack.Equipment.description(
                                    tooltipRaw);
            int tw = Math.max(fontRenderer.getWidth(name), fontRenderer.getWidth(detail)),
                    tx = Math.max(3, Math.min(width - tw - 6, mouseX + 10)),
                    ty = Math.min(height - 28, mouseY + 10);
            drawBox(tx - 3, ty - 3, tx + tw + 3, ty + 23, 0xEF111111);
            drawString(fontRenderer, name, tx, ty, 0xFFFF80);
            drawString(fontRenderer, detail, tx, ty + 12, 0xFFFFFF);
        }
        if (carriedId > 0 && carriedCnt > 0) {
            int cx = mouseX - 8, cy = mouseY - 8;
            drawIdIcon(carriedId, cx, cy);
            if (carriedCnt > 1) {
                String t = Integer.toString(carriedCnt);
                this.fontRenderer.render(
                        t, cx + 16 - this.fontRenderer.getWidth(t), cy + 10, 0xFFFFFF);
            }
        }

        if (depthWas) GL11.glEnable(GL11.GL_DEPTH_TEST);
        super.render(mouseX, mouseY);
    }

    // ensure model part has a GL list
    private static void ensureList(net.classicremastered.minecraft.model.ModelPart p) {
        if (p != null && !p.hasList) p.generateList(0.0625F);
    }

    private net.classicremastered.minecraft.model.HumanoidModel preview;

    private void drawPlayerPreview(int x, int y, int w, int h, int mx, int my) {
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
            GL11.glTranslatef(x + w * .5f, y + 16, 80);
            GL11.glScalef(27, 27, 27);
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
    }

    private void drawCount(Inventory inv, int slot) {
        int n = inv.totalSize();
        if (slot < 0 || slot >= n) return;
        int c = inv.getCount(slot);
        if (c > 1) {
            int[] xy = new int[2];
            slotXY(slot, xy);
            String t = Integer.toString(c);
            this.fontRenderer.render(
                    t, xy[0] + 16 - this.fontRenderer.getWidth(t), xy[1] + 10, 0xFFFFFF);
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
                inv.setIdCount(i, carriedId, put);
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
                                carriedCnt));
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
        // Done: clear cursor + drag state
        carriedId = -1;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++) painted[i] = false;
    }

    // ---------- input ----------
    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (this.minecraft != null && !this.minecraft.gamemode.isSurvival()) {
            int x0 = panelLeft();
            int y0 = (this.height - GUI_H) / 2;
            int cx1 = x0 + 10;
            int cx2 = x0 + 65;
            int cy1 = y0 - 15;
            int cy2 = y0;
            if (mx >= cx1 && mx < cx2 && my >= cy1 && my < cy2 && button == 0) {
                flushCarriedBackIntoInventory();
                this.minecraft.setCurrentScreen(new BlockSelectScreen());
                return;
            }
        }

        int gx = panelLeft(), gy = (height - GUI_H) / 2;
        if (button == 0 && mx >= gx + 98 && mx < gx + 167 && my >= gy + 61 && my < gy + 76) {
            recipeBook = !recipeBook;
            return;
        }
        if (button == 0 && clickRecipeBook(mx, my, gy)) return;
        if (button == 0 || button == 1) {
            if (mx >= gx + 98 && mx < gx + 134 && my >= gy + 19 && my < gy + 55) {
                clickCraftSlot((mx - gx - 98) / CELL + (my - gy - 19) / CELL * 2, button);
                return;
            }
            if (mx >= gx + 151 && mx < gx + 168 && my >= gy + 29 && my < gy + 46) {
                takeCraftResult();
                return;
            }
        }
        Inventory inv = this.minecraft.player.inventory;

        if (totalPages > 1) {
            if (inRect(mx, my, leftBtnRect())) {
                page = (page + totalPages - 1) % totalPages;
                return;
            }
            if (inRect(mx, my, rightBtnRect())) {
                page = (page + 1) % totalPages;
                return;
            }
        }

        int ax = panelLeft() + 8, ay = (height - GUI_H) / 2 + 8;
        if (mx >= ax && mx < ax + 16 && my >= ay && my < ay + 72) {
            int gearSlot = (my - ay) / 18;
            var gear = net.classicremastered.minecraft.level.itemstack.Equipment.gear(carriedId);
            if (carriedId <= 0 || gear != null && gear.slot == gearSlot) {
                int old = minecraft.player.equipment[gearSlot];
                minecraft.player.equipment[gearSlot] = carriedId > 0 ? carriedId : 0;
                carriedId = old > 0 ? old : -1;
                carriedCnt = old > 0 ? 1 : 0;
            }
            return;
        }
        int n = inv.totalSize();
        int slot = slotAt(mx, my);
        if (slot < 0 || slot >= n) {
            super.onMouseClick(mx, my, button);
            return;
        }

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
        super.onMouseClick(mx, my, button);
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
            int slot = slotAt(mx, my);
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
