package net.classicremastered.minecraft.mob.ai.goal.zombie;

import java.util.HashMap;
import java.util.Map;
import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.LeavesBaseBlock;
import net.classicremastered.minecraft.level.tile.StairBlock;
import net.classicremastered.minecraft.mob.ZombieBuilder;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.Goal;
import net.classicremastered.minecraft.mob.ai.zombie.ZombieBuilderAI;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class BuildStepsGoal extends Goal {

    private final ZombieBuilder builder;
    private int cooldown, poseTicks, travelTicks;
    private int stepX, stepY, stepZ, breakX, breakY, breakZ, breakHP;
    private float goalX, goalZ;
    private boolean descending, stepReady;
    private int routeCheckTicks;
    private boolean walkingRoute;
    private float routeX, routeY, routeZ;
    private int stalledTicks;
    private final Map<Long, Integer> rejectedSteps = new HashMap<>();
    private float progressX, progressY, progressZ;

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
        rejectedSteps.values().removeIf(expiry -> expiry <= builder.tickCount);
        if (routeCheckTicks > 0) routeCheckTicks--;
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
        builder.leaveTowerFor(p);
        if (travelTicks == 0 && builder.onGround && hasWalkingRoute(p)) {
            travelTicks = 0;
            breakHP = 0;
            builder.poseBreaking = builder.posePlacing = false;
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
                if (!stepReady) {
                    if (travelTicks == 0) rejectStep(stepX, stepY + 2, stepZ);
                    return;
                }
            }
            float sx = stepX + .5f - builder.x,
                    sz = stepZ + .5f - builder.z,
                    len = (float) Math.sqrt(sx * sx + sz * sz);
            if (builder.onGround && Math.abs(builder.bb.y0 - (stepY + 1)) < .2f && len < .4f) {
                travelTicks = 0;
            } else if (travelTicks == 0 || stalled()) {
                rejectStep(stepX, stepY + (descending ? 2 : 0), stepZ);
            } else if (builder.onGround
                    && builder.bb.y0 < stepY + .9f
                    && cooldown == 0
                    && len < 1.6f) {
                builder.yd = .42f;
                cooldown = 6;
            }
            return;
        }
        descending = false;
        stalledTicks = 0;
        if (!builder.onGround) return;
        int fx = (int) Math.floor(builder.x),
                fy = (int) Math.floor(builder.bb.y0 + .01f),
                fz = (int) Math.floor(builder.z);
        int targetFeet = (int) Math.floor(p.bb.y0 + .01f);
        if (targetFeet == fy && dx * dx + dz * dz < .6f) return;
        int nx = fx, nz = fz;
        if (Math.abs(dx) >= Math.abs(dz)) nx += dx >= 0 ? 1 : -1;
        else nz += dz > 0 ? 1 : -1;
        if (targetFeet > fy) {
            float best = Float.POSITIVE_INFINITY;
            for (int direction = 0; direction < 4; direction++) {
                int x = fx + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                int z = fz + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                if (!canBuildAscent(x, fy, z, p)) continue;
                float distance =
                        (p.x - x - .5f) * (p.x - x - .5f) + (p.z - z - .5f) * (p.z - z - .5f);
                // Prefer an open staircase beside the canopy over tunneling into it.
                for (int y = fy + 1; y <= Math.max(fy + 2, targetFeet - 1); y++) {
                    if (Block.blocks[builder.level.getTile(x, y, z)] instanceof LeavesBaseBlock)
                        distance += 3;
                }
                if (distance < best) {
                    best = distance;
                    nx = x;
                    nz = z;
                }
            }
            if (!Float.isFinite(best)) return;
        }
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
            if (!bodySpace(nx, fy + 1, nz)) {
                clear(nx, fy + 1, nz);
                return;
            }
            if (!bodySpace(nx, fy + 2, nz)) {
                clear(nx, fy + 2, nz);
                return;
            }
            if (air(nx, fy - 1, nz)) {
                place(nx, fy - 1, nz);
                return;
            }
            if (replaceable(nx, fy, nz)) {
                if (!place(nx, fy, nz)) {
                    if (builder.bb.intersects(new AABB(nx, fy, nz, nx + 1, fy + 1, nz + 1))) {
                        builder.xd -= (nx - fx) * .08f;
                        builder.zd -= (nz - fz) * .08f;
                    } else if (cooldown == 0) {
                        rejectStep(nx, fy, nz);
                    }
                    return;
                }
            }
            if (supportsConstruction(nx, fy, nz)) {
                // Lift off in the current column before moving onto the next tread.
                if (!bodySpace(fx, fy + 2, fz)) {
                    if (breakableBlock(fx, fy + 2, fz) != null) clear(fx, fy + 2, fz);
                    else rejectStep(nx, fy, nz);
                    return;
                }
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
            if (!descending && builder.bb.y0 <= stepY + 1.3f) {
                ai.moveToBuiltStep(stepX + .5f, stepY + 1, stepZ + .5f);
            } else {
                ai.navigateTo(stepX + .5f, stepY + 1, stepZ + .5f, ai.runSpeed);
            }
        } else if (walkingRoute
                && ai.attackTarget == null
                && builder.level.getPlayer() instanceof Player player
                && player.health > 0
                && !player.removed
                && !CreativeModeHelper.shouldIgnoreTarget(player)
                && Math.abs(player.x - routeX) <= .5f
                && Math.abs(player.z - routeZ) <= .5f
                && Math.abs(player.bb.y0 - routeY) <= .5f
                && Math.abs(routeY - builder.bb.y0) >= 1
                && Math.hypot(routeX - builder.x, routeZ - builder.z) <= 40
                && CombatRules.difficulty(builder.level) != Difficulty.PEACEFUL
                && !builder.isInWater()
                && !builder.isInLava()) {
            // A staircase built by another builder may work before the canopy is visible.
            ai.navigateTo(routeX, routeY, routeZ, ai.runSpeed);
        }
    }

    private boolean canBuildAscent(int x, int y, int z, Player player) {
        if (!loaded(x, z)
                || !builder.level.isInBounds(x, y + 2, z)
                || rejectedSteps.containsKey(Level.getCoordKey(x, y, z))) return false;
        if (!replaceable(x, y, z) && !supportsConstruction(x, y, z)) return false;
        AABB landing = new AABB(x + .2f, y + 1, z + .2f, x + .8f, y + 2.8f, z + .8f);
        for (Object entity : builder.level.findEntities(builder, landing)) {
            if (entity instanceof ZombieBuilder other
                    && !other.removed
                    && other.health > 0
                    && other.bb.intersects(landing)) return false;
        }
        for (int above = y + 1; above <= y + 2; above++) {
            if (above == (int) Math.floor(player.bb.y0 - .01f)
                    && x == (int) Math.floor(player.x)
                    && z == (int) Math.floor(player.z)) return false;
            if (!bodySpace(x, above, z) && breakableBlock(x, above, z) == null) return false;
        }
        return true;
    }

    private boolean stalled() {
        float dx = builder.x - progressX,
                dy = builder.bb.y0 - progressY,
                dz = builder.z - progressZ;
        if (dx * dx + dz * dz >= .04f || builder.onGround && Math.abs(dy) >= .45f) {
            progressX = builder.x;
            progressY = builder.bb.y0;
            progressZ = builder.z;
            stalledTicks = 0;
        }
        return ++stalledTicks >= 20;
    }

    private void rejectStep(int x, int y, int z) {
        rejectedSteps.put(Level.getCoordKey(x, y, z), builder.tickCount + 100);
        stalledTicks = travelTicks = routeCheckTicks = 0;
        if (builder.ai instanceof ZombieBuilderAI ai) ai.clearNavigation();
    }

    private boolean hasWalkingRoute(Player player) {
        if (Math.abs(player.x - routeX) > .5f
                || Math.abs(player.bb.y0 - routeY) > .5f
                || Math.abs(player.z - routeZ) > .5f) routeCheckTicks = 0;
        if (routeCheckTicks == 0) {
            routeCheckTicks = 10;
            routeX = player.x;
            routeY = player.bb.y0;
            routeZ = player.z;
            var path =
                    Pathfinder.findChasePath(builder.level, builder, routeX, routeY, routeZ, 1024);
            walkingRoute =
                    path != null
                            && !path.isEmpty()
                            && Math.abs(path.get(path.size() - 1).feetY - routeY) < 1.25f;
        }
        return walkingRoute;
    }

    private boolean prepareDescent() {
        // Clear the whole swept body space before opening the lower landing.
        for (int y = stepY + 3; y > stepY; y--) {
            if (!air(stepX, y, stepZ)) {
                clear(stepX, y, stepZ);
                return false;
            }
        }
        int x = (int) Math.floor(builder.x), z = (int) Math.floor(builder.z);
        int facing = stepX > x ? 4 : stepX < x ? 5 : stepZ > z ? 2 : 3;
        int stair = StairBlock.id(2, facing, false);
        if (builder.level.getTile(stepX, stepY, stepZ) == stair) return true;
        if (!air(stepX, stepY, stepZ)) {
            if (builder.level.isSolidTile(stepX, stepY, stepZ)
                    && breakableBlock(stepX, stepY, stepZ) == null) return true;
            clear(stepX, stepY, stepZ);
            return false;
        }
        // Extend the underside of the current footing, then attach the next tread.
        if (!builder.level.isSolidTile(x, stepY, z)) {
            place(x, stepY, z);
            return false;
        }
        return place(stepX, stepY, stepZ, stair);
    }

    private boolean descentSpace(int x, int feetY, int z) {
        if (!loaded(x, z) || feetY < 3 || feetY + 1 >= builder.level.depth - 2) return false;
        if (rejectedSteps.containsKey(Level.getCoordKey(x, feetY, z))) return false;
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
        return place(x, y, z, Block.STONE.id);
    }

    private boolean supportsConstruction(int x, int y, int z) {
        Block block = Block.blocks[builder.level.getTile(x, y, z)];
        return block != null
                && (block.isSolid()
                        || block instanceof StairBlock
                        || block instanceof LeavesBaseBlock);
    }

    private boolean bodySpace(int x, int y, int z) {
        if (air(x, y, z)) return true;
        int id = builder.level.getTile(x, y, z);
        return id == Block.SNOW_LAYER.id;
    }

    private boolean replaceable(int x, int y, int z) {
        return y > 0 && y < builder.level.depth - 2 && loaded(x, z) && bodySpace(x, y, z);
    }

    private boolean place(int x, int y, int z, int block) {
        if (cooldown > 0 || !replaceable(x, y, z)) return false;
        AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
        if (builder.bb.intersects(cell) || !builder.level.findEntities(builder, cell).isEmpty())
            return false;
        if (!supportsConstruction(x, y - 1, z)
                && !supportsConstruction(x, y + 1, z)
                && !supportsConstruction(x - 1, y, z)
                && !supportsConstruction(x + 1, y, z)
                && !supportsConstruction(x, y, z - 1)
                && !supportsConstruction(x, y, z + 1)) return false;
        if ((net.classicremastered.minecraft.level.structure.GeneratedHouse.protects(
                                builder.level, x, y, z)
                        && !(descending && builder.canBuildTowerExit(x, y, z)))
                || !builder.level.setTile(x, y, z, block)) return false;
        cooldown = 6;
        poseTicks = 6;
        builder.posePlacing = true;
        builder.poseBreaking = false;
        routeCheckTicks = 0;
        return true;
    }

    private Block breakableBlock(int x, int y, int z) {
        int id = builder.level.getTile(x, y, z);
        Block b = id > 0 ? Block.blocks[id] : null;
        if ((descending || b instanceof LeavesBaseBlock)
                && net.classicremastered.minecraft.level.structure.GeneratedHouse.protects(
                        builder.level, x, y, z)
                && !builder.canBuildTowerExit(x, y, z)) return null;
        if (b == null
                || !(b.isSolid() || !descending && b instanceof LeavesBaseBlock)
                || b == Block.BEDROCK
                || b == Block.OBSIDIAN
                || b == Block.GLASS
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
        float reach = descending ? 3f : 2.2f;
        if (dx * dx + dy * dy + dz * dz > reach * reach) return;
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
            routeCheckTicks = 0;
            if (builder.level.particleEngine != null)
                b.spawnBreakParticles(builder.level, x, y, z, builder.level.particleEngine);
        }
    }
}
