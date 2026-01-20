package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.render.ShapeRenderer;

public final class GuiCrafting extends GuiScreen {

    private int texture;
    private static final int GRID_START = 9;
    private static final int GRID_SIZE = 9;
    private static final int OUTPUT_SLOT = 18;

    private int carriedId = -1;
    private int carriedCnt = 0;

    @Override
    public void onOpen() {
        this.texture = minecraft.textureManager.load("/gui/crafting.png");
    }

    @Override
    public void onClose() {
        flushCarriedBack();
        super.onClose();
    }

    private void flushCarriedBack() {
        if (carriedId <= 0 || carriedCnt <= 0) return;
        Player p = minecraft.player;
        Inventory inv = p.inventory;
        int cap = 99;

        for (int i = 0; i < inv.totalSize() && carriedCnt > 0; i++) {
            int id = inv.getId(i);
            int ct = inv.getCount(i);
            if (id == carriedId && ct < cap) {
                int put = Math.min(cap - ct, carriedCnt);
                inv.setIdCount(i, id, ct + put);
                carriedCnt -= put;
            }
        }
        for (int i = 0; i < inv.totalSize() && carriedCnt > 0; i++) {
            int id = inv.getId(i);
            int ct = inv.getCount(i);
            if (id <= 0 || ct <= 0) {
                int put = Math.min(cap, carriedCnt);
                inv.setIdCount(i, carriedId, put);
                carriedCnt -= put;
            }
        }
        carriedId = -1;
        carriedCnt = 0;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        final int GUI_W = 176, GUI_H = 166;
        final int x0 = (width - GUI_W) / 2;
        final int y0 = (height - GUI_H) / 2;

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(x0, y0, 0, 0, GUI_W, GUI_H);

        Inventory inv = minecraft.player.inventory;

        int gridX = x0 + 29, gridY = y0 + 16;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slot = GRID_START + (r * 3 + c);
                int id = inv.getId(slot);
                if (id > 0) drawSlotIcon(id, gridX + c * 18, gridY + r * 18);
            }
        }

        int outX = x0 + 124, outY = y0 + 35;
        int outId = inv.getId(OUTPUT_SLOT);
        if (outId > 0) drawSlotIcon(outId, outX, outY);

        if (carriedId > 0) {
            drawSlotIcon(carriedId, mouseX - 8, mouseY - 8);
            if (carriedCnt > 1) {
                String s = Integer.toString(carriedCnt);
                fontRenderer.render(s, mouseX + 8, mouseY + 8, 0xFFFFFFFF);
            }
        }
    }

    @Override
    protected void onMouseClick(int mouseX, int mouseY, int button) {
        final int GUI_W = 176, GUI_H = 166;
        final int x0 = (width - GUI_W) / 2;
        final int y0 = (height - GUI_H) / 2;

        Inventory inv = minecraft.player.inventory;

        int gridX = x0 + 29, gridY = y0 + 16;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = gridX + c * 18;
                int sy = gridY + r * 18;
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    handleSlotClick(inv, GRID_START + (r * 3 + c), button);
                    return;
                }
            }
        }

        int outX = x0 + 124, outY = y0 + 35;
        if (mouseX >= outX && mouseX < outX + 18 && mouseY >= outY && mouseY < outY + 18) {
            handleOutputClick(inv, button);
            return;
        }
    }

    private void handleSlotClick(Inventory inv, int slot, int button) {
        int id = inv.getId(slot);
        int cnt = inv.getCount(slot);

        if (button == 0) {
            if (carriedId <= 0 && cnt > 0) {
                carriedId = id;
                carriedCnt = cnt;
                inv.setIdCount(slot, -1, 0);
            } else if (carriedId > 0) {
                if (id <= 0) {
                    inv.setIdCount(slot, carriedId, carriedCnt);
                    carriedId = -1;
                    carriedCnt = 0;
                } else if (id == carriedId && cnt < 99) {
                    int add = Math.min(99 - cnt, carriedCnt);
                    inv.setIdCount(slot, id, cnt + add);
                    carriedCnt -= add;
                    if (carriedCnt <= 0) carriedId = -1;
                }
            }
        }

        if (button == 1) {
            if (carriedId <= 0 && cnt > 0) {
                int half = (cnt + 1) / 2;
                carriedId = id;
                carriedCnt = half;
                inv.setIdCount(slot, (cnt - half) > 0 ? id : -1, cnt - half);
            } else if (carriedId > 0) {
                if (id <= 0) {
                    inv.setIdCount(slot, carriedId, 1);
                } else if (id == carriedId && cnt < 99) {
                    inv.setIdCount(slot, id, cnt + 1);
                } else return;
                if (--carriedCnt <= 0) carriedId = -1;
            }
        }

        updateResult(inv);
    }

    private void handleOutputClick(Inventory inv, int button) {
        int outId = inv.getId(OUTPUT_SLOT);
        int outCnt = inv.getCount(OUTPUT_SLOT);
        if (outId <= 0 || outCnt <= 0) return;

        Player p = minecraft.player;
        int added = 0;
        for (int i = 0; i < outCnt; i++)
            if (p.inventory.addResource(outId)) added++;

        if (added <= 0) return;

        consumeIngredients(inv);
        inv.setIdCount(OUTPUT_SLOT, (outCnt - added) > 0 ? outId : -1, outCnt - added);

        updateResult(inv);
    }

    private void updateResult(Inventory inv) {
        int[] grid = new int[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++)
            grid[i] = inv.getId(GRID_START + i);

        if (grid[4] == Block.WOOD.id && countNonAir(grid, 4) == 1) {
            inv.setIdCount(OUTPUT_SLOT, Block.WOOD.id, 4);
            return;
        }

        if (grid[0] == Block.WOOD.id && grid[1] == Block.WOOD.id &&
            grid[3] == Block.WOOD.id && grid[4] == Block.WOOD.id &&
            countNonAir(grid, 0,1,3,4) == 4) {
            inv.setIdCount(OUTPUT_SLOT, Block.WORKBENCH.id, 1);
            return;
        }

        if (countAll(grid, Block.COBBLESTONE.id, inv) >= 8) {
            inv.setIdCount(OUTPUT_SLOT, Block.FURNACE.id, 1);
            return;
        }

        inv.setIdCount(OUTPUT_SLOT, -1, 0);
    }

    private void consumeIngredients(Inventory inv) {
        int[] grid = new int[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++)
            grid[i] = inv.getId(GRID_START + i);

        if (grid[4] == Block.WOOD.id && countNonAir(grid, 4) == 1) {
            decSlot(inv, GRID_START + 4, 1);
            return;
        }

        if (grid[0] == Block.WOOD.id && grid[1] == Block.WOOD.id &&
            grid[3] == Block.WOOD.id && grid[4] == Block.WOOD.id &&
            countNonAir(grid, 0,1,3,4) == 4) {
            decSlot(inv, GRID_START + 0, 1);
            decSlot(inv, GRID_START + 1, 1);
            decSlot(inv, GRID_START + 3, 1);
            decSlot(inv, GRID_START + 4, 1);
            return;
        }

        int rem = 8;
        for (int i = 0; i < GRID_SIZE && rem > 0; i++) {
            if (grid[i] == Block.COBBLESTONE.id) {
                int gl = GRID_START + i;
                int have = inv.getCount(gl);
                int take = Math.min(rem, have);
                decSlot(inv, gl, take);
                rem -= take;
            }
        }
    }

    private void decSlot(Inventory inv, int slot, int amt) {
        int ct = inv.getCount(slot);
        if (ct <= amt) inv.setIdCount(slot, -1, 0);
        else inv.setIdCount(slot, inv.getId(slot), ct - amt);
    }

    private int countAll(int[] grid, int id, Inventory inv) {
        int c = 0;
        for (int i = 0; i < GRID_SIZE; i++)
            if (grid[i] == id) c += inv.getCount(GRID_START + i);
        return c;
    }

    private int countNonAir(int[] grid, int... s) {
        int c = 0;
        for (int i : s) if (grid[i] != -1) c++;
        return c;
    }

    private void drawSlotIcon(int id, int x, int y) {
        if (id < 256 && Block.blocks[id] != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 8, y + 8, 0f);
            GL11.glScalef(10f, 10f, 10f);
            GL11.glTranslatef(0.8f, 0.8f, 8f);
            GL11.glRotatef(-30f, 1f, 0f, 0f);
            GL11.glRotatef(45f, 0f, 1f, 0f);
            GL11.glTranslatef(-1.5f, 0.5f, 0.5f);
            GL11.glScalef(-1f, -1f, -1f);
            int tex = minecraft.textureManager.load("/terrain.png");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            ShapeRenderer.instance.begin();
            Block.blocks[id].renderFullbright(ShapeRenderer.instance);
            ShapeRenderer.instance.end();
            GL11.glPopMatrix();
        } else {
            int idx = id - 256;
            if (idx >= 0 && idx < Item.items.length && Item.items[idx] != null) {
                int tex = minecraft.textureManager.load(Item.items[idx].getTexture());
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
                ShapeRenderer.instance.begin();
                ShapeRenderer.instance.vertexUV(x, y + 16, 0, 0, 1);
                ShapeRenderer.instance.vertexUV(x + 16, y + 16, 0, 1, 1);
                ShapeRenderer.instance.vertexUV(x + 16, y, 0, 1, 0);
                ShapeRenderer.instance.vertexUV(x, y, 0, 0, 0);
                ShapeRenderer.instance.end();
            }
        }
    }
}
