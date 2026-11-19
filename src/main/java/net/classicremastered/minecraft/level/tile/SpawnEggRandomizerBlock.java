package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.gui.SpawnEggRandomizerScreen;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.SpawnEggLoadout;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;

public final class SpawnEggRandomizerBlock extends Block {
    public SpawnEggRandomizerBlock(int id) {
        super(id, 56, "Spawn Egg Randomizer");
        setData(Tile$SoundType.stone, 1f, 1f, 3.5f);
    }

    @Override
    protected int getTextureId(int side) {
        return switch (side) {
            case 0 -> 1;
            case 1 -> 57;
            default -> textureId;
        };
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        if (!canUse(level, x, y, z, player))
            return false;
        player.minecraft.setCurrentScreen(new SpawnEggRandomizerScreen(level, x, y, z));
        return true;
    }

    public static boolean canUse(Level level, int x, int y, int z, Player player) {
        return level != null && player != null && player.level == level && player.minecraft != null
                && player.minecraft.level == level && !player.minecraft.isOnline() && !level.isNetworkMode()
                && !player.isSpectator() && !player.removed && player.health > 0
                && level.getTile(x, y, z) == Block.SPAWN_EGG_RANDOMIZER.id;
    }

    public static boolean spawn(Level level, int x, int y, int z, Player player, SpawnEggLoadout loadout) {
        if (!canUse(level, x, y, z, player) || level.blockMap == null)
            return false;
        Inventory inventory = player.inventory;
        int egg = loadout.species().eggId();
        int eggSlot = level.creativeMode ? -1 : findEgg(inventory, egg);
        if (!level.creativeMode && eggSlot < 0)
            return false;

        float feet = y + 1f;
        Mob mob = loadout.createMob(level, x + .5f, feet, z + .5f);
        if (mob == null)
            return false;
        mob.setPos(x + .5f, feet + mob.bbHeight * .5f, z + .5f);
        if (mob.bb.y1 >= level.depth || !level.getCubes(mob.bb).isEmpty()
                || !level.findEntities(mob, mob.bb).isEmpty() || level.containsAnyLiquid(mob.bb))
            return false;

        if (mob instanceof Defender defender)
            defender.owner = CrossbowTurret.playerName(player);
        level.addEntity(mob);
        if (eggSlot >= 0) {
            int remaining = inventory.getCount(eggSlot) - 1;
            inventory.setIdCount(eggSlot, remaining > 0 ? egg : -1, remaining);
        }
        return true;
    }

    public static int findEgg(Inventory inventory, int egg) {
        for (int slot = 0; slot < inventory.totalSize(); slot++) {
            if (inventory.getId(slot) == egg && inventory.getCount(slot) > 0)
                return slot;
        }
        return -1;
    }
}
