package net.classicremastered.minecraft.chat.commands;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ToolItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public class GiveCommand implements Command {
    public String getName() { return "give"; }
    public String getUsage() { return "/give <id|name> [count]  |  /give help [blocks|items|all] [page]"; }

    private static final int PAGE_SIZE = 20;
    private static final int ITEM_BASE_ID = 256; 
    private static final int EMPTY_GIVE_ID = ITEM_BASE_ID;

    private static boolean isDisallowedBlock(int id) {
        if (id <= 0 || id >= Block.blocks.length) return false;
        Block b = Block.blocks[id];
        return b == Block.WATER || b == Block.LAVA || b == Block.FIRE;
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (args.length == 0) {
            mc.hud.addChat("&eUsage: " + getUsage());
            return;
        }

        // ---------- HELP ----------
        if ("help".equalsIgnoreCase(args[0])) {
            String what = (args.length >= 2) ? args[1].toLowerCase() : "all";
            int page = 1;
            if (args.length >= 3) {
                try { page = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
            }
            switch (what) {
                case "blocks": listBlocks(mc, page); return;
                case "items": listItems(mc, page); return;
                default: listAll(mc, page); return;
            }
        }

        // ---------- GIVE ----------
        int id = -1;
        int requested = (args.length > 1) ? parseCount(args[1]) : 64;

        // Try parse as number first
        try { id = Integer.parseInt(args[0]); } 
        catch (NumberFormatException nfe) {
            // Not a number → resolve by name
            id = resolveNameToId(args[0]);
            if (id == -1) {
                mc.hud.addChat("&cUnknown block/item: " + args[0]);
                return;
            }
        }

        int slot = sender.inventory.selected;

        // BLOCK
        if (id >= 0 && id < Block.blocks.length && Block.blocks[id] != null) {
            if (isDisallowedBlock(id)) {
                mc.hud.addChat("&cYou cannot give that block directly (use buckets/tools).");
                return;
            }
            int count = Math.min(requested, 99);
            sender.inventory.slots[slot] = id;
            sender.inventory.count[slot] = count;
            mc.hud.addChat("&aGave " + count + "x " + safeBlockName(id) + " &7(slot " + (slot+1) + ")");
            return;
        }

        // Prevent EMPTY (256)
        if (id == EMPTY_GIVE_ID) {
            mc.hud.addChat("&cInvalid id: 256");
            return;
        }

        // ITEM
        if (id >= ITEM_BASE_ID) {
            int itemId = id - ITEM_BASE_ID;
            if (itemId >= 0 && itemId < Item.items.length) {
                Item it = Item.items[itemId];
                if (it == null || it == Item.EMPTY || itemId == 0) {
                    mc.hud.addChat("&cCannot give EMPTY.");
                    return;
                }
                int count = (it instanceof ToolItem) ? 1 : Math.min(requested, 64);
                sender.inventory.slots[slot] = id;
                sender.inventory.count[slot] = count;
                mc.hud.addChat("&aGave " + count + "x " + safeItemName(itemId) + " &8[id " + id + "]");
                return;
            }
        }

        mc.hud.addChat("&cInvalid id: " + id);
    }

    // ---------- Helpers ----------

    private int parseCount(String s) {
        try { return Math.max(1, Integer.parseInt(s)); }
        catch (NumberFormatException e) { return 64; }
    }

    /** Resolve a string name to block/item id */
    private int resolveNameToId(String name) {
        String key = name.toLowerCase().replace(' ', '_');

        // Blocks
        for (int i = 0; i < Block.blocks.length; i++) {
            if (Block.blocks[i] != null) {
                String n = Block.blocks[i].name;
                if (n != null && n.toLowerCase().replace(' ', '_').equals(key)) {
                    return i;
                }
            }
        }
        // Items
        for (int i = 0; i < Item.items.length; i++) {
            Item it = Item.items[i];
            if (it != null && it != Item.EMPTY && i != 0) {
                String n = it.name;
                if (n != null && n.toLowerCase().replace(' ', '_').equals(key)) {
                    return ITEM_BASE_ID + i;
                }
            }
        }
        return -1;
    }

    private void listBlocks(Minecraft mc, int page) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Block.blocks.length; i++) {
            if (Block.blocks[i] != null && !isDisallowedBlock(i)) {
                lines.add("&7" + i + ": &f" + safeBlockName(i));
            }
        }
        pagedDump(mc, "&eBlocks", lines, page);
    }

    private void listItems(Minecraft mc, int page) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Item.items.length; i++) {
            Item it = Item.items[i];
            int giveId = ITEM_BASE_ID + i;
            if (giveId == EMPTY_GIVE_ID || it == null || it == Item.EMPTY || i == 0) continue;
            lines.add("&7" + giveId + ": &f" + safeItemName(i));
        }
        pagedDump(mc, "&eItems", lines, page);
    }

    private void listAll(Minecraft mc, int page) {
        List<String> lines = new ArrayList<>();
        lines.add("&6=== Blocks ===");
        for (int i = 0; i < Block.blocks.length; i++) {
            if (Block.blocks[i] != null && !isDisallowedBlock(i)) {
                lines.add("&7" + i + ": &f" + safeBlockName(i));
            }
        }
        lines.add("&6=== Items (use id ≥256) ===");
        for (int i = 0; i < Item.items.length; i++) {
            Item it = Item.items[i];
            int giveId = ITEM_BASE_ID + i;
            if (giveId == EMPTY_GIVE_ID || it == null || it == Item.EMPTY || i == 0) continue;
            lines.add("&7" + giveId + ": &f" + safeItemName(i));
        }
        pagedDump(mc, "&eAll Givable", lines, page);
    }

    private void pagedDump(Minecraft mc, String title, List<String> lines, int page) {
        int total = lines.size();
        int pages = Math.max(1, (int)Math.ceil(total / (double)PAGE_SIZE));
        int p = Math.min(Math.max(1, page), pages);
        int start = (p - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);

        mc.hud.addChat(title + " &7(page " + p + "/" + pages + ")");
        if (total == 0) {
            mc.hud.addChat("&8(none)");
            return;
        }
        for (int i = start; i < end; i++) mc.hud.addChat(lines.get(i));
    }

    private String safeBlockName(int id) {
        String n = Block.blocks[id].name;
        return (n != null && !n.isEmpty()) ? n : ("Block#" + id);
    }

    private String safeItemName(int itemId) {
        Item it = Item.items[itemId];
        String n = (it != null) ? it.name : null;
        return (n != null && !n.isEmpty()) ? n : ("Item#" + itemId);
    }
}
