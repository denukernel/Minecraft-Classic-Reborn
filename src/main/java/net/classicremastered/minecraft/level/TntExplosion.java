package net.classicremastered.minecraft.level;

import java.util.LinkedHashMap;
import java.util.Map;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.PrimedTnt;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.util.MathHelper;

/** Resistance-based blast for ordinary primed TNT. */
public final class TntExplosion {
    private static final int RAY_GRID = 16;
    private static final float STEP = .3f;

    private TntExplosion() {}

    public static void detonate(
            Level level, Entity source, Entity owner, float x, float y, float z, float power) {
        if (level.isNetworkMode() || power <= 0) return;
        Map<Pos, Block> affected = new LinkedHashMap<>();
        for (int i = 0; i < RAY_GRID; i++) {
            for (int j = 0; j < RAY_GRID; j++) {
                for (int k = 0; k < RAY_GRID; k++) {
                    if (i != 0
                            && i != RAY_GRID - 1
                            && j != 0
                            && j != RAY_GRID - 1
                            && k != 0
                            && k != RAY_GRID - 1) continue;
                    trace(level, x, y, z, i, j, k, power, affected);
                }
            }
        }
        damageEntities(level, source, owner, x, y, z, power * 2);
        Map<Pos, Block> removed = new LinkedHashMap<>();
        for (var entry : affected.entrySet()) {
            Pos pos = entry.getKey();
            Block block = entry.getValue();
            if (level.getTile(pos.x(), pos.y(), pos.z()) == block.id
                    && level.setTileNoNeighborChange(pos.x(), pos.y(), pos.z(), 0)
                    && level.getTile(pos.x(), pos.y(), pos.z()) != block.id) {
                removed.put(pos, block);
            }
        }
        for (var entry : removed.entrySet()) {
            Pos pos = entry.getKey();
            int bx = pos.x(), by = pos.y(), bz = pos.z();
            Block block = entry.getValue();
            if (level instanceof LevelInfiniteTerrain terrain && block.getLightValue() > 0) {
                terrain.setLight(bx, by, bz, 0);
            }
            if (level instanceof LevelInfiniteTerrain || level instanceof LevelInfiniteFlat) {
                level.onBlockChanged(bx, by, bz);
            }
            if (block == Block.TNT) {
                level.addEntity(
                        new PrimedTnt(
                                level,
                                bx + .5f,
                                by + .5f,
                                bz + .5f,
                                owner,
                                10 + level.random.nextInt(20)));
            } else {
                block.dropItems(level, bx, by, bz, Math.min(1, 1 / power));
                block.explode(level, bx, by, bz);
            }
            level.updateNeighborsAt(bx, by, bz, level.getTile(bx, by, bz));
        }
    }

    private static void trace(
            Level level,
            float x,
            float y,
            float z,
            int i,
            int j,
            int k,
            float power,
            Map<Pos, Block> affected) {
        float dx = i * 2f / (RAY_GRID - 1) - 1;
        float dy = j * 2f / (RAY_GRID - 1) - 1;
        float dz = k * 2f / (RAY_GRID - 1) - 1;
        float step = STEP / MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        float strength = power * (.7f + level.random.nextFloat() * .6f);
        for (; strength > 0; strength -= .225f) {
            int bx = MathHelper.floor(x), by = MathHelper.floor(y), bz = MathHelper.floor(z);
            if (!loaded(level, bx, by, bz)) {
                if (level.isInBounds(bx, by, bz)) return;
            } else {
                Block block = Block.blocks[level.getTile(bx, by, bz)];
                if (block != null) {
                    strength -= (resistance(block) + .3f) * STEP;
                    if (strength > 0) affected.putIfAbsent(new Pos(bx, by, bz), block);
                }
            }
            x += dx * step;
            y += dy * step;
            z += dz * step;
        }
    }

    private static boolean loaded(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z)) return false;
        if (level instanceof LevelInfiniteTerrain terrain)
            return terrain.chunks().isChunkLoaded(x >> 4, z >> 4);
        if (level instanceof LevelInfiniteFlat flat)
            return flat.chunks().isChunkLoaded(x >> 4, z >> 4);
        return true;
    }

    private static float resistance(Block block) {
        if (block == Block.BEDROCK) return 3_600_000;
        if (block == Block.OBSIDIAN
                || block == Block.PORTAL
                || block instanceof net.classicremastered.minecraft.level.tile.StructureBlock)
            return 1200;
        if (block.getLiquidType() != LiquidType.NOT_LIQUID) return 100;
        if (block == Block.STONE
                || block == Block.COBBLESTONE
                || block == Block.MOSSY_COBBLESTONE
                || block == Block.BRICK
                || block == Block.SLAB
                || block == Block.DOUBLE_SLAB
                || block == Block.FURNACE
                || block == Block.IRON_BLOCK
                || block == Block.GOLD_BLOCK
                || block == Block.DIAMOND_BLOCK_NEW
                || block == Block.REDSTONE_BLOCK) return 6;
        if (block == Block.COAL_ORE
                || block == Block.IRON_ORE
                || block == Block.GOLD_ORE
                || block == Block.REDSTONE_ORE
                || block == Block.DIAMOND_ORE_NEW
                || block == Block.LOG
                || block == Block.WOOD
                || block == Block.BOOKSHELF) return 3;
        if (block == Block.CHEST || block == Block.WORKBENCH) return 2.5f;
        if (block == Block.SANDSTONE
                || block == Block.CUT_SANDSTONE
                || block == Block.CHISELED_SANDSTONE
                || block == Block.SANDSTONE_SLAB
                || block == Block.SANDSTONE_STAIRS
                || block == Block.ORANGE_TERRACOTTA
                || block == Block.BLUE_TERRACOTTA) return .8f;
        if (block == Block.SMOOTH_SANDSTONE) return 6;
        if (block == Block.TNT || block == Block.FIRE) return 0;
        if (block == Block.GLASS || block == Block.TORCH || block == Block.REDSTONE_TORCH)
            return .3f;
        if (block == Block.DIRT || block == Block.SAND) return .5f;
        if (block == Block.GRASS || block == Block.GRAVEL) return .6f;
        if (block == Block.CACTUS || block == Block.LEAVES) return .4f;
        return block.isSolid() ? 1 : .2f;
    }

    private static void damageEntities(
            Level level, Entity source, Entity owner, float x, float y, float z, float radius) {
        AABB area =
                new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (Object value : level.findEntities(source, area).toArray()) {
            Entity entity = (Entity) value;
            if (entity.removed) continue;
            float dx = entity.x - x,
                    dy = (entity.bb.y0 + entity.bb.y1) * .5f - y,
                    dz = entity.z - z;
            float distance = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius) continue;
            int visible = 0;
            for (int i = 0; i < 3; i++) {
                float height = entity.bb.y0 + (entity.bb.y1 - entity.bb.y0) * (i + 1) / 4;
                if (level.clip(new Vec3D(entity.x, height, entity.z), new Vec3D(x, y, z), true)
                        == null) visible++;
            }
            float impact = (1 - distance / radius) * visible / 3;
            if (impact <= 0) continue;
            int damage = (int) ((impact * impact + impact) * 3.5f * radius + 1);
            if (entity instanceof Mob mob) mob.hurtExplosion(owner, damage);
            else entity.hurt(owner, damage);
            if (distance > .001f) {
                entity.xd += dx / distance * impact;
                entity.yd += dy / distance * impact;
                entity.zd += dz / distance * impact;
            }
        }
    }
}
