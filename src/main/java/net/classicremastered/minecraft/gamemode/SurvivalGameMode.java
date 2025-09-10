package net.classicremastered.minecraft.gamemode;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.MobSpawner;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;

public final class SurvivalGameMode extends GameMode {
    public SurvivalGameMode(Minecraft minecraft) {
        super(minecraft);
    }

    private int hitX, hitY, hitZ;
    private int hits;
    private int hardness;   // effective hardness for cracks/progress
    private int hitDelay;
    private MobSpawner spawner;

    @Override
    public void apply(Level level) {
        super.apply(level);
    }

    @Override
    public boolean canPlace(int block) {
        if (minecraft != null && minecraft.level != null && minecraft.level.creativeMode) return true;
        if (minecraft == null || minecraft.player == null) return false;
        return minecraft.player.inventory.hasResource(block);
    }

    public boolean useItemOn(Player player, int x, int y, int z, int face) {
        if (player == null) return false;
        int selId = player.inventory.getSelected();
        if (selId <= 0) return false;

        if (selId >= 256) {
            int itemId = selId - 256;
            if (itemId >= 0 && itemId < net.classicremastered.minecraft.level.itemstack.Item.items.length) {
                net.classicremastered.minecraft.level.itemstack.Item it =
                        net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
                if (it != null) {
                    try {
                        if (player.minecraft != null && player.minecraft.renderer != null) {
                            net.classicremastered.minecraft.render.HeldBlock hb = player.minecraft.renderer.heldBlock;
                            if (hb != null) { hb.offset = -1; hb.moving = true; }
                        }
                    } catch (Throwable ignored) {}
                    it.use(player, player.level);
                    int slot = player.inventory.selected;
                    if (player.inventory.count[slot] <= 0) {
                        player.inventory.slots[slot] = -1;
                        player.inventory.count[slot] = 0;
                    }
                    return true;
                }
            }
            return false;
        }

        if (selId < 256 && Block.blocks[selId] != null) {
            if (!this.canPlace(selId)) return false;
            boolean placed = this.tryPlaceBlock(player, x, y, z, selId);
            if (!placed) return false;
            Block.blocks[selId].onPlace(minecraft.level, x, y, z);
            player.inventory.removeResource(1);
            return true;
        }
        return false;
    }

    @Override
    public void hitBlock(int x, int y, int z, int side) {
        if (hitDelay > 0) { hitDelay--; return; }
        if (x != hitX || y != hitY || z != hitZ) {
            hits = 0;
            hitX = x; hitY = y; hitZ = z;
        }
        final int type = minecraft.level.getTile(x, y, z);
        if (type == 0) { hits = 0; return; }
        final Block block = Block.blocks[type];
        final int base = Math.max(0, block.getHardness());
        final float mult = getToolSpeed(minecraft.player, block);
        final int required = Math.max(1, (int)Math.ceil((base + 1) / Math.max(1.0f, mult)));
        block.spawnBlockParticles(minecraft.level, x, y, z, side, minecraft.particleManager);
        if (++hits >= required) {
            breakBlock(x, y, z);
            hits = 0;
            hitDelay = 5;
        }
    }

    @Override
    public void breakBlock(int x, int y, int z) {
        int block = minecraft.level.getTile(x, y, z);
        Block.blocks[block].onBreak(minecraft.level, x, y, z);
        super.breakBlock(x, y, z);
    }

    @Override
    public void hitBlock(int x, int y, int z) {
        int block = this.minecraft.level.getTile(x, y, z);
        if (block > 0 && Block.blocks[block].getHardness() == 0) {
            breakBlock(x, y, z);
        }
    }

    @Override
    public void resetHits() {
        this.hits = 0;
        this.hitDelay = 0;
    }

    @Override
    public void applyCracks(float time) {
        if (hits <= 0) { minecraft.levelRenderer.cracks = 0.0F; return; }
        int type = minecraft.level.getTile(hitX, hitY, hitZ);
        if (type == 0) { minecraft.levelRenderer.cracks = 0.0F; return; }
        Block b = Block.blocks[type];
        int base = Math.max(0, b.getHardness());
        float mult = getToolSpeed(minecraft.player, b);
        int required = Math.max(1, (int)Math.ceil((base + 1) / Math.max(1.0f, mult)));
        minecraft.levelRenderer.cracks = ((float)hits + time) / (float)required;
    }

    @Override
    public float getReachDistance() { return 4.0F; }

    @Override
    public boolean useItem(Player player, int type) {
        if (super.useItem(player, type)) return true;
        Block block = Block.blocks[type];
        if (block == Block.RED_MUSHROOM && minecraft.player.inventory.removeResource(type)) {
            player.hurt(null, 3);
            return true;
        } else if (block == Block.BROWN_MUSHROOM && minecraft.player.inventory.removeResource(type)) {
            player.heal(5);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void preparePlayer(Player player) {
        // Clear hotbar & inventory for true survival start
        for (int i = 0; i < player.inventory.slots.length; i++) {
            player.inventory.slots[i] = -1;
            player.inventory.count[i] = 0;
        }
    }


    @Override
    public void spawnMob() {
        // handled universally in Level.tick()
    }


    @Override
    public void prepareLevel(Level level) {
        spawner = new MobSpawner(level);
        minecraft.progressBar.setText("Spawning..");
        int area = level.width * level.height * level.depth / 800;

        // Initial world generation: ONLY passives (no hostiles).
        spawner.spawn(area, null, minecraft.progressBar, false);
    }

    // -------- Tool multiplier logic --------

    private float getToolSpeed(Player player, Block block) {
        if (player == null || player.inventory == null) return 1.0f;

        int sel = player.inventory.getSelected();
        if (sel < 256) return 1.0f; // holding a block or empty

        int itemId = sel - 256;
        if (itemId < 0 || itemId >= net.classicremastered.minecraft.level.itemstack.Item.items.length) return 1.0f;

        net.classicremastered.minecraft.level.itemstack.Item it = net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
        if (it == null) return 1.0f;

        String n = it.name != null ? it.name.toLowerCase(java.util.Locale.ROOT) : "";

        if (n.contains("mining axe") || n.contains("pickaxe") || n.equals("pick") || n.equals("pick axe")) {
            if (block.prefersPickaxe()) return 3.0f;
            return 1.0f;
        }
        if (n.contains("shovel") || n.contains("spade")) {
            if (block.prefersShovel()) return 3.0f;
            return 1.0f;
        }
        if (n.contains("sword")) return 1.0f;
        return 1.0f;
    }
}
