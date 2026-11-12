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
        net.classicremastered.minecraft.level.itemstack.Equipment.save(player,tag);
        tag.putInt("Health", player.health);
        tag.putInt("Coins", player.coins);
        tag.putInt("Score", player.score);
        if (player.packedTurretHealth > 0) {
            tag.putInt("PackedTurretHealth", player.packedTurretHealth);
            tag.putInt("PackedTurretAmmo", player.packedTurretAmmo);
            tag.putBoolean("PackedTurretEnabled", player.packedTurretEnabled);
        }
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
            var crate = inventory.crate(slot);
            if (crate != null) stack.put(crate.save());
            stacks.add(stack);
        }
        tag.put(stacks);
    }

    static void load(CompoundTag tag, Entity entity) {
        if (!(entity instanceof Player player))
            return;
        net.classicremastered.minecraft.level.itemstack.Equipment.load(player, tag);
        // Older saves have only position and rotation. Keep their player defaults.
        if (tag.hasKey("Health"))
            player.health = tag.getInt("Health");
        if (tag.hasKey("Coins")) player.coins = tag.getInt("Coins");
        player.score = tag.getInt("Score");
        player.packedTurretHealth = tag.getInt("PackedTurretHealth");
        player.packedTurretAmmo = tag.getInt("PackedTurretAmmo");
        player.packedTurretEnabled = tag.getBoolean("PackedTurretEnabled");
        if (tag.hasKey("Inventory")) {
            var inventory = new Inventory();
            ListTag stacks = tag.getList("Inventory");
            int size = Math.max(Inventory.HOTBAR_SIZE,
                    tag.hasKey("InventorySize")
                            ? tag.getInt("InventorySize")
                            : inventory.totalSize());
            for (int i = 0; i < stacks.size(); i++) {
                int slot = ((CompoundTag) stacks.get(i)).getInt("Slot");
                if (slot < 0 || slot == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid saved inventory slot: " + slot);
                }
                size = Math.max(size, slot + 1);
            }
            inventory.resizeGrid(size - Inventory.HOTBAR_SIZE);
            for (int i = 0; i < stacks.size(); i++) {
                CompoundTag stack = (CompoundTag) stacks.get(i);
                inventory.setLoadedStack(
                        stack.getInt("Slot"), stack.getInt("Id"), stack.getInt("Count"));
                var crate =
                        net.classicremastered.minecraft.level.itemstack.PackedTurret.load(stack);
                if (crate != null
                        && stack.getInt("Id")
                                == net.classicremastered.minecraft.level.itemstack.TurretItems
                                                .PACKED
                                        + 256) inventory.setCrate(stack.getInt("Slot"), crate);
            }
            if (tag.hasKey("SelectedSlot")) {
                inventory.selected = Math.max(0,
                        Math.min(Inventory.HOTBAR_SIZE - 1, tag.getInt("SelectedSlot")));
            }
            Inventory.splitOversized(
                    inventory.totalSize(),
                    inventory::getId,
                    inventory::getCount,
                    inventory::setLoadedStack);
            player.inventory = inventory;
            net.classicremastered.minecraft.level.itemstack.TurretItems.migrateLegacyCrate(player);
        }
    }
}
