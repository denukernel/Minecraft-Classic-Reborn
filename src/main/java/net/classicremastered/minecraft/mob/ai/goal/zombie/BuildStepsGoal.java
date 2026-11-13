package net.classicremastered.minecraft.mob.ai.goal.zombie;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ZombieBuilder;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.mob.ai.zombie.ZombieBuilderAI;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class BuildStepsGoal extends Goal {

    private final ZombieBuilder builder;
    private int cooldown, poseTicks, travelTicks;
    private int stepX, stepY, stepZ, breakX, breakY, breakZ, breakHP;
    private float goalX, goalZ;
    private boolean descending, stepReady;

    public BuildStepsGoal(ZombieBuilder builder) {
        this.builder = builder;
        builder.poseBreaking = builder.posePlacing = false;
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public void tick() {

        if (cooldown > 0) cooldown--;
        if (poseTicks > 0 && --poseTicks == 0) {
            builder.poseBreaking = builder.posePlacing = false;
        }
        if (builder.level == null
                || builder.level.isNetworkMode()
                || builder.health <= 0
                || CombatRules.difficulty(builder.level) == Difficulty.PEACEFUL) {
            travelTicks = 0;
            return;
        }
        if (!(builder.level.getPlayer() instanceof Player p)
                || p.health <= 0
                || p.removed
                || CreativeModeHelper.shouldIgnoreTarget(p)) {
            travelTicks = 0;
            return;
        }
        float dx = p.x - builder.x, dz = p.z - builder.z;
        if (dx * dx + dz * dz > 40 * 40) {
            travelTicks = 0;
            return;
        }
        if (builder.ai instanceof BasicAI a) a.suppressRandomJump = false;
        if (builder.isInWater() || builder.isInLava()) {
            travelTicks = 0;
            return;
        }
        if (travelTicks > 0) {
            travelTicks--;
            if (Math.abs(goalX - p.x) > 2 || Math.abs(goalZ - p.z) > 2) {
                travelTicks = 0;
                return;
            }
            if (descending && !stepReady) {
                builder.xd = builder.zd = 0;
                stepReady = prepareDescent();
                if (!stepReady) return;
                builder.leaveTowerFor(p);
            }
            float sx = stepX + .5f - builder.x,
                    sz = stepZ + .5f - builder.z,
                    len = (float) Math.sqrt(sx * sx + sz * sz);
            if (len > .12f) {
                builder.xd += sx / len * .035f;
                builder.zd += sz / len * .035f;
                builder.yRot = (float) Math.toDegrees(Math.atan2(sz, sx)) - 90;
            }
            if (builder.onGround && Math.abs(builder.bb.y0 - (stepY + 1)) < .2f && len < .3f) {
                travelTicks = 0;
            } else if (builder.onGround && builder.bb.y0 < stepY + .9f && cooldown == 0) {
                builder.yd = .42f;
                cooldown = 6;
            }
            return;
        }
        descending = false;
        if (!builder.onGround) return;
        int fx = (int) Math.floor(builder.x),
                fy = (int) Math.floor(builder.bb.y0 + .01f),
                fz = (int) Math.floor(builder.z);
        int targetFeet = (int) Math.floor(p.bb.y0 + .01f);
        if (targetFeet >= fy && dx * dx + dz * dz < .6f) return;
        int nx = fx, nz = fz;
        if (Math.abs(dx) >= Math.abs(dz)) nx += dx >= 0 ? 1 : -1;
        else nz += dz > 0 ? 1 : -1;
        if (!loaded(nx, nz)) return;
        if (targetFeet < fy) {
            descending = true;
            if (!descentSpace(nx, fy, nz)) {
                float best = Float.POSITIVE_INFINITY;
                for (int direction = 0; direction < 4; direction++) {
                    int x = fx + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                    int z = fz + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                    float distance =
                            (p.x - x - .5f) * (p.x - x - .5f) + (p.z - z - .5f) * (p.z - z - .5f);
                    if (distance < best && descentSpace(x, fy, z)) {
                        best = distance;
                        nx = x;
                        nz = z;
                    }
                }
                if (best == Float.POSITIVE_INFINITY) return;
            }
            stepX = nx;
            stepY = fy - 2;
            stepZ = nz;
            goalX = p.x;
            goalZ = p.z;
            stepReady = false;
            travelTicks = 100;
            builder.xd = builder.zd = 0;
            return;
        }
        if (targetFeet == fy && air(nx, fy - 1, nz)) {
            if (place(nx, fy - 1, nz)) {
                stepX = nx;
                stepY = fy - 1;
                stepZ = nz;
                goalX = p.x;
                goalZ = p.z;
                travelTicks = 40;
            }
            return;
        }
        if (targetFeet > fy) {
            if (!air(nx, fy + 1, nz)) {
                clear(nx, fy + 1, nz);
                return;
            }
            if (!air(nx, fy + 2, nz)) {
                clear(nx, fy + 2, nz);
                return;
            }
            if (air(nx, fy - 1, nz)) {
                place(nx, fy - 1, nz);
                return;
            }
            if (air(nx, fy, nz)) {
                if (!place(nx, fy, nz)) {
                    if (builder.bb.intersects(new AABB(nx, fy, nz, nx + 1, fy + 1, nz + 1))) {
                        builder.xd -= (nx - fx) * .08f;
                        builder.zd -= (nz - fz) * .08f;
                    }
                    return;
                }
            }
            if (builder.level.isSolidTile(nx, fy, nz)) {
                stepX = nx;
                stepY = fy;
                stepZ = nz;
                goalX = p.x;
                goalZ = p.z;
                travelTicks = 50;
                builder.yd = .42f;
            }
        } else if (builder.horizontalCollision) {
            // Only clear the two cells the next walking step actually requires.
            if (!air(nx, fy + 1, nz)) clear(nx, fy + 1, nz);
            else if (!air(nx, fy, nz)) clear(nx, fy, nz);
        }
    }

    public void steer(ZombieBuilderAI ai) {
        if (travelTicks > 0 && !builder.isInWater() && !builder.isInLava()) {
            ai.suppressRandomJump = true;
            if (descending && !stepReady) {
                ai.holdForConstruction();
                return;
            }
            ai.navigateTo(stepX + .5f, stepY + 1, stepZ + .5f, ai.runSpeed);
        }
    }

    private boolean prepareDescent() {
        // Clear the whole swept body space before opening the lower landing.
        for (int y = stepY + 3; y > stepY; y--) {
            if (!air(stepX, y, stepZ)) {
                clear(stepX, y, stepZ);
                return false;
            }
        }
        if (builder.level.isSolidTile(stepX, stepY, stepZ)) return true;
        int x = (int) Math.floor(builder.x), z = (int) Math.floor(builder.z);
        // Extend the underside of the current footing, then attach the next tread.
        if (!builder.level.isSolidTile(x, stepY, z)) {
            place(x, stepY, z);
            return false;
        }
        return place(stepX, stepY, stepZ);
    }

    private boolean descentSpace(int x, int feetY, int z) {
        if (!loaded(x, z) || feetY < 3 || feetY + 1 >= builder.level.depth - 2) return false;
        if (!air(x, feetY - 2, z) && !builder.level.isSolidTile(x, feetY - 2, z)) return false;
        for (int y = feetY - 1; y <= feetY + 1; y++) {
            if (!air(x, y, z) && breakableBlock(x, y, z) == null) return false;
        }
        return true;
    }

    private boolean loaded(int x, int z) {
        if (builder.level instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        if (builder.level instanceof net.classicremastered.minecraft.level.LevelInfiniteFlat t)
            return t.chunks().isChunkLoaded(x >> 4, z >> 4);
        return true;
    }

    private boolean air(int x, int y, int z) {
        return y > 0
                && y < builder.level.depth - 2
                && loaded(x, z)
                && builder.level.getTile(x, y, z) == 0;
    }

    private boolean place(int x, int y, int z) {
        if (cooldown > 0 || !air(x, y, z)) return false;
        AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
        if (builder.bb.intersects(cell) || !builder.level.findEntities(builder, cell).isEmpty())
            return false;
        if (!builder.level.isSolidTile(x, y - 1, z)
                && !builder.level.isSolidTile(x, y + 1, z)
                && !builder.level.isSolidTile(x - 1, y, z)
                && !builder.level.isSolidTile(x + 1, y, z)
                && !builder.level.isSolidTile(x, y, z - 1)
                && !builder.level.isSolidTile(x, y, z + 1)) return false;
        if ((net.classicremastered.minecraft.level.structure.GeneratedHouse.protects(
                                builder.level, x, y, z)
                        && !(descending && builder.canBuildTowerExit(x, y, z)))
                || !builder.level.setTile(x, y, z, Block.STONE.id)) return false;
        cooldown = 6;
        poseTicks = 6;
        builder.posePlacing = true;
        builder.poseBreaking = false;
        return true;
    }

    private Block breakableBlock(int x, int y, int z) {
        if (descending
                && net.classicremastered.minecraft.level.structure.GeneratedHouse.protects(
                        builder.level, x, y, z)
                && !builder.canBuildTowerExit(x, y, z)) return null;
        int id = builder.level.getTile(x, y, z);
        Block b = id > 0 ? Block.blocks[id] : null;
        if (b == null
                || !b.isSolid()
                || b == Block.BEDROCK
                || b == Block.OBSIDIAN
                || b == Block.GLASS
                || b == Block.LEAVES
                || b == Block.TNT) return null;
        return b;
    }

    private void clear(int x, int y, int z) {
        if (cooldown > 0 || !loaded(x, z)) return;
        Block b = breakableBlock(x, y, z);
        if (b == null) return;
        float dx = x + .5f - builder.x,
                dy = y + .5f - (builder.bb.y0 + 1),
                dz = z + .5f - builder.z;
        if (dx * dx + dy * dy + dz * dz > 2.2f * 2.2f) return;
        if (breakHP <= 0 || x != breakX || y != breakY || z != breakZ) {
            breakX = x;
            breakY = y;
            breakZ = z;
            breakHP = Math.max(4, Math.min(40, b.getHardness()));
        }
        breakHP -= 4;
        cooldown = 6;
        poseTicks = 6;
        builder.poseBreaking = true;
        builder.posePlacing = false;
        if (breakHP <= 0) {
            builder.level.setTile(x, y, z, 0);
            if (builder.level.particleEngine != null)
                b.spawnBreakParticles(builder.level, x, y, z, builder.level.particleEngine);
        }
    }
}
