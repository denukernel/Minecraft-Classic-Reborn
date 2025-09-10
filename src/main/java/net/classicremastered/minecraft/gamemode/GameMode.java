package net.classicremastered.minecraft.gamemode;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.Tile$SoundType;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

public class GameMode {

    protected Level level;
    public Minecraft minecraft;
    public boolean instantBreak;
    public Player player;

    public GameMode(Minecraft minecraft) {
        this.minecraft = minecraft;
        instantBreak = false;
    }

    public void apply(Level level) {
        this.level = level;
        level.creativeMode = false;
        level.growTrees = true;

        Player.creativeInvulnerable = false;
    }

    public void openInventory() {
        if (minecraft != null) {
            minecraft.setCurrentScreen(new net.classicremastered.minecraft.gui.InventoryScreen());
        }
    }

    public void hitBlock(int x, int y, int z) { this.breakBlock(x, y, z); }
    public void hitBlock(int x, int y, int z, int side) {}
    public void resetHits() {}
    public void applyCracks(float time) {}
    public float getReachDistance() { return 5.0F; }

    /** Allow placement by default; subclasses refine. */
    public boolean canPlace(int block) { return true; }

    public boolean useItem(Player player, int type) { return false; }

    public void preparePlayer(Player player) {}
    public void spawnMob() {}
    public void prepareLevel(Level level) {}
    public boolean isSurvival() { return true; }

    public void apply(Player player) {
        // Survival-like defaults
        player.canFly   = false;
        player.isFlying = false;
    }

    // ---------- Placement safety + utility ----------

    protected boolean canPlaceWithoutColliding(Player player, int x, int y, int z, int blockId) {
        if (blockId <= 0) return false;
        if (this.level == null) this.level = minecraft.level;
        if (this.level == null) return false;
        if (!this.level.isInBounds(x, y, z)) return false;

        // Collision check with player AABB
        final float eps = 0.001f;
        AABB blockBox = new AABB(
            x + eps,     y + eps,     z + eps,
            x + 1 - eps, y + 1 - eps, z + 1 - eps
        );

        return player == null || player.bb == null || !player.bb.intersects(blockBox);
    }


    public boolean tryPlaceBlock(Player player, int x, int y, int z, int blockId) {
        if (blockId <= 0) return false;
        if (this.level == null) this.level = minecraft.level;
        if (this.level == null) return false;

        if (!canPlaceWithoutColliding(player, x, y, z, blockId)) return false;

        int current = level.getTile(x, y, z);
        if (current != 0) {
            Block cur = Block.blocks[current];
            if (cur != null && cur.isSolid()) {
                // If it's solid, don't replace — instead, place *adjacent* on the clicked face.
                // (handled earlier by InputManager with face offset)
            }
        }

        boolean success;
        if (level instanceof LevelInfiniteTerrain) {
            success = ((LevelInfiniteTerrain) level).setTile(x, y, z, blockId);
        } else {
            success = level.setTile(x, y, z, blockId);
        }
        if (!success) return false;

        // rest of your sound/particle/packet logic unchanged…
        Block placed = Block.blocks[blockId];
        if (placed != null) {
            if (minecraft.isOnline() && minecraft.networkManager != null) {
                // correct packet: face = -1
                minecraft.networkManager.sendBlockChange(x, y, z, -1, blockId);
            }
            if (placed.stepsound != Tile$SoundType.none) {
                float vol   = (placed.stepsound.getVolume() + 1.0F) / 2.0F;
                float pitch = placed.stepsound.getPitch() * 0.8F;
                if (player != null) {
                    player.playPositionalSound(placed.stepsound.pool, vol, pitch);
                } else {
                    level.playSound(placed.stepsound.pool, (float)x, (float)y, (float)z, vol, pitch);
                }
            }
        }
        return true;
    }



    // Infinite-safe block breaking
    public void breakBlock(int x, int y, int z) {
        if (this.level == null) this.level = minecraft.level;
        if (this.level == null) return;

        int id = level.getTile(x, y, z);
        Block block = id > 0 ? Block.blocks[id] : null;

        boolean success;
        if (level instanceof LevelInfiniteTerrain) {
            success = ((LevelInfiniteTerrain)level).setTile(x, y, z, 0);
        } else {
            success = level.setTile(x, y, z, 0);
        }

        if (block != null && success) {
            if (minecraft.isOnline()) {
                int held = minecraft.player.inventory.getSelected();
                int heldBlockId = (held >= 0 && held < 256) ? held : 0;
                minecraft.networkManager.sendBlockChange(x, y, z, 0, heldBlockId);
            }
            if (block.stepsound != Tile$SoundType.none) {
                float vol   = (block.stepsound.getVolume() + 1.0F) / 2.0F;
                float pitch = block.stepsound.getPitch() * 0.8F;
                Player p = this.minecraft != null ? this.minecraft.player : null;
                if (p != null) {
                    p.playPositionalSound(block.stepsound.pool, vol, pitch);
                } else {
                    level.playSound(block.stepsound.pool, (float)x, (float)y, (float)z, vol, pitch);
                }
            }
            block.spawnBreakParticles(level, x, y, z, minecraft.particleManager);
        }
    }

    // ---------- Item dispatch helper ----------
    protected boolean useSelectedItem(Player player) {
        if (player == null || player.inventory == null || player.level == null) return false;

        int slot = player.inventory.selected;
        if (slot < 0 || slot >= player.inventory.slots.length) return false;

        int sv = player.inventory.slots[slot];
        if (sv < 256) return false;

        int itemId = sv - 256;
        if (itemId < 0 || itemId >= net.classicremastered.minecraft.level.itemstack.Item.items.length) return false;

        net.classicremastered.minecraft.level.itemstack.Item it = net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
        if (it == null) return false;

        try {
            if (player.minecraft != null && player.minecraft.renderer != null) {
                net.classicremastered.minecraft.render.HeldBlock hb = player.minecraft.renderer.heldBlock;
                if (hb != null) { hb.offset = -1; hb.moving = true; }
            }
        } catch (Throwable ignored) {}

        it.use(player, player.level);

        if (player.inventory.count[slot] <= 0) {
            player.inventory.slots[slot] = -1;
            player.inventory.count[slot] = 0;
        }
        return true;
    }
}
