package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

import net.classicremastered.minecraft.CreativeInventoryBlocks;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

public final class BlockSelectScreen extends GuiScreen {

   private static final String[] TABS = {"All", "Build", "Nature", "Decor", "Wool", "Items", "Util", "Inv"};
   private int currentTab = 0;
   private String searchQuery = "";
   private int scrollRow = 0;

   public BlockSelectScreen() {
      this.grabsMouse = true;
   }

   private String getBlockDisplayName(Block block) {
      if (block == null) return "";
      if (block == Block.RED_WOOL) return "Red Wool";
      if (block == Block.ORANGE_WOOL) return "Orange Wool";
      if (block == Block.YELLOW_WOOL) return "Yellow Wool";
      if (block == Block.LIME_WOOL) return "Lime Wool";
      if (block == Block.GREEN_WOOL) return "Green Wool";
      if (block == Block.AQUA_GREEN_WOOL) return "Aqua Green Wool";
      if (block == Block.CYAN_WOOL) return "Cyan Wool";
      if (block == Block.BLUE_WOOL) return "Blue Wool";
      if (block == Block.PURPLE_WOOL) return "Purple Wool";
      if (block == Block.INDIGO_WOOL) return "Indigo Wool";
      if (block == Block.VIOLET_WOOL) return "Violet Wool";
      if (block == Block.MAGENTA_WOOL) return "Magenta Wool";
      if (block == Block.PINK_WOOL) return "Pink Wool";
      if (block == Block.BLACK_WOOL) return "Black Wool";
      if (block == Block.GRAY_WOOL) return "Gray Wool";
      if (block == Block.WHITE_WOOL) return "White Wool";
      
      if (block == Block.STONE) return "Stone";
      if (block == Block.GRASS) return "Grass";
      if (block == Block.DIRT) return "Dirt";
      if (block == Block.COBBLESTONE) return "Cobblestone";
      if (block == Block.WOOD) return "Planks";
      if (block == Block.SAPLING) return "Sapling";
      if (block == Block.BEDROCK) return "Bedrock";
      if (block == Block.SAND) return "Sand";
      if (block == Block.GRAVEL) return "Gravel";
      if (block == Block.GOLD_ORE) return "Gold Ore";
      if (block == Block.IRON_ORE) return "Iron Ore";
      if (block == Block.COAL_ORE) return "Coal Ore";
      if (block == Block.LOG) return "Log";
      if (block == Block.LEAVES) return "Leaves";
      if (block == Block.SPONGE) return "Sponge";
      if (block == Block.GLASS) return "Glass";
      
      if (block == Block.GOLD_BLOCK) return "Block of Gold";
      if (block == Block.IRON_BLOCK) return "Block of Iron";
      if (block == Block.DOUBLE_SLAB) return "Double Slab";
      if (block == Block.SLAB) return "Slab";
      if (block == Block.BRICK) return "Brick Block";
      if (block == Block.TNT) return "TNT";
      if (block == Block.BOOKSHELF) return "Bookshelf";
      if (block == Block.MOSSY_COBBLESTONE) return "Mossy Cobblestone";
      if (block == Block.OBSIDIAN) return "Obsidian";
      
      if (block == Block.DIAMOND_ORE_NEW) return "Diamond Ore";
      if (block == Block.DIAMOND_BLOCK_NEW) return "Block of Diamond";
      if (block == Block.BEACON) return "Beacon";
      if (block == Block.FURNACE) return "Furnace";
      if (block == Block.PUMPKIN) return "Pumpkin";
      if (block == Block.CACTUS) return "Cactus";
      if (block == Block.PISTON) return "Piston";
      if (block == Block.LUCKY_BLOCK) return "Lucky Block";
      
      if (block.name != null) return block.name;
      return "Unknown Block";
   }

   private String getItemDisplayName(Item item) {
      if (item == null) return "";
      if (item == Item.APPLE) return "Apple";
      if (item == Item.SWORD) return "Sword";
      if (item == Item.MINING_AXE) return "Axe";
      if (item == Item.SHOVEL) return "Shovel";
      if (item == Item.BEETROOT) return "Beetroot";
      if (item == Item.BOW) return "Bow";
      if (item == Item.FLINT_AND_STEEL) return "Flint and Steel";
      if (item == Item.FEATHER) return "Feather";
      if (item == Item.BUCKET_EMPTY) return "Bucket";
      if (item == Item.BUCKET_WATER) return "Water Bucket";
      if (item == Item.BUCKET_LAVA) return "Lava Bucket";
      if (item == Item.GRAVITYGUN) return "Gravity Gun";
      if (item == Item.BAG_OF_SALT) return "Bag of Salt";
      if (item == Item.HEALTH_DRINK) return "Health Drink";
      if (item == Item.DAMAGE_BOTTLE) return "Damage Bottle";
      if (item == Item.GRAPPLING_HOOK) return "Grappling Hook";
      if (item == Item.GLASS_LOOKER) return "Glass Looker";
      if (item == Item.ENDER_PEARL) return "Ender Pearl";
      
      if (item.name != null) return item.name;
      return "Unknown Item";
   }

   private String getDisplayName(int rawId) {
      if (rawId < 256) {
         return getBlockDisplayName(Block.blocks[rawId]);
      } else {
         int itemId = rawId - 256;
         if (itemId >= 0 && itemId < Item.items.length) {
            return getItemDisplayName(Item.items[itemId]);
         }
      }
      return "Unknown";
   }

   private boolean isBlockInTab(Block block, int tab) {
      if (tab == 0) return true; // All
      String name = getBlockDisplayName(block).toLowerCase();
      if (tab == 1) { // Building
         return block == Block.STONE || block == Block.COBBLESTONE || block == Block.BRICK || 
                block == Block.WOOD || block == Block.LOG || block == Block.SLAB || 
                block == Block.MOSSY_COBBLESTONE || block == Block.BOOKSHELF || 
                block == Block.OBSIDIAN || block == Block.BEDROCK || 
                block == Block.PISTON || block == Block.LUCKY_BLOCK;
      }
      if (tab == 2) { // Natural
         return block == Block.DIRT || block == Block.SAND || block == Block.GRAVEL || 
                block == Block.SPONGE || block == Block.COAL_ORE || block == Block.IRON_ORE || 
                block == Block.GOLD_ORE || block == Block.IRON_BLOCK || block == Block.GOLD_BLOCK || 
                block == Block.DIAMOND_ORE_NEW || block == Block.DIAMOND_BLOCK_NEW || 
                block == Block.BEACON;
      }
      if (tab == 3) { // Decor
         return block == Block.LEAVES || block == Block.GLASS || block == Block.SAPLING || 
                block == Block.DANDELION || block == Block.ROSE || 
                block == Block.BROWN_MUSHROOM || block == Block.RED_MUSHROOM || 
                block == Block.PUMPKIN || block == Block.CACTUS;
      }
      if (tab == 4) { // Wool
         return name.contains("wool");
      }
      if (tab == 6) { // Utility (Note: tab 5 is Items)
         return block == Block.TNT || block == Block.FURNACE || block == Block.WORKBENCH;
      }
      return false;
   }

   private java.util.List<Integer> getFilteredRawIds() {
      java.util.List<Integer> list = new java.util.ArrayList<>();
      
      // Items tab
      if (currentTab == 5) {
         for (Item item : Item.items) {
            if (item != null && item != Item.EMPTY) {
               int rawId = item.id + 256;
               String name = getItemDisplayName(item);
               if (searchQuery.isEmpty() || name.toLowerCase().contains(searchQuery.toLowerCase())) {
                  list.add(rawId);
               }
            }
         }
         return list;
      }
      
      // All tab (Blocks only)
      if (currentTab == 0) {
         for (Object obj : CreativeInventoryBlocks.allowedBlocks) {
            Block block = (Block) obj;
            String name = getBlockDisplayName(block);
            if (searchQuery.isEmpty() || name.toLowerCase().contains(searchQuery.toLowerCase())) {
               list.add(block.id);
            }
         }
         return list;
      }
      
      // Categorized block tabs
      for (Object obj : CreativeInventoryBlocks.allowedBlocks) {
         Block block = (Block) obj;
         if (isBlockInTab(block, currentTab)) {
            String name = getBlockDisplayName(block);
            if (searchQuery.isEmpty() || name.toLowerCase().contains(searchQuery.toLowerCase())) {
               list.add(block.id);
            }
         }
      }
      return list;
   }

   private int getBlockOnScreen(int mx, int my) {
      int x1 = this.width / 2 - 120;
      int y1 = this.height / 2 - 100;
      java.util.List<Integer> filtered = getFilteredRawIds();
      for (int i = 0; i < filtered.size(); ++i) {
         int col = i % 9;
         int row = i / 9;
         int renderRow = row - scrollRow;
         if (renderRow >= 0 && renderRow < 5) {
            int var4 = x1 + 12 + col * 24;
            int var5 = y1 + 25 + renderRow * 24 + 3;
            if (mx >= var4 && mx <= var4 + 24 && my >= var5 - 12 && my <= var5 + 12) {
               return i;
            }
         }
      }
      return -1;
   }

   public final void render(int mx, int my) {
      int x1 = this.width / 2 - 120;
      int x2 = this.width / 2 + 120;
      int y1 = this.height / 2 - 100;
      int y2 = this.height / 2 + 100;

      java.util.List<Integer> filtered = getFilteredRawIds();
      int totalBlocks = filtered.size();
      int totalRows = (totalBlocks + 8) / 9;
      int maxScrollRow = Math.max(0, totalRows - 5);

      // Mouse wheel scrolling
      int dw = org.lwjgl.input.Mouse.getDWheel();
      if (dw != 0) {
         if (dw > 0) {
            scrollRow--;
         } else {
            scrollRow++;
         }
         if (scrollRow < 0) scrollRow = 0;
         if (scrollRow > maxScrollRow) scrollRow = maxScrollRow;
      }

      // Draw background box
      drawFadingBox(x1, y1, x2, y2, -1878719232, -1070583712);

      // Draw tabs
      int tabWidth = 240 / TABS.length;
      for (int i = 0; i < TABS.length; ++i) {
         int tx1 = x1 + i * tabWidth;
         int tx2 = tx1 + tabWidth;
         int tabBgColor = (i == currentTab) ? 0x60FFFFFF : ((mx >= tx1 && mx < tx2 && my >= y1 + 5 && my <= y1 + 22) ? 0x30FFFFFF : 0x00FFFFFF);
         if (tabBgColor != 0) {
            drawBox(tx1 + 1, y1 + 5, tx2 - 1, y1 + 20, tabBgColor);
         }
         int textColor = (i == currentTab) ? 16777120 : 14737632;
         drawCenteredString(this.fontRenderer, TABS[i], tx1 + tabWidth / 2, y1 + 8, textColor);
      }

      // Separator
      drawBox(x1, y1 + 22, x2, y1 + 23, 0x40FFFFFF);

      int hoveredIndex = this.getBlockOnScreen(mx, my);

      TextureManager textureManager = this.minecraft.textureManager;
      ShapeRenderer shapeRenderer = ShapeRenderer.instance;
      int terrainTextureId = textureManager.load("/terrain.png");

      for (int i = 0; i < filtered.size(); ++i) {
         int col = i % 9;
         int row = i / 9;
         int renderRow = row - scrollRow;
         if (renderRow < 0 || renderRow >= 5) continue; // Out of visible range

         int rawId = filtered.get(i);
         GL11.glPushMatrix();
         int bx = x1 + 12 + col * 24;
         int by = y1 + 25 + renderRow * 24;

         if (rawId < 256) {
            Block block = Block.blocks[rawId];
            if (block != null) {
               GL11.glTranslatef((float)bx, (float)by, 0.0F);
               GL11.glScalef(10.0F, 10.0F, 10.0F);
               GL11.glTranslatef(1.0F, 0.5F, 8.0F);
               GL11.glRotatef(-30.0F, 1.0F, 0.0F, 0.0F);
               GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
               if (hoveredIndex == i) {
                  GL11.glScalef(1.6F, 1.6F, 1.6F);
               }
               GL11.glTranslatef(-1.5F, 0.5F, 0.5F);
               GL11.glScalef(-1.0F, -1.0F, -1.0F);
               GL11.glBindTexture(3553, terrainTextureId);
               shapeRenderer.begin();
               block.renderFullbright(shapeRenderer);
               shapeRenderer.end();
            }
         } else {
            int itemId = rawId - 256;
            if (itemId >= 0 && itemId < Item.items.length) {
               Item item = Item.items[itemId];
               if (item != null) {
                  GL11.glTranslatef((float)(bx + 4), (float)(by + 4), 0.0F);
                  if (hoveredIndex == i) {
                     GL11.glScalef(1.3F, 1.3F, 1.3F);
                     GL11.glTranslatef(-2.0F, -2.0F, 0.0F);
                  }
                  item.renderIcon(textureManager, shapeRenderer);
               }
            }
         }
         GL11.glPopMatrix();
      }

      // Draw scrollbar if there are more than 5 rows
      if (totalRows > 5) {
         drawBox(x1 + 231, y1 + 25, x1 + 234, y1 + 145, 0x40FFFFFF);
         int thumbHeight = 120 * 5 / totalRows;
         int thumbY = y1 + 25 + (120 - thumbHeight) * scrollRow / maxScrollRow;
         drawBox(x1 + 230, thumbY, x1 + 235, thumbY + thumbHeight, 0x80FFFFFF);
      }

      // Draw search text
      String searchDisplayText = "Search: " + searchQuery + "_";
      drawString(this.fontRenderer, searchDisplayText, x1 + 12, y2 - 18, 0xFFFFFFFF);

      // Draw tooltip
      if (hoveredIndex >= 0 && hoveredIndex < filtered.size()) {
         int hoveredRawId = filtered.get(hoveredIndex);
         String displayName = getDisplayName(hoveredRawId);
         int tooltipWidth = this.fontRenderer.getWidth(displayName);
         int tx = mx + 10;
         int ty = my - 10;
         if (tx + tooltipWidth + 6 > this.width) {
            tx = mx - tooltipWidth - 10;
         }
         drawBox(tx, ty - 2, tx + tooltipWidth + 6, ty + 10, -1878719232);
         drawString(this.fontRenderer, displayName, tx + 3, ty, 0xFFFFFF00);
      }
   }

   protected final void onMouseClick(int mx, int my, int button) {
      if (button == 0) {
         int x1 = this.width / 2 - 120;
         int y1 = this.height / 2 - 100;
         
         // Check tab click
         if (my >= y1 + 5 && my <= y1 + 22) {
            int tabWidth = 240 / TABS.length;
            for (int i = 0; i < TABS.length; ++i) {
               int tx1 = x1 + i * tabWidth;
               int tx2 = tx1 + tabWidth;
               if (mx >= tx1 && mx <= tx2) {
                  if (TABS[i].equals("Inv")) {
                     this.minecraft.setCurrentScreen(new net.classicremastered.minecraft.gui.InventoryScreen());
                  } else {
                     currentTab = i;
                     scrollRow = 0;
                  }
                  return;
               }
            }
         }
         
         // Check block click
         int clickedIndex = this.getBlockOnScreen(mx, my);
         if (clickedIndex >= 0) {
            java.util.List<Integer> filtered = getFilteredRawIds();
            if (clickedIndex < filtered.size()) {
               this.minecraft.player.inventory.replaceSlotWithId(filtered.get(clickedIndex));
               this.minecraft.setCurrentScreen((GuiScreen)null);
            }
         }
      }
   }

   protected final void onKeyPress(char var1, int var2) {
      if (var2 == Keyboard.KEY_ESCAPE) {
         this.minecraft.setCurrentScreen((GuiScreen)null);
         this.minecraft.grabMouse();
         return;
      }
      if (var2 == Keyboard.KEY_BACK) {
         if (searchQuery.length() > 0) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollRow = 0;
         }
         return;
      }
      if (var1 >= ' ' && var1 <= '~') {
         searchQuery += var1;
         scrollRow = 0;
      }
   }
}
