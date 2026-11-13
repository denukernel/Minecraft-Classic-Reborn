package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.CreativeInventoryBlocks;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.TurretVariant;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.*;

/** Classic catalog with a shared grid for drawing and input. */
public final class BlockSelectScreen extends InventoryScreen {
    private static final String[] TABS = {
        "All",
        "Building",
        "Nature",
        "Decor",
        "Equipment",
        "Items",
        "Utilities",
        "Spawn eggs",
        "Mob wands"
    };
    private static final String[] TURRET_TABS = {"Turrets", "Ammo", "Upgrades"};
    private static final int SEARCH_TAB = -1;
    private static final int[] INVENTORY_TABS = {1, 0, 2, 3, 6, 8, SEARCH_TAB, 4, 9, 5, 10, 7, 11};
    private static final int SURVIVAL_TAB = INVENTORY_TABS.length;
    private static int selectedCatalogTab = 1;
    private static int scrollRow;
    private static String searchQuery = "";
    private boolean searchFocused;
    private int mouseX, mouseY;
    private int catalogCursor = -1;
    private boolean inventoryView;
    private boolean draggingCatalog;
    private boolean leftMouseDown;
    private int draggedHotbarSlot = -1;
    private int draggedInventorySlot = -1;
    private static final Map<Block, String> BLOCK_NAMES = blockNames();

    private static Map<Block, String> blockNames() {
        Map<Block, String> names = new HashMap<>();
        for (java.lang.reflect.Field field : Block.class.getFields())
            if (field.getType() == Block.class)
                try {
                    Block block = (Block) field.get(null);
                    StringBuilder label = new StringBuilder();
                    for (String word : field.getName().replace("_NEW", "").split("_")) {
                        if (label.length() > 0) label.append(' ');
                        label.append(word.charAt(0))
                                .append(word.substring(1).toLowerCase(Locale.ROOT));
                    }
                    names.put(block, label.toString());
                } catch (ReflectiveOperationException ignored) {
                }
        names.put(Block.WOOD, "Planks");
        names.put(Block.LADDER, "Ladder");
        return names;
    }

    public BlockSelectScreen() {
        grabsMouse = true;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        ItemRegistry.bootstrap();
    }

    public static String displayName(int raw) {
        if (raw < 256)
            return raw > 0 && Block.blocks[raw] != null
                    ? BLOCK_NAMES.getOrDefault(Block.blocks[raw], Block.blocks[raw].name)
                    : "";
        return raw - 256 < Item.items.length && Item.items[raw - 256] != null
                ? Item.items[raw - 256].name
                : "";
    }

    public static List<Integer> catalog(int tab, String query) {
        ItemRegistry.bootstrap();
        List<Integer> ids = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (Object o : CreativeInventoryBlocks.allowedBlocks) {
            Block b = (Block) o;
            if (b == Block.REDSTONE_WIRE || b == Block.PISTON_HEAD) continue;
            String n = displayName(b.id).toLowerCase(Locale.ROOT);
            boolean nature =
                    b == Block.DIRT
                            || b == Block.SAND
                            || b == Block.GRAVEL
                            || b == Block.LOG
                            || b == Block.LEAVES
                            || b instanceof net.classicremastered.minecraft.level.tile.SnowBlock
                            || b == Block.SNOWY_LEAVES
                            || b == Block.SHORT_GRASS
                            || n.contains("ore")
                            || n.contains("sapling")
                            || n.contains("mushroom")
                            || n.contains("flower")
                            || b == Block.CACTUS;
            boolean decor =
                    n.contains("wool")
                            || b == Block.GLASS
                            || b == Block.BOOKSHELF
                            || b == Block.PUMPKIN;
            boolean utility =
                    b == Block.TNT
                            || b instanceof net.classicremastered.minecraft.level.tile.CircuitBlock
                            || b == Block.CHEST
                            || b == Block.JUKEBOX
                            || b == Block.WORKBENCH
                            || b
                                    instanceof
                                    net.classicremastered.minecraft.level.tile.StructureBlock
                            || b == Block.FURNACE
                            || b == Block.BEACON
                            || b == Block.LUCKY_BLOCK;
            if ((tab == 0
                            || tab == 1 && !nature && !decor && !utility
                            || tab == 2 && nature
                            || tab == 3 && decor
                            || tab == 6 && utility)
                    && n.contains(q)
                    && !ids.contains(b.id)) ids.add(b.id);
        }
        ids.removeIf(
                id ->
                        Block.blocks[id]
                                        instanceof
                                        net.classicremastered.minecraft.level.tile.StairBlock stair
                                && !stair.isInventoryVariant());
        for (Item item : Item.items)
            if (item != null
                    && item != Item.EMPTY
                    && !Item.isBlockIcon(item.id)
                    // Packed turrets require saved crate data and are obtained by packing a turret.
                    && item.id != TurretItems.PACKED) {
                int raw = item.id + 256;
                boolean turret = TurretVariant.fromItem(raw) != null;
                boolean ammo = isTurretAmmo(raw);
                boolean upgrade = TurretItems.upgradeIndex(raw) >= 0;
                boolean turretSupply =
                        item.id == TurretItems.REPAIR
                                || item.id == TurretItems.CONTROL
                                || item.id == TurretItems.CRATE
                                || item.id == TurretItems.HEAD;
                boolean egg = item instanceof ItemSpawnEgg,
                        gear =
                                Equipment.GEAR.containsKey(item.id)
                                        || item == Item.MINING_AXE
                                        || item == Item.SHOVEL
                                        || item == Item.BOW
                                        || item
                                                instanceof
                                                net.classicremastered.minecraft.level.itemstack
                                                        .SaddleItem;
                boolean utility =
                        item instanceof ToolItem && !gear
                                || item == Item.ENDER_PEARL
                                || item == Item.OAK_SIGN
                                || item.id == Item.REDSTONE_DUST_ID
                                || turretSupply;
                if ((tab == 0
                                || tab == 7 && egg
                                || tab == 4 && gear && !egg
                                || tab == 5 && !egg && !gear
                                || tab == 6 && utility
                                || tab == 8 && item instanceof MobWandItem
                                || tab == 9 && turret
                                || tab == 10 && ammo
                                || tab == 11 && (upgrade || turretSupply))
                        && item.name.toLowerCase(Locale.ROOT).contains(q)) ids.add(raw);
            }
        return ids;
    }

    private List<Integer> entries() {
        int category = INVENTORY_TABS[selectedCatalogTab];
        List<Integer> ids = catalog(category == SEARCH_TAB ? 0 : category, searchQuery);
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, (ids.size() + 8) / 9 - 5)));
        return ids;
    }

    private static boolean isTurretAmmo(int raw) {
        for (TurretVariant variant : TurretVariant.values()) {
            if (variant.acceptsAmmo(raw)) return true;
        }
        return false;
    }

    private int entryAt(int x, int y) {
        int gx = x - panelLeft() - 9, gy = y - panelTop() - 18;
        if (gx < 0 || gy < 0 || gx >= 162 || gy >= 90 || gx % 18 >= 16 || gy % 18 >= 16) return -1;
        int index = (gy / 18 + scrollRow) * 9 + gx / 18;
        return index < entries().size() ? index : -1;
    }

    @Override
    public void render(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        if (inventoryView) {
            super.render(mx, my);

            renderCatalogCursor(mx, my);
            return;
        }
        int x = panelLeft(), y = panelTop();
        List<Integer> ids = entries();
        int wheel = Mouse.isCreated() ? Mouse.getDWheel() : 0;
        if (wheel != 0) {
            scrollRow += wheel > 0 ? -1 : 1;
            ids = entries();
        }
        drawInventoryBackground();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load("/gui/creative.png"));
        drawImage(x, y, 0, 0, CREATIVE_WIDTH, CREATIVE_HEIGHT);
        drawInventoryTab(selectedCatalogTab, true);
        fontRenderer.renderNoShadow(tabLabel(selectedCatalogTab), x + 8, y + 5, 0x404040);
        drawBox(x + 80, y + 3, x + 187, y + 15, 0xFF373737);
        String query =
                searchQuery.isEmpty() && !searchFocused
                        ? "Search..."
                        : searchQuery + (searchFocused ? "_" : "");
        while (fontRenderer.getWidth(query) > 94 && query.length() > 1) query = query.substring(1);
        drawString(fontRenderer, query, x + 83, y + 5, 0xFFFFFF);
        int hover = entryAt(mx, my);
        int discardSlot = hasCarriedStack() ? catalogGridSlotAt(mx, my) : -1;
        for (int row = 0; row < 5; row++)
            for (int col = 0; col < 9; col++) {
                int index = (row + scrollRow) * 9 + col,
                        sx = x + 9 + col * 18,
                        sy = y + 18 + row * 18;
                if (row * 9 + col == discardSlot) {
                    drawBox(sx, sy, sx + 16, sy + 16, 0x80D98B8B);
                } else if (index == hover) {
                    drawBox(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                }
                if (index < ids.size()) drawIdIcon(ids.get(index), sx, sy);
            }
        if (ids.isEmpty())
            drawCenteredString(fontRenderer, "No matching items", x + 86, y + 59, 0x404040);
        int max = Math.max(0, (ids.size() + 8) / 9 - 5);
        drawSprite(
                "/gui/creative-scroll.png",
                x + 175,
                y + 18 + (max == 0 ? 0 : 75 * scrollRow / max),
                12,
                15);
        var inv = minecraft.player.inventory;
        for (int slot = 0; slot < 9; slot++) {
            int sx = x + 9 + slot * 18;
            if (slot == inv.selected) drawBox(sx, y + 112, sx + 16, y + 128, 0x60FFFFFF);
            if (inv.getId(slot) > 0) {
                drawIdIcon(inv.getId(slot), sx, y + 112);
                drawStackCount(inv.getCount(slot), sx, y + 112);
            }
        }
        if (hasCarriedStack()) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            renderCarriedStack(mx, my);
            if (discardSlot >= 0) {
                drawString(fontRenderer, "Drop to delete", mx + 10, my - 10, 0xFFFFFF);
            }
        } else if (catalogCursor > 0) {
            renderCatalogCursor(mx, my);
        } else if (hover >= 0) {
            ItemTooltip.draw(this, ids.get(hover), null, mx, my);
        } else {
            drawTabTooltip(mx, my);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button == 0) {
            draggedHotbarSlot = -1;
            draggedInventorySlot = -1;
            int tab = inventoryTabAt(mx, my);
            if (tab >= 0) {
                selectTab(tab);
                return;
            }
            if (discardAt(mx, my)) return;
        }
        if (isOutsideInventory(mx, my) && hasCarriedStack()) {
            dropCarriedStack(button);
            return;
        }
        if (inventoryView) {
            if (catalogCursor > 0) {
                if (button == 0 && !clickInventoryPage(mx, my))
                    placeCatalogItem(inventorySlotAt(mx, my));
            } else {
                int source = button == 0 && !hasCarriedStack() ? inventorySlotAt(mx, my) : -1;
                super.onMouseClick(mx, my, button);
                if (hasCarriedStack()) draggedInventorySlot = source;
            }
            return;
        }
        if (button != 0) return;
        int x = panelLeft(), y = panelTop();
        searchFocused = inside(mx, my, x + 80, y + 3, 107, 12);
        if (searchFocused) return;
        if (mx >= x + 175 && mx < x + 187 && my >= y + 18 && my < y + 108) {
            scrollRow =
                    Math.round((my - y - 18) / 89f * Math.max(0, (entries().size() + 8) / 9 - 5));
            return;
        }
        int slot = catalogHotbarSlotAt(mx, my);
        if (slot >= 0) {
            if (catalogCursor > 0) placeCatalogItem(slot);
            else if (hasCarriedStack()) placeCarriedStackInSlot(slot);
            else {
                minecraft.player.inventory.selected = slot;
                draggedHotbarSlot = slot;
            }
            return;
        }
        int index = entryAt(mx, my);
        if (index >= 0) {
            boolean shift =
                    Keyboard.isCreated()
                            && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            int raw = entries().get(index);
            if (shift) grant(raw, true);
            else pickCatalogItem(raw);
        }
    }

    @Override
    protected boolean hasCatalogCursor() {
        return catalogCursor > 0;
    }

    @Override
    protected boolean creativeInventoryLayout() {
        return true;
    }

    private void selectTab(int tab) {
        inventoryView = tab == SURVIVAL_TAB;
        searchFocused = !inventoryView && INVENTORY_TABS[tab] == SEARCH_TAB;
        if (inventoryView || selectedCatalogTab == tab) return;
        selectedCatalogTab = tab;
        scrollRow = 0;
        searchQuery = "";
    }

    private String tabLabel(int tab) {
        if (tab == SURVIVAL_TAB) return "Survival Inventory";
        int category = INVENTORY_TABS[tab];
        if (category == SEARCH_TAB) return "Search";
        return category < TABS.length ? TABS[category] : TURRET_TABS[category - TABS.length];
    }

    private void drawTabTooltip(int mx, int my) {
        int tab = inventoryTabAt(mx, my);
        if (tab >= 0) drawString(fontRenderer, tabLabel(tab), mx + 6, my - 10, 0xFFFFFF);
    }

    private int inventoryTabX(int tab) {
        int column = tab % 7;
        return panelLeft() + (column < 5 ? column * 27 : CREATIVE_WIDTH - 27 * (7 - column) + 1);
    }

    private int inventoryTabY(int tab) {
        return panelTop() + (tab < 7 ? -28 : CREATIVE_HEIGHT - 4);
    }

    private int inventoryTabAt(int mx, int my) {
        for (int tab = 0; tab <= SURVIVAL_TAB; tab++) {
            int y = inventoryTabY(tab);
            int hitY = tab < 7 ? y : y + 4;
            if (inside(mx, my, inventoryTabX(tab), hitY, 26, 28)) return tab;
        }
        return -1;
    }

    private void drawInventoryTab(int tab, boolean selected) {
        int x = inventoryTabX(tab), y = inventoryTabY(tab);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                minecraft.textureManager.load("/gui/creative-inventory-tabs.png"));
        int backgroundY = (selected ? 128 : 0) + (tab / 7) * 32;
        drawImage(x, y, (tab % 7) * 26, backgroundY, 26, 32);
        // The icon regions retain the reference's two-pixel GUI scale.
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x + 5, y + (tab < 7 ? 9 : 7), 0);
            GL11.glScalef(0.5F, 0.5F, 1);
            drawImage(0, 0, (tab % 7) * 32, 64 + (tab / 7) * 32, 32, 32);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    protected void drawInventoryBackground() {
        drawBox(0, 0, width, height, 0x60000000);
        int selected = inventoryView ? SURVIVAL_TAB : selectedCatalogTab;
        for (int tab = 0; tab <= SURVIVAL_TAB; tab++) {
            if (tab != selected) drawInventoryTab(tab, false);
        }
    }

    private void pickCatalogItem(int raw) {
        catalogCursor = raw;
        draggingCatalog = true;
    }

    private void placeCatalogItem(int slot) {
        var inventory = minecraft.player.inventory;
        if (catalogCursor <= 0 || slot < 0 || slot >= inventory.totalSize()) return;
        int count = inventory.getCount(slot);
        if (count > 0 && inventory.getId(slot) == catalogCursor && inventory.crate(slot) == null) {
            if (count >= Item.stackLimit(catalogCursor)) return;
            inventory.setIdCount(slot, catalogCursor, count + 1);
        } else {
            if (count > 0) pickUpAll(inventory, slot);
            inventory.setIdCount(slot, catalogCursor, 1);
        }
        catalogCursor = -1;
    }

    private void renderCatalogCursor(int x, int y) {
        if (catalogCursor <= 0) return;
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        drawIdIcon(catalogCursor, x - 8, y - 8);
    }

    @Override
    public void mouseEvent() {
        int x = Mouse.getEventX() * width / minecraft.width;
        int y = height - Mouse.getEventY() * height / minecraft.height - 1;
        handleMouseEvent(x, y, Mouse.getEventButton(), Mouse.getEventButtonState());
    }

    private void handleMouseEvent(int x, int y, int button, boolean pressed) {
        if (button == 0) leftMouseDown = pressed;
        if (button >= 0 && pressed) {
            onMouseClick(x, y, button);
            return;
        }
        boolean released = button == 0 && !pressed;
        if (!released && (button != -1 || !leftMouseDown)) return;
        if (!inventoryView
                && draggedHotbarSlot >= 0
                && !hasCarriedStack()
                && catalogHotbarSlotAt(x, y) != draggedHotbarSlot) {
            pickUpAll(minecraft.player.inventory, draggedHotbarSlot);
        }
        int tab = inventoryTabAt(x, y);
        if (inventoryView && hasCarriedStack() && tab >= 0 && tab != SURVIVAL_TAB) {
            selectTab(tab);
        } else if (!inventoryView
                && tab == SURVIVAL_TAB
                && (draggingCatalog || hasCarriedStack())) {
            selectTab(tab);
        }
        if (released) finishDrag(x, y);
    }

    private void finishDrag(int x, int y) {
        int hotbarSource = draggedHotbarSlot;
        int inventorySource = draggedInventorySlot;
        boolean catalogSource = draggingCatalog;
        draggedHotbarSlot = -1;
        draggedInventorySlot = -1;
        draggingCatalog = false;

        if (discardAt(x, y)) return;
        if (isOutsideInventory(x, y)
                && inventoryTabAt(x, y) < 0
                && (hotbarSource >= 0 || inventorySource >= 0)) {
            dropCarriedStack(0);
            return;
        }
        int target = inventoryView ? inventorySlotAt(x, y) : catalogHotbarSlotAt(x, y);
        if (catalogSource) {
            placeCatalogItem(target);
        } else if (target >= 0
                && hasCarriedStack()
                && (hotbarSource >= 0 || inventorySource >= 0 && target != inventorySource)) {
            placeCarriedStackInSlot(target);
        }
    }

    private boolean discardAt(int x, int y) {
        if (inventoryView && overDeleteSlot(x, y)) {
            discardCarriedStack();
            catalogCursor = -1;
            return true;
        }
        if (!inventoryView && hasCarriedStack() && catalogGridSlotAt(x, y) >= 0) {
            discardCarriedStack();
            return true;
        }
        return false;
    }

    private int catalogGridSlotAt(int x, int y) {
        int gridX = x - panelLeft() - 9, gridY = y - panelTop() - 18;
        if (!inside(gridX, gridY, 0, 0, 9 * CELL, 5 * CELL)) return -1;
        return gridY / CELL * 9 + gridX / CELL;
    }

    private int catalogHotbarSlotAt(int x, int y) {
        int startX = panelLeft() + 9;
        return inside(x, y, startX, panelTop() + 112, 9 * CELL, 16) ? (x - startX) / CELL : -1;
    }

    @Override
    protected void renderInventoryExtras(int mx, int my) {
        drawInventoryTab(SURVIVAL_TAB, true);
        if (overDeleteSlot(mx, my)) {
            int x = panelLeft() + 173, y = panelTop() + 112;
            drawBox(x, y, x + 16, y + 16, 0x30FFFFFF);
            if (!hasCarriedStack() && !hasCatalogCursor()) {
                drawString(fontRenderer, "Delete item", mx + 6, my - 10, 0xFFFFFF);
            }
        } else if (!hasCarriedStack() && !hasCatalogCursor()) {
            drawTabTooltip(mx, my);
        }
    }

    private boolean overDeleteSlot(int x, int y) {
        return inside(x, y, panelLeft() + 172, panelTop() + 111, CELL, CELL);
    }

    @Override
    public void onClose() {
        catalogCursor = -1;
        draggingCatalog = false;
        leftMouseDown = false;
        draggedHotbarSlot = -1;
        draggedInventorySlot = -1;
        super.onClose();
    }

    private void grant(int raw, boolean bulk) {
        int count = bulk ? Item.stackLimit(raw) : 1;
        while (count-- > 0 && minecraft.player.inventory.addResource(raw)) {
            // Preserve occupied slots and stop when the inventory is full.
        }
    }

    @Override
    protected void onKeyPress(char ch, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false;
                return;
            }
            minecraft.setCurrentScreen(null);
            minecraft.grabMouse();
            return;
        }
        if (inventoryView) {
            super.onKeyPress(ch, key);
            return;
        }
        if (searchFocused) {
            if (key == Keyboard.KEY_BACK && !searchQuery.isEmpty())
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            else if (ch >= 32 && ch < 127 && searchQuery.length() < 24) searchQuery += ch;
            scrollRow = 0;
            return;
        }
        if (key == minecraft.settings.inventoryKey.key) {
            minecraft.setCurrentScreen(null);
            minecraft.grabMouse();
            return;
        }
        if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9) {
            int i = entryAt(mouseX, mouseY);
            if (i >= 0) {
                grant(entries().get(i), false);
            }
        }
    }
}
