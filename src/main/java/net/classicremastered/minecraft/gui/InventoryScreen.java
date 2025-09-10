package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.render.ShapeRenderer;

public class InventoryScreen extends GuiScreen {
    // textures
    private int texture; // /gui/inventory.png
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

    // drag-paint band lock
    private boolean paintHotbarBand = false; // true = painting hotbar only; false = main grid (unused with 9 slots)

    @Override
    public void onOpen() {
        this.texture = this.minecraft.textureManager.load("/gui/inventory.png");
        this.terrainAtlas = this.minecraft.textureManager.load("/terrain.png");

        Inventory inv = this.minecraft.player.inventory;

        int n = inv.totalSize(); // hotbar + grid
        if (painted.length != n)
            painted = new boolean[n];

        carriedId = -1;
        carriedCnt = 0;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++)
            painted[i] = false;

        int main = Math.max(0, n - HOTBAR_SIZE);
        int perPage = ROWS_VISIBLE * 9; // 27
        totalPages = Math.max(1, (main + perPage - 1) / perPage);
        if (page >= totalPages)
            page = totalPages - 1;
        if (page < 0)
            page = 0;
    }

    // ---------- helpers ----------
    private static int maxStackFor(int id) {
        if (id >= 256) {
            int itemId = id - 256;
            if (itemId >= 0 && itemId < Item.items.length && Item.items[itemId] != null) {
                return Item.items[itemId].isTool() ? 1 : 99;
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
        if (id <= 0 || cnt <= 0)
            return;
        carriedId = id;
        carriedCnt = cnt;
        inv.setIdCount(slot, -1, 0);
    }

    private void pickUpHalf(Inventory inv, int slot) {
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);
        if (id <= 0 || cnt <= 0)
            return;
        int half = (cnt + 1) / 2;
        carriedId = id;
        carriedCnt = half;
        inv.setIdCount(slot, (cnt - half) > 0 ? id : -1, Math.max(0, cnt - half));
    }

    private void placeAllOrMerge(Inventory inv, int slot) {
        if (carriedId <= 0 || carriedCnt <= 0)
            return;

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
                if (carriedCnt <= 0)
                    carriedId = -1;
                return;
            }
        }
        inv.setIdCount(slot, carriedId, carriedCnt);
        carriedId = dstId;
        carriedCnt = dstCnt;
    }

    private void giveOneInto(Inventory inv, int slot, int id) {
        if (id <= 0 || carriedCnt <= 0)
            return;
        int dstId = inv.getId(slot);
        int dstCnt = inv.getCount(slot);

        if (dstId <= 0) {
            inv.setIdCount(slot, id, 1);
            if (--carriedCnt <= 0)
                carriedId = -1;
            return;
        }
        if (dstId == id && dstCnt < maxStackFor(id)) {
            inv.setIdCount(slot, dstId, dstCnt + 1);
            if (--carriedCnt <= 0)
                carriedId = -1;
        }
    }

    // slot → screen coords (HOTBAR ONLY)
    // slot → screen coords (global index)
    private void slotXY(int slot, int[] out) {
        int x0 = (this.width - GUI_W) / 2;
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
        if (vis < 0)
            vis = 0;
        int col = vis % 9;
        int row = vis / 9; // 0..ROWS_VISIBLE-1
        out[0] = x0 + 8 + col * CELL;
        out[1] = y0 + 84 + row * CELL;
    }

    // mouse → global slot index, or -1
    private int slotAt(int mx, int my) {
        int x0 = (this.width - GUI_W) / 2;
        int y0 = (this.height - GUI_H) / 2;

        // hotbar band
        int hbY = y0 + 142;
        if (my >= hbY && my < hbY + 16) {
            int rel = mx - (x0 + 8);
            if (rel >= 0) {
                int col = rel / CELL;
                if (col >= 0 && col < HOTBAR_SIZE)
                    return col; // 0..8
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
        int x0 = (this.width - GUI_W) / 2, y0 = (this.height - GUI_H) / 2;
        return new int[] { x0 + GUI_W - 38, y0 + 6, x0 + GUI_W - 26, y0 + 18 };
    }

    private int[] rightBtnRect() {
        int x0 = (this.width - GUI_W) / 2, y0 = (this.height - GUI_H) / 2;
        return new int[] { x0 + GUI_W - 20, y0 + 6, x0 + GUI_W - 8, y0 + 18 };
    }

    private boolean inRect(int x, int y, int[] r) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // ---------- render ----------
    @Override
    public void render(int mouseX, int mouseY) {
        final int x0 = (this.width - GUI_W) / 2;
        final int y0 = (this.height - GUI_H) / 2;

        // --- background ---
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(x0, y0, 0, 0, GUI_W, GUI_H);

        // --- player preview (LEAVE AS IS / DO NOT TOUCH) ---
        {
            net.classicremastered.minecraft.player.Player pl = this.minecraft.player;
            if (pl != null) {
                net.classicremastered.minecraft.model.HumanoidModel m = pl.getModel();
                if (m != null) {
                    final int pvX = x0 + 8; // black square left
                    final int pvY = y0 + 18; // black square top
                    final int pvW = 72, pvH = 90;

                    // mouse → head look
                    float cx = pvX + pvW * 0.5f, cy = pvY + pvH * 0.5f;
                    float netHeadYawDeg = (mouseX - cx) * 0.6f; // left/right
                    float headPitchDeg = (cy - mouseY) * 0.6f; // up/down

                    this.minecraft.renderer.setLighting(true);

                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_NORMALIZE);
                    GL11.glEnable(GL11.GL_COLOR_MATERIAL);

                    // Put him inside the pane; z=50 → eye z = -150 (visible with your ortho)
                    GL11.glTranslatef(pvX + pvW * 0.6f, pvY + pvH - 75.0f, 50.0f);

                    // SCALE: flip Y only (no 180° Z); 30 is a good size
                    GL11.glScalef(30.0f, -30.0f, 30.0f);

                    // FACE CAMERA: turn around Y
                    GL11.glRotatef(180.0f, 0.0f, 1.0f, 0.0f);

                    // slight forward tilt so shoulders are visible
                    GL11.glRotatef(539.0f, 0.0f, 0.0f, 40f);

                    // bind skin and render neutral pose
                    pl.bindTexture(this.minecraft.textureManager);
                    m.render(0f, 0f, 0f, // limbSwing, limbSwingAmount, ageInTicks
                            netHeadYawDeg, // netHeadYaw (degrees)
                            headPitchDeg, // headPitch (degrees)
                            0.0625f // model scale (1/16)
                    );

                    GL11.glDisable(GL11.GL_NORMALIZE);
                    GL11.glDisable(GL11.GL_COLOR_MATERIAL);
                    GL11.glPopMatrix();

                    GL11.glDisable(GL11.GL_LIGHTING);
                    this.minecraft.renderer.setLighting(false);
                }
            }
        }

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

        for (int i = base; i < end; i++)
            drawCount(inv, i);
        for (int i = 0; i < Math.min(HOTBAR_SIZE, n); i++)
            drawCount(inv, i);

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

        if (carriedId > 0 && carriedCnt > 0) {
            int cx = mouseX - 8, cy = mouseY - 8;
            drawIdIcon(carriedId, cx, cy);
            if (carriedCnt > 1) {
                String t = Integer.toString(carriedCnt);
                this.fontRenderer.render(t, cx + 16 - this.fontRenderer.getWidth(t), cy + 10, 0xFFFFFF);
            }
        }

        if (depthWas)
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        super.render(mouseX, mouseY);
    }

    // ensure model part has a GL list
    private static void ensureList(net.classicremastered.minecraft.model.ModelPart p) {
        if (p != null && !p.hasList)
            p.generateList(0.0625F);
    }

    // Player preview (HumanoidModel) in the black square
    private void drawPlayerPreview(int x, int y, int w, int h, int mouseX, int mouseY) {
        net.classicremastered.minecraft.player.Player pl = this.minecraft.player;
        if (pl == null)
            return;
        net.classicremastered.minecraft.model.HumanoidModel m = pl.getModel();
        if (m == null)
            return;

        float cx = x + w * 0.5f;
        float cy = y + h * 0.5f;
        float yaw = (mouseX - cx) * 0.3f;

        this.minecraft.renderer.setLighting(true);

        GL11.glPushMatrix();
        GL11.glTranslatef(x + w * 0.5f, y + h - 6.0f, 100.0f);
        GL11.glScalef(40.0f, 40.0f, 40.0f);
        GL11.glRotatef(180f, 0f, 0f, 1f);
        GL11.glRotatef(12f, 1f, 0f, 0f);
        GL11.glRotatef(-yaw, 0f, 1f, 0f);

        pl.bindTexture(this.minecraft.textureManager);

        ensureList(m.head);
        ensureList(m.body);
        ensureList(m.leftArm);
        ensureList(m.rightArm);
        ensureList(m.leftLeg);
        ensureList(m.rightLeg);

        GL11.glCallList(m.head.list);
        GL11.glCallList(m.body.list);
        GL11.glCallList(m.rightArm.list);
        GL11.glCallList(m.leftArm.list);
        GL11.glCallList(m.rightLeg.list);
        GL11.glCallList(m.leftLeg.list);

        GL11.glPopMatrix();
        this.minecraft.renderer.setLighting(false);
    }

    private void drawSlotIcon(Inventory inv, int slot) {
        int n = inv.totalSize();
        if (slot < 0 || slot >= n)
            return;
        int id = inv.getId(slot);
        if (id <= 0)
            return;
        int[] xy = new int[2];
        slotXY(slot, xy);
        drawIdIcon(id, xy[0], xy[1]);
    }

    private void drawCount(Inventory inv, int slot) {
        int n = inv.totalSize();
        if (slot < 0 || slot >= n)
            return;
        int c = inv.getCount(slot);
        if (c > 1) {
            int[] xy = new int[2];
            slotXY(slot, xy);
            String t = Integer.toString(c);
            this.fontRenderer.render(t, xy[0] + 16 - this.fontRenderer.getWidth(t), xy[1] + 10, 0xFFFFFF);
        }
    }

    // Draw a block as a tiny 3D cube (BlockSelect transform) or a 2D item icon
    private void drawIdIcon(int id, int sx, int sy) {
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
                if (it != null && it.getTexture() != null) {
                    int tex = this.minecraft.textureManager.load(it.getTexture());
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                    ShapeRenderer.instance.begin();
                    ShapeRenderer.instance.vertexUV(sx, sy + 16, 0, 0, 1);
                    ShapeRenderer.instance.vertexUV(sx + 16, sy + 16, 0, 1, 1);
                    ShapeRenderer.instance.vertexUV(sx + 16, sy, 0, 1, 0);
                    ShapeRenderer.instance.vertexUV(sx, sy, 0, 0, 0);
                    ShapeRenderer.instance.end();
                }
            }
        }
    }

    // Return the carried stack to the inventory (merge, then empty slots)
    private void flushCarriedBackIntoInventory() {
        if (carriedId <= 0 || carriedCnt <= 0)
            return;

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

        // Done: clear cursor + drag state
        carriedId = -1;
        draggingRMB = false;
        for (int i = 0; i < painted.length; i++)
            painted[i] = false;
    }

    // ---------- input ----------
    @Override
    protected void onMouseClick(int mx, int my, int button) {
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

        int n = inv.totalSize();
        int slot = slotAt(mx, my);
        if (slot < 0 || slot >= n) {
            super.onMouseClick(mx, my, button);
            return;
        }

        if (button == 0) {
            if (carriedId <= 0)
                pickUpAll(inv, slot);
            else
                placeAllOrMerge(inv, slot);
        } else if (button == 1) {
            if (carriedId <= 0)
                pickUpHalf(inv, slot);
            else
                giveOneInto(inv, slot, carriedId);

            draggingRMB = true;
            paintHotbarBand = inv.isHotbarIndex(slot);

            if (painted.length != n) {
                boolean[] np = new boolean[n];
                System.arraycopy(painted, 0, np, 0, Math.min(painted.length, n));
                painted = np;
            }
            for (int i = 0; i < painted.length; i++)
                painted[i] = false;
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
                for (int i = 0; i < painted.length; i++)
                    painted[i] = false;
                return;
            }
            int mx = Mouse.getX() * this.width / this.minecraft.width;
            int my = this.height - Mouse.getY() * this.height / this.minecraft.height - 1;

            Inventory inv = this.minecraft.player.inventory;
            int slot = slotAt(mx, my);
            int n = inv.totalSize();

            if (slot >= 0 && slot < n) {
                if (inv.isHotbarIndex(slot) != paintHotbarBand)
                    return; // wrong band
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
        super.onClose();
    }
    @Override
    protected void onKeyPress(char ch, int key) {
        // Close on Inventory key or ESC
        if (key == this.minecraft.settings.inventoryKey.key || key == 1) {
            flushCarriedBackIntoInventory();  // <- add this
            this.minecraft.setCurrentScreen(null);
            this.minecraft.grabMouse();
            return;
        }
        super.onKeyPress(ch, key);
    }
}
