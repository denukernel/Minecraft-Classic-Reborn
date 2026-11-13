package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.nbt.*;
import java.util.List;

/** Serializes player inventory and stats independently of the world format. */
final class PlayerNbtCodec {
    private PlayerNbtCodec() {}

    static CompoundTag save(Level level) {
        CompoundTag tag = new CompoundTag("Player");
        tag.putString("id", "Player");
        Entity entity = level.player;
        if (entity == null
                && level.minecraft != null
                && level.minecraft.player != null
                && level.minecraft.player.level == level) {
            entity = level.minecraft.player;
        }
        if (!(entity instanceof Player player)) {
            return tag;
        }

        ListTag pos = new ListTag("Pos", (byte) 5);
        pos.add(new FloatTag(null, player.x));
        pos.add(new FloatTag(null, player.y));
        pos.add(new FloatTag(null, player.z));
        tag.put(pos);
        ListTag rotation = new ListTag("Rotation", (byte) 5);
        rotation.add(new FloatTag(null, player.yRot));
        rotation.add(new FloatTag(null, player.xRot));
        tag.put(rotation);
        tag.putFloat("FeetY", player.bb.y0);
        tag.putBoolean("Sneaking", player.sneaking);
        RidingLinks.savePassenger(
                level.blockMap == null ? List.of() : level.blockMap.all, player, tag);
        if (player.health > 0 && level.blockMap != null) {
            ListTag arrows = new ListTag("StuckArrows", (byte) 4);
            for (Entity candidate : level.blockMap.all) {
                if (candidate instanceof Arrow arrow && arrow.isStuckTo(player)) {
                    arrows.add(new LongTag(null, arrow.getAttachmentSeed()));
                }
            }
            if (arrows.size() > 0) tag.put(arrows);
        }

        net.classicremastered.minecraft.level.itemstack.Equipment.save(player, tag);
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
            int wear = inventory.armorWear(slot);
            if (wear > 0) stack.putInt("ArmorWear", wear);
            var crate = inventory.crate(slot);
            if (crate != null) stack.put(crate.save());
            stacks.add(stack);
        }
        tag.put(stacks);
        return tag;
    }

    static void load(Level level, CompoundTag tag) {
        ListTag pos = tag.getList("Pos");
        if (pos.size() < 3) {
            return;
        }
        ListTag rotation = tag.getList("Rotation");
        float yaw = rotation.size() >= 2 ? ((FloatTag) rotation.get(0)).value : 0;
        float pitch = rotation.size() >= 2 ? ((FloatTag) rotation.get(1)).value : 0;
        float x = ((FloatTag) pos.get(0)).value;
        float y = ((FloatTag) pos.get(1)).value;
        float z = ((FloatTag) pos.get(2)).value;
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || (x == 0 && y == 0 && z == 0 && !tag.hasKey("Health") && !tag.hasKey("FeetY"))) {
            return;
        }

        Player player = new Player(null);
        player.level = level;
        float feetY = tag.hasKey("FeetY") ? tag.getFloat("FeetY") : Float.NaN;
        if (Float.isFinite(feetY)) {
            player.restoreSavedPosition(x, feetY, z, yaw, pitch, tag.getBoolean("Sneaking"));
        } else {
            restoreLegacyPosition(player, x, y, z, yaw, pitch);
        }
        loadStats(tag, player);
        level.player = player;
        level.addEntity(player);
        if (player.health > 0) {
            for (Tag entry : tag.getList("StuckArrows").list) {
                if (entry instanceof LongTag seed) Arrow.restoreStuckArrow(player, seed.value);
            }
        }
    }

    static void restoreLegacyPosition(
            Player player, float x, float eyeY, float z, float yaw, float pitch) {
        float feetY = eyeY - player.heightOffset;
        player.restoreSavedPosition(x, feetY, z, yaw, pitch, false);
        // Old saves omitted crouch height and step smoothing. Recover a floor
        // just above the inferred feet if the restored box starts inside it.
        float floorY = feetY;
        float maxLegacyStepOffset = .701f;
        for (Object collision : player.level.getCubes(player.bb)) {
            AABB box = (AABB) collision;
            if (box.y0 <= feetY
                    && box.y1 <= feetY + maxLegacyStepOffset
                    && box.intersects(player.bb)) {
                floorY = Math.max(floorY, box.y1);
            }
        }
        if (floorY > feetY) {
            player.restoreSavedPosition(x, floorY, z, yaw, pitch, false);
        }
    }

    private static void loadStats(CompoundTag tag, Player player) {
        net.classicremastered.minecraft.level.itemstack.Equipment.load(player, tag);
        // Older saves have only position and rotation. Keep their player defaults.
        if (tag.hasKey("Health")) player.lastHealth = player.health = tag.getInt("Health");
        if (tag.hasKey("Coins")) player.coins = tag.getInt("Coins");
        player.score = tag.getInt("Score");
        player.packedTurretHealth = tag.getInt("PackedTurretHealth");
        player.packedTurretAmmo = tag.getInt("PackedTurretAmmo");
        player.packedTurretEnabled = tag.getBoolean("PackedTurretEnabled");
        if (tag.hasKey("Inventory")) {
            var inventory = new Inventory();
            ListTag stacks = tag.getList("Inventory");
            int size =
                    Math.max(
                            Inventory.HOTBAR_SIZE,
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
                int slot = stack.getInt("Slot");
                inventory.setLoadedStack(slot, stack.getInt("Id"), stack.getInt("Count"));
                inventory.setArmorWear(slot, stack.getInt("ArmorWear"));
                var crate =
                        net.classicremastered.minecraft.level.itemstack.PackedTurret.load(stack);
                if (crate != null
                        && stack.getInt("Id")
                                == net.classicremastered.minecraft.level.itemstack.TurretItems
                                                .PACKED
                                        + 256) inventory.setCrate(stack.getInt("Slot"), crate);
            }
            if (tag.hasKey("SelectedSlot")) {
                inventory.selected =
                        Math.max(
                                0, Math.min(Inventory.HOTBAR_SIZE - 1, tag.getInt("SelectedSlot")));
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
