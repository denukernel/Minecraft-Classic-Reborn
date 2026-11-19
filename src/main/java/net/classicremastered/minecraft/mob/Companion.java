package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.tile.CactusBlock;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;

public interface Companion {
    int PACK_SIZE = 9;

    Mob mob();

    Inventory pack();

    void assignOwner(Player player);

    boolean canControl(Player player);

    default int maxHealth() {
        return 20;
    }

    default String activity() {
        return "";
    }

    default boolean equip(Player player, Inventory inventory, int inventorySlot) {
        if (!canControl(player) || (inventory != player.inventory && inventory != pack()) || inventorySlot < 0
                || inventorySlot >= inventory.totalSize())
            return false;
        int raw = inventory.getId(inventorySlot);
        int count = inventory.getCount(inventorySlot);
        int slot = Defender.equipmentSlot(raw);
        if (slot < 0 || count <= 0)
            return false;
        Mob mob = mob();
        int previous = mob.equipment[slot];
        int previousWear = Equipment.wear(mob, slot);
        int wear = inventory.armorWear(inventorySlot);
        if (count == 1) {
            inventory.setIdCount(inventorySlot, previous, previous > 0 ? 1 : 0);
            inventory.setArmorWear(inventorySlot, previousWear);
        } else {
            if (previous > 0 && !inventory.addResource(previous, previousWear))
                return false;
            inventory.setIdCount(inventorySlot, raw, count - 1);
        }
        Equipment.equip(mob, slot, raw, wear);
        return true;
    }

    default boolean unequip(Player player, int slot) {
        Mob mob = mob();
        if (!canControl(player) || slot < 0 || slot >= mob.equipment.length || mob.equipment[slot] <= 0
                || !player.inventory.addResource(mob.equipment[slot], Equipment.wear(mob, slot)))
            return false;
        Equipment.equip(mob, slot, 0, 0);
        return true;
    }

    static boolean canControl(Mob mob, String owner, Player player) {
        return player != null && mob.level != null && !mob.level.isNetworkMode() && player.level == mob.level
                && player.health > 0 && !player.isSpectator() && !mob.removed && mob.health > 0
                && mob.distanceToSqr(player) <= 25 && owner.equals(CrossbowTurret.playerName(player));
    }

    static boolean hire(Player player, short registryId, int price) {
        if (player == null || player.level == null || player.level.isNetworkMode()
                || player.level.creativeMode || player.isSpectator() || player.health <= 0
                || player.coins < price || player.level.blockMap == null)
            return false;
        var level = player.level;
        Mob mob = MobRegistry.create(registryId, level, player.x, player.y, player.z);
        if (!(mob instanceof Companion companion) || mob.getRegistryId() != registryId)
            return false;
        int baseX = (int) Math.floor(player.x), baseY = (int) Math.floor(player.bb.y0);
        int baseZ = (int) Math.floor(player.z);
        for (int radius = 2; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
                        continue;
                    for (int dy = 1; dy >= -2; dy--) {
                        int x = baseX + dx, y = baseY + dy, z = baseZ + dz;
                        if (!level.isInBounds(x, y + 2, z) || !level.isSolidTile(x, y - 1, z))
                            continue;
                        mob.setPos(x + .5f, y + mob.bbHeight / 2f, z + .5f);
                        if (!level.getCubes(mob.bb).isEmpty() || CactusBlock.nearSpawn(level, mob.bb)
                                || level.containsAnyLiquid(mob.bb) || !level.isFree(mob.bb))
                            continue;
                        companion.assignOwner(player);
                        level.addEntity(mob);
                        player.coins -= price;
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
