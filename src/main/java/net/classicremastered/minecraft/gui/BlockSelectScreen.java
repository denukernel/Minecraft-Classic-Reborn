package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.CreativeInventoryBlocks;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.render.ShapeRenderer;

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
    private static int currentTab, scrollRow;
    private static String searchQuery = "";
    private boolean searchFocused;
    private int mouseX, mouseY;
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
        return names;
    }

    private int left() {
        return (width - 195) / 2;
    }

    private int top() {
        return (height - 180) / 2 + 28;
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
        for (Item item : Item.items)
            if (item != null && item != Item.EMPTY && item.id != Item.LEVER_ICON_ID) {
                boolean egg = item instanceof ItemSpawnEgg,
                        gear =
                                Equipment.GEAR.containsKey(item.id)
                                        || item == Item.MINING_AXE
                                        || item == Item.SHOVEL
                                        || item == Item.BOW;
                boolean utility =
                        item instanceof ToolItem && !gear
                                || item == Item.ENDER_PEARL
                                || item.id == Item.REDSTONE_DUST_ID;
                if ((tab == 0
                                || tab == 7 && egg
                                || tab == 4 && gear && !egg
                                || tab == 5 && !egg && !gear
                                || tab == 6 && utility
                                || tab == 8 && item instanceof MobWandItem)
                        && item.name.toLowerCase(Locale.ROOT).contains(q)) ids.add(item.id + 256);
            }
        return ids;
    }

    private List<Integer> entries() {
        List<Integer> ids = catalog(currentTab, searchQuery);
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, (ids.size() + 8) / 9 - 5)));
        return ids;
    }

    private int entryAt(int x, int y) {
        int gx = x - left() - 9, gy = y - top() - 18;
        if (gx < 0 || gy < 0 || gx >= 162 || gy >= 90 || gx % 18 >= 16 || gy % 18 >= 16) return -1;
        int index = (gy / 18 + scrollRow) * 9 + gx / 18;
        return index < entries().size() ? index : -1;
    }

    private void sprite(String path, int x, int y, int w, int h) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load(path));
        ShapeRenderer shape = ShapeRenderer.instance;
        shape.begin();
        shape.vertexUV(x, y + h, 0, 0, 1);
        shape.vertexUV(x + w, y + h, 0, 1, 1);
        shape.vertexUV(x + w, y, 0, 1, 0);
        shape.vertexUV(x, y, 0, 0, 0);
        shape.end();
    }

    @Override
    public void render(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        int x = left(), y = top();
        List<Integer> ids = entries();
        int wheel = Mouse.isCreated() ? Mouse.getDWheel() : 0;
        if (wheel != 0) {
            scrollRow += wheel > 0 ? -1 : 1;
            ids = entries();
        }
        drawBox(0, 0, width, height, 0x60000000);
        int[] icons = {
            Block.STONE.id,
            Block.WOOD.id,
            Block.SAPLING.id,
            Block.GLASS.id,
            Item.BOW.id + 256,
            Item.APPLE.id + 256,
            Block.TNT.id,
            357,
            336
        };
        for (int t = 0; t < TABS.length; t++) {
            int tx = x + 2 + t * 21;
            sprite(
                    t == currentTab ? "/gui/creative-tab-selected.png" : "/gui/creative-tab.png",
                    tx,
                    y - 28,
                    23,
                    29);
            drawIdIcon(icons[t], tx + 3, y - 22);
        }
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load("/gui/creative.png"));
        drawImage(x, y, 0, 0, 195, 136);
        drawString(fontRenderer, TABS[currentTab], x + 8, y + 5, 0x404040);
        drawBox(x + 80, y + 3, x + 187, y + 15, 0xFF373737);
        String query =
                searchQuery.isEmpty() && !searchFocused
                        ? "Search..."
                        : searchQuery + (searchFocused ? "_" : "");
        while (fontRenderer.getWidth(query) > 94 && query.length() > 1) query = query.substring(1);
        drawString(fontRenderer, query, x + 83, y + 5, 0xFFFFFF);
        int hover = entryAt(mx, my);
        for (int row = 0; row < 5; row++)
            for (int col = 0; col < 9; col++) {
                int index = (row + scrollRow) * 9 + col,
                        sx = x + 9 + col * 18,
                        sy = y + 18 + row * 18;
                if (index == hover) drawBox(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                if (index < ids.size()) drawIdIcon(ids.get(index), sx, sy);
            }
        if (ids.isEmpty())
            drawCenteredString(fontRenderer, "No matching items", x + 86, y + 59, 0x404040);
        int max = Math.max(0, (ids.size() + 8) / 9 - 5);
        sprite(
                "/gui/creative-scroll.png",
                x + 175,
                y + 18 + (max == 0 ? 0 : 75 * scrollRow / max),
                12,
                15);
        var inv = minecraft.player.inventory;
        for (int slot = 0; slot < 9; slot++) {
            int sx = x + 9 + slot * 18;
            if (slot == inv.selected) drawBox(sx, y + 112, sx + 16, y + 128, 0x60FFFFFF);
            if (inv.getId(slot) > 0) drawIdIcon(inv.getId(slot), sx, y + 112);
        }
        drawBox(x, y + 139, x + 62, y + 154, 0xFF555555);
        drawString(fontRenderer, "Inventory", x + 5, y + 143, 0xFFFFFF);
        drawString(fontRenderer, "Click / 1-9 to give", x + 70, y + 143, 0xFFFFFF);
        String tooltip = null, detail = "";
        if (hover >= 0) {
            int raw = ids.get(hover);
            tooltip = displayName(raw);
            if (raw >= 256 && Item.items[raw - 256] instanceof MobWandItem wand)
                detail = wand.instructions();
            else detail = Equipment.description(raw);
        } else if (my >= y - 28 && my < y && mx >= x + 2 && mx < x + 191)
            tooltip = TABS[(mx - x - 2) / 21];
        if (tooltip != null) {
            int tw = Math.max(fontRenderer.getWidth(tooltip), fontRenderer.getWidth(detail));
            int tx = Math.max(3, Math.min(width - tw - 6, mx + 9)),
                    ty = Math.min(height - 28, my + 12);
            drawBox(tx - 3, ty - 3, tx + tw + 4, ty + (detail.isEmpty() ? 11 : 23), 0xF0100010);
            drawString(fontRenderer, tooltip, tx, ty, 0xFFFFFF);
            if (!detail.isEmpty()) drawString(fontRenderer, detail, tx, ty + 12, 0xAAAAAA);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button != 0) return;
        int x = left(), y = top();
        searchFocused = mx >= x + 80 && mx < x + 187 && my >= y + 3 && my < y + 15;
        if (searchFocused) return;
        if (my >= y - 28 && my < y && mx >= x + 2 && mx < x + 191) {
            currentTab = (mx - x - 2) / 21;
            scrollRow = 0;
            return;
        }
        if (mx >= x + 175 && mx < x + 187 && my >= y + 18 && my < y + 108) {
            scrollRow =
                    Math.round((my - y - 18) / 89f * Math.max(0, (entries().size() + 8) / 9 - 5));
            return;
        }
        if (my >= y + 112 && my < y + 128 && mx >= x + 9 && mx < x + 171) {
            minecraft.player.inventory.selected = (mx - x - 9) / 18;
            return;
        }
        if (my >= y + 139 && my < y + 154 && mx >= x && mx < x + 62) {
            minecraft.setCurrentScreen(new InventoryScreen());
            return;
        }
        int index = entryAt(mx, my);
        if (index >= 0) minecraft.player.inventory.replaceSlotWithId(entries().get(index));
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
                int prev = minecraft.player.inventory.selected;
                minecraft.player.inventory.selected = key - Keyboard.KEY_1;
                minecraft.player.inventory.replaceSlotWithId(entries().get(i));
                minecraft.player.inventory.selected = prev;
            }
        }
    }
}
