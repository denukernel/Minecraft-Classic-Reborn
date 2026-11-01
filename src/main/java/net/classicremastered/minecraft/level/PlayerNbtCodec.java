package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.*;

/** Serializes player inventory and stats independently of the world format. */
final class PlayerNbtCodec {
    private PlayerNbtCodec() {}

    static void save(CompoundTag tag, Entity entity) {
        if (!(entity instanceof Player player)) return;
        tag.putInt("Health", player.health);
        tag.putInt("Coins", player.coins);
        var inventory = player.inventory;
        tag.putInt("InventorySize", inventory.totalSize());
        tag.putInt("SelectedSlot", inventory.selected);
        ListTag stacks = new ListTag("Inventory", (byte) 10);
        for (int slot = 0; slot < inventory.totalSize(); slot++) {
            if (inventory.getId(slot) <= 0 || inventory.getCount(slot) <= 0) continue;
            CompoundTag stack = new CompoundTag(null);
            stack.putInt("Slot", slot);
            stack.putInt("Id", inventory.getId(slot));
            stack.putInt("Count", inventory.getCount(slot));
            stacks.add(stack);
        }
        tag.put(stacks);
    }

    static void load(CompoundTag tag, Entity entity) {
        if (!(entity instanceof Player player)) return;
        // Older saves have only position and rotation. Keep their player defaults.
        if (tag.hasKey("Health")) player.health = tag.getInt("Health");
        if (tag.hasKey("Coins")) player.coins = tag.getInt("Coins");
        if (tag.hasKey("Inventory")) {
            var inventory = new Inventory();
            if (tag.hasKey("InventorySize")) {
                inventory.resizeGrid(Math.max(0, tag.getInt("InventorySize") - Inventory.HOTBAR_SIZE));
            }
            ListTag stacks = tag.getList("Inventory");
            for (int i = 0; i < stacks.size(); i++) {
                CompoundTag stack = (CompoundTag) stacks.get(i);
                inventory.setIdCount(stack.getInt("Slot"), stack.getInt("Id"), stack.getInt("Count"));
            }
            if (tag.hasKey("SelectedSlot")) {
                inventory.selected = Math.max(0, Math.min(Inventory.HOTBAR_SIZE - 1, tag.getInt("SelectedSlot")));
            }
            player.inventory = inventory;
        }
    }
}
