package net.classicremastered.minecraft.gamemode;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.MobSpawner;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ToolItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public final class SurvivalGameMode extends GameMode {
    // Seven progress units per normal tick, sixty per diamond tick.
    private static final int OBSIDIAN_PROGRESS_REQUIRED = 8_400;

    public SurvivalGameMode(Minecraft minecraft) {
        super(minecraft);
    }

    private int hitX, hitY, hitZ;
    private float hits;
    private int hitType;
    private int hardness; // effective hardness for cracks/progress
    private int hitDelay;
    private MobSpawner spawner;

    @Override
    public void apply(Level level) {
        super.apply(level);
    }

    @Override
    public boolean canPlace(int block) {
        if (minecraft != null && minecraft.level != null && minecraft.level.creativeMode)
            return true;
        if (minecraft == null || minecraft.player == null) return false;
        return minecraft.player.inventory.hasResource(block);
    }

    public boolean useItemOn(Player player, int x, int y, int z, int face) {
        if (player == null) return false;
        int selId = player.inventory.getSelected();
        if (selId <= 0) return false;

        if (selId == net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256
                || selId == Block.REDSTONE_WIRE.id) {
            if (!canPlace(selId) || !tryPlaceBlock(player, x, y, z, Block.REDSTONE_WIRE.id, face))
                return false;
            if (!level.creativeMode) player.inventory.removeSelected(1);
            return true;
        }

        if (selId >= 256) return useSelectedItem(player);

        if (selId < 256 && Block.blocks[selId] != null) {
            if (!this.canPlace(selId)) return false;
            boolean placed = this.tryPlaceBlock(player, x, y, z, selId, face);
            if (!placed) return false;
            player.inventory.removeResource(1);
            return true;
        }
        return false;
    }

    @Override
    public void hitBlock(int x, int y, int z, int side) {
        final int type = minecraft.level.getTile(x, y, z);
        if (type == Block.FIRE.id) {
            breakBlock(x, y, z);
            return;
        }
        if (hitDelay > 0) {
            hitDelay--;
            return;
        }
        if (x != hitX || y != hitY || z != hitZ) {
            hits = 0;
            hitX = x;
            hitY = y;
            hitZ = z;
        }
        if (type != hitType) {
            hits = 0;
            hitType = type;
        }
        if (type == 0) {
            hits = 0;
            return;
        }
        final Block block = Block.blocks[type];
        block.spawnBlockParticles(minecraft.level, x, y, z, side, minecraft.particleManager);
        hits += miningProgress(block);
        if (hits >= requiredProgress(block)) {
            breakBlock(x, y, z);
            hits = 0;
            hitDelay = 5;
        }
    }

    @Override
    public void breakBlock(int x, int y, int z) {
        int block = minecraft.level.getTile(x, y, z);
        if (block == 0) return;
        if (block == Block.FIRE.id) {
            resetHits();
        } else if ((block != Block.SNOW_BLOCK.id || holdingTool(ToolItem.Kind.SHOVEL))
                && (block != Block.MAGMA.id || holdingTool(ToolItem.Kind.PICKAXE))) {
            Block.blocks[block].onMined(minecraft.level, x, y, z, heldTool());
        }
        super.breakBlock(x, y, z);
    }

    private boolean holdingTool(ToolItem.Kind kind) {
        ToolItem tool = heldTool();
        return tool != null && tool.kind() == kind;
    }

    private ToolItem heldTool() {
        int selected = minecraft.player.inventory.getSelected();
        if (selected >= 256
                && selected - 256 < Item.items.length
                && Item.items[selected - 256] instanceof ToolItem tool) return tool;
        return null;
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
        if (hits <= 0) {
            minecraft.levelRenderer.cracks = 0.0F;
            return;
        }
        int type = minecraft.level.getTile(hitX, hitY, hitZ);
        if (type == 0) {
            minecraft.levelRenderer.cracks = 0.0F;
            return;
        }
        Block b = Block.blocks[type];
        minecraft.levelRenderer.cracks =
                Math.min(1f, (hits + Math.max(0, time) * miningProgress(b)) / requiredProgress(b));
    }

    @Override
    public float getReachDistance() {
        return 4.0F;
    }

    @Override
    public boolean useItem(Player player, int type) {
        if (super.useItem(player, type)) return true;
        Block block = Block.blocks[type];
        if (block == Block.RED_MUSHROOM && minecraft.player.inventory.removeResource(type)) {
            player.hurt(null, 3);
            return true;
        } else if (block == Block.BROWN_MUSHROOM
                && minecraft.player.inventory.removeResource(type)) {
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

    private float miningProgress(Block block) {
        ToolItem tool = heldTool();
        if (block == Block.OBSIDIAN) return tool != null && tool.isDiamondPickaxe() ? 60f : 7f;
        return tool == null ? 1f : tool.miningSpeed(block);
    }

    private static int requiredProgress(Block block) {
        return block == Block.OBSIDIAN
                ? OBSIDIAN_PROGRESS_REQUIRED
                : Math.max(0, block.getHardness()) + 1;
    }
}
