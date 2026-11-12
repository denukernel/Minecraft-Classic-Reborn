package net.classicremastered.minecraft.gamemode;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gui.BlockSelectScreen;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.MobSpawner;
import net.classicremastered.minecraft.player.Player;

public class CreativeGameMode extends GameMode {
    public CreativeGameMode(Minecraft minecraft) {
        super(minecraft);
        instantBreak = true;
    }

    private MobSpawner spawner;

    @Override
    public void apply(Level level) {
        super.apply(level);
        level.creativeMode = true;
        level.growTrees = false;

        // (Re)bind spawner to this level
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        player.canFly = true;
        player.isFlying = false; // start grounded; user toggles with V
        player.creativeInvulnerable = false;
    }

    @Override
    public void openInventory() {
        BlockSelectScreen blockSelectScreen = new BlockSelectScreen();
        minecraft.setCurrentScreen(blockSelectScreen);
    }

    @Override
    public boolean isSurvival() {
        return false;
    }

    // Optional: safe placement helper you referenced
    public boolean useItemOn(Player player, int x, int y, int z, int face) {
        int blockId = player.inventory.getSelected();
        if (blockId
                == net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256) {
            blockId = Block.REDSTONE_WIRE.id;
        }
        return blockId > 0
                && blockId < 256
                && Block.blocks[blockId] != null
                && tryPlaceBlock(player, x, y, z, blockId, face);
    }

    // ---------- NEW: world-gen seeding (passives only) ----------
    @Override
    public void prepareLevel(Level level) {
        spawner = new MobSpawner(level);
        minecraft.progressBar.setText("Spawning..");
        int area = level.width * level.height * level.depth / 800;

        // Initial world generation: ONLY passives (no hostiles).
        spawner.spawn(area, null, minecraft.progressBar, false);
    }

    // ---------- NEW: runtime spawning while in Creative ----------
    @Override
    public void spawnMob() {
        // handled universally in Level.tick()

    }
}
