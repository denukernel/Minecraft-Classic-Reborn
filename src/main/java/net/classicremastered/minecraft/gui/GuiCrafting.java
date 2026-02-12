package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.crafting.CraftingManager;
import net.classicremastered.minecraft.crafting.CraftingManager.ItemStack;

public final class GuiCrafting extends GuiScreen {

    private int texture;
    
    // GUI Texture Layout Constants
    private static final int GUI_W = 176;
    private static final int GUI_H = 166;
    
    // Separate crafting matrix (3x3)
    private int[] craftMatrix = new int[9];
    private int[] craftCount = new int[9];
    
    // Output result
    private int resultId = -1;
    private int resultCount = 0;

    // Carried item (on mouse cursor)
    private int carriedId = -1;
    private int carriedCnt = 0;

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
    }

    @Override
    public void onClose() {
        // Return items from crafting matrix to inventory
        Player p = minecraft.player;
        if (p != null && p.inventory != null) {
            for (int i = 0; i < 9; i++) {
                if (craftMatrix[i] > 0 && craftCount[i] > 0) {
                     // Try to add back to inventory
                     if (!p.inventory.addResource(craftMatrix[i])) {
                         // Inventory full. In a real game, drop to ground.
                     }
                }
            }
            
            // Also flush carried item
            if (carriedId > 0 && carriedCnt > 0) {
                p.inventory.addResource(carriedId); 
            }
        }
        super.onClose();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        final int x0 = (width - GUI_W) / 2;
        final int y0 = (height - GUI_H) / 2;

        // 1. Draw Background
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        drawImage(x0, y0, 0, 0, GUI_W, GUI_H);

        // 2. Draw Crafting Matrix (3x3)
        // Grid starts at (30, 17) in texture
        int gridX = x0 + 30;
        int gridY = y0 + 17;
        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int id = craftMatrix[i];
            if (id > 0) {
                drawSlotIcon(id, gridX + col * 18, gridY + row * 18);
                if (craftCount[i] > 1) {
                    drawCountString(craftCount[i], gridX + col * 18, gridY + row * 18);
                }
            }
        }

        // 3. Draw Output Slot
        // Output at (124, 35)
        int outX = x0 + 124;
        int outY = y0 + 35;
        if (resultId > 0 && resultCount > 0) {
            drawSlotIcon(resultId, outX, outY);
            if (resultCount > 1) {
                drawCountString(resultCount, outX, outY);
            }
        }

        // 4. Draw Player Inventory (Main 9-35)
        // Starts at (8, 84) - Reverted to standard 84 (Matches InventoryScreen)
        // Any value > 88 causes overlap with Hotbar (142), so 84 is the only logical choice unless texture is non-standard.
        Inventory inv = minecraft.player.inventory;
        // Main Inventory grid (3 rows of 9)
        for (int i = 0; i < 27; i++) {
            int slotIdx = 9 + i; // Offset by hotbar size
            int col = i % 9;
            int row = i / 9;
            int x = x0 + 8 + col * 18;
            int y = y0 + 84 + row * 18; // Reverted to 84
            
            int id = inv.getId(slotIdx);
            int count = inv.getCount(slotIdx);
            
            if (id > 0 && count > 0) {
                drawSlotIcon(id, x, y);
                if (count > 1) drawCountString(count, x, y);
            }
        }

        // 5. Draw Hotbar (0-8)
        // Starts at (8, 142)
        for (int i = 0; i < 9; i++) {
            int x = x0 + 8 + i * 18;
            int y = y0 + 142;
            int id = inv.getId(i);
            int count = inv.getCount(i);
             if (id > 0 && count > 0) {
                drawSlotIcon(id, x, y);
                if (count > 1) drawCountString(count, x, y);
            }
        }

        // 6. Draw Carried Item (Floating)
        if (carriedId > 0 && carriedCnt > 0) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 200f); // Render above everything
            drawSlotIcon(carriedId, mouseX - 8, mouseY - 8);
            if (carriedCnt > 1) {
                drawCountString(carriedCnt, mouseX - 8, mouseY - 8);
            }
            GL11.glPopMatrix();
        }
    }

    private void drawCountString(int count, int x, int y) {
         String s = Integer.toString(count);
         GL11.glDisable(GL11.GL_LIGHTING);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         fontRenderer.render(s, x + 19 - fontRenderer.getWidth(s), y + 6 + 3, 0xFFFFFFFF);
         GL11.glEnable(GL11.GL_LIGHTING);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    protected void onMouseClick(int mouseX, int mouseY, int button) {
        final int x0 = (width - GUI_W) / 2;
        final int y0 = (height - GUI_H) / 2;
        Inventory inv = minecraft.player.inventory;

        // Check Crafting Matrix (30, 17)
        int gridX = x0 + 30;
        int gridY = y0 + 17;
        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int sx = gridX + col * 18;
            int sy = gridY + row * 18;
            if (isOver(mouseX, mouseY, sx, sy)) {
                slotClick(craftMatrix, craftCount, i, button);
                checkRecipe();
                return;
            }
        }

        // Check Output Slot (124, 35)
        int outX = x0 + 124;
        int outY = y0 + 35;
        if (isOver(mouseX, mouseY, outX, outY)) {
            handleOutputClick();
            return;
        }

        // Check Main Inventory (8, 84)
        for (int i = 0; i < 27; i++) {
            int slotIdx = 9 + i;
            int col = i % 9;
            int row = i / 9;
            int sx = x0 + 8 + col * 18;
            int sy = y0 + 84 + row * 18; // Reverted to 84
            if (isOver(mouseX, mouseY, sx, sy)) {
                inventoryClick(inv, slotIdx, button);
                return;
            }
        }

        // Check Hotbar (8, 142)
        for (int i = 0; i < 9; i++) {
            int sx = x0 + 8 + i * 18;
            int sy = y0 + 142;
             if (isOver(mouseX, mouseY, sx, sy)) {
                inventoryClick(inv, i, button);
                return;
            }
        }
    }
    
    private boolean isOver(int mx, int my, int sx, int sy) {
        return mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18;
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
                    // Place all
                     ids[slot] = carriedId;
                     counts[slot] = carriedCnt;
                     carriedId = -1;
                     carriedCnt = 0;
                } else if (id == carriedId) {
                    // Add matches
                    if (count < 64) { // Stack limit
                        int space = 64 - count;
                        int move = Math.min(space, carriedCnt);
                        counts[slot] += move;
                        carriedCnt -= move;
                        if (carriedCnt == 0) carriedId = -1;
                    }
                } else {
                    // Swap
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
                     if (count < 64) {
                         counts[slot]++;
                         carriedCnt--;
                         if (carriedCnt == 0) carriedId = -1;
                     }
                 }
             }
        }
    }

    // Wrap inventory to match slotClick logic logic
    private void inventoryClick(Inventory inv, int slot, int button) {
        int id = inv.getId(slot);
        int count = inv.getCount(slot);
        
        if (button == 0) {
            if (carriedId == -1) {
                if (id > 0) {
                    carriedId = id;
                    carriedCnt = count;
                    inv.setIdCount(slot, -1, 0);
                }
            } else {
                if (id <= 0) {
                    inv.setIdCount(slot, carriedId, carriedCnt);
                    carriedId = -1;
                    carriedCnt = 0;
                } else if (id == carriedId) {
                    if (count < 64) {
                        int space = 64 - count;
                        int move = Math.min(space, carriedCnt);
                        inv.setIdCount(slot, id, count + move);
                        carriedCnt -= move;
                        if (carriedCnt == 0) carriedId = -1;
                    }
                } else {
                    // Swap
                    inv.setIdCount(slot, carriedId, carriedCnt);
                    carriedId = id;
                    carriedCnt = count;
                }
            }
        } else if (button == 1) {
             if (carriedId == -1) {
                 if (id > 0) {
                     int half = (count + 1) / 2;
                     carriedId = id;
                     carriedCnt = half;
                     inv.setIdCount(slot, id, count - half);
                     if (count - half == 0) inv.setIdCount(slot, -1, 0);
                 }
             } else {
                 if (id <= 0) {
                     inv.setIdCount(slot, carriedId, 1);
                     carriedCnt--;
                     if (carriedCnt == 0) carriedId = -1;
                 } else if (id == carriedId) {
                     if (count < 64) {
                         inv.setIdCount(slot, id, count + 1);
                         carriedCnt--;
                         if (carriedCnt == 0) carriedId = -1;
                     }
                 }
             }
        }
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
        if (resultId <= 0) return;
        
        // Can we pick it up?
        if (carriedId == -1) {
            carriedId = resultId;
            carriedCnt = resultCount;
            consumeGrid();
            checkRecipe();
        } else if (carriedId == resultId) {
            if (carriedCnt + resultCount <= 64) {
                carriedCnt += resultCount;
                consumeGrid();
                checkRecipe();
            }
        }
    }
    
    private void consumeGrid() {
        // Decrease items in grid
        for (int i = 0; i < 9; i++) {
            if (craftMatrix[i] > 0) {
                craftCount[i]--;
                if (craftCount[i] <= 0) {
                    craftMatrix[i] = -1;
                    craftCount[i] = 0;
                }
            }
        }
    }

    private void drawSlotIcon(int id, int x, int y) {
        if (id > 0 && id < 256 && Block.blocks[id] != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0f);
            GL11.glScalef(10.0F, 10.0F, 10.0F);
            GL11.glTranslatef(0.8F, 0.8F, 8.0F);
            GL11.glRotatef(-30.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-1.5F, 0.5F, 0.5F);
            GL11.glScalef(-1.0F, -1.0F, -1.0F);
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
