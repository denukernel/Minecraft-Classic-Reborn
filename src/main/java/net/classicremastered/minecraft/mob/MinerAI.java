package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.OreMaterials;
import net.classicremastered.minecraft.level.itemstack.ToolItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.OreBlock;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;

final class MinerAI extends WorkerAI {
    private static final long serialVersionUID = 1L;
    private static final int DELIVERY_SIZE = 8;
    private static final float MINING_STANCE_RANGE = 1.55f;
    private static final float MINING_HIT_REACH = 3.5f;
    private transient MiningJob job;
    private transient int progress, scanDelay;
    private transient Vec3D surveyCenter, surveyGoal;
    private transient int surveyDirection;
    private transient Builder unavailableBuilder;
    private transient int retryDelivery;

    private record MiningJob(Pos ore, int block, int tool, Vec3D stand) {
    }

    @Override
    void work(Worker worker) {
        Miner miner = (Miner) worker;
        ToolItem tool = miner.workTool();
        int cargo = cargoCount(miner);
        Builder builder = cargo > 0 ? deliveryTarget(miner) : null;
        if (builder != null
                && (cargo >= DELIVERY_SIZE || tool == null || miner.distanceToSqr(builder) < 6.25f)) {
            deliver(miner, builder);
            return;
        }
        if (tool == null) {
            miner.setActivity("Needs a pickaxe or axe");
            job = null;
            return;
        }
        if (job != null && (job.tool() != tool.id
                || level.getTile(job.ore().x(), job.ore().y(), job.ore().z()) != job.block())) {
            job = null;
        }
        if (job == null && scanDelay-- <= 0) {
            scanDelay = 10;
            progress = 0;
            job = findSource(miner, tool);
        }
        if (job == null) {
            if (builder != null)
                deliver(miner, builder);
            else {
                miner.setActivity(
                        tool.kind() == ToolItem.Kind.AXE ? "Looking for logs" : "Looking for stone or ore");
                if (miner.roomFor(
                        tool.kind() == ToolItem.Kind.AXE ? Block.WOOD.id : Block.COBBLESTONE.id) > 0) {
                    survey(miner);
                } else
                    miner.setActivity("Mining pack is full");
            }
            return;
        }
        surveyGoal = null;
        Pos pos = job.ore();
        Block block = Block.blocks[job.block()];
        if (!canMine(miner, pos, block) || miner.roomFor(block.getDrop()) < maxYield(block)) {
            job = null;
            return;
        }
        MovingObjectPosition hit = miningHit(eyes(), pos);
        if (hit == null) {
            Vec3D stand = job.stand();
            progress = 0;
            miner.setActivity("Walking to " + block.name);
            if (stand == null || eyes().add(0, -miner.getEyeHeight(1), 0).distanceSquared(stand) < .0064f
                    || !approachStand(stand)) {
                job = null;
            }
            return;
        }
        miner.setActivity(tool.kind() == ToolItem.Kind.AXE ? "Chopping wood" : "Mining " + block.name);
        face(hit.vec);
        miner.xd = miner.zd = 0;
        if (progress++ % 8 == 0) {
            miner.swingHand();
            if (level.particleEngine != null) {
                block.spawnBlockParticles(level, pos.x(), pos.y(), pos.z(), hit.face, level.particleEngine);
            }
        }
        int workTicks = Math.max(8, (int) Math.ceil((block.getHardness() + 1) / tool.miningSpeed(block)));
        if (progress < workTicks) {
            miner.updateCrack(pos, block.id, (float) progress / workTicks);
            return;
        }
        if (level.setTile(pos.x(), pos.y(), pos.z(), 0)) {
            int count = block.getDropCount();
            for (int i = 0; i < count; i++)
                miner.pack().addResource(block.getDrop());
            if (level.particleEngine != null)
                block.spawnBreakParticles(level, pos.x(), pos.y(), pos.z(), level.particleEngine);
            level.playSound(block.stepsound.pool, miner, .55f, 1);
        }
        scanDelay = 0;
        job = null;
    }

    private MiningJob findSource(Miner miner, ToolItem tool) {
        int cx = (int) Math.floor(miner.x), cy = (int) Math.floor(miner.bb.y0),
                cz = (int) Math.floor(miner.z);
        MiningJob best = null;
        float bestScore = Float.POSITIVE_INFINITY;
        for (int x = cx - 7; x <= cx + 7; x++) {
            for (int z = cz - 7; z <= cz + 7; z++) {
                for (int y = cy - 1; y <= cy + 3; y++) {
                    if (!level.isInBounds(x, y, z))
                        continue;
                    int id = level.getTile(x, y, z);
                    Block block = Block.blocks[id];
                    if (!matches(tool, block) || miner.roomFor(block.getDrop()) < maxYield(block))
                        continue;
                    float dx = x + .5f - miner.x, dz = z + .5f - miner.z;
                    float score = dx * dx + dz * dz + (y - cy) * (y - cy);
                    if (block instanceof OreBlock || block == Block.REDSTONE_ORE)
                        score -= 8;
                    if (score >= bestScore)
                        continue;
                    Pos pos = new Pos(x, y, z);
                    if (!canMine(miner, pos, block))
                        continue;
                    Vec3D stand = miningHit(eyes(), pos) != null
                            ? new Vec3D(miner.x, miner.bb.y0, miner.z)
                            : approach(pos, cy);
                    if (stand == null)
                        continue;
                    best = new MiningJob(pos, id, tool.id, stand);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private Vec3D approach(Pos pos, int feetY) {
        Vec3D best = null, eye = eyes();
        float nearest = Float.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;
                int x = pos.x() + dx, z = pos.z() + dz;
                for (int y = Math.max(feetY - 2, pos.y() - 2); y <= Math.min(feetY + 2, pos.y() + 1); y++) {
                    Vec3D stand = new Vec3D(x + .5f, y, z + .5f);
                    float distance = eye.distanceSquared(stand);
                    if (distance >= nearest || miningHit(stand.add(0, mob.getEyeHeight(1), 0), pos) == null
                            || reachableStand(x, y, z) == null)
                        continue;
                    best = stand;
                    nearest = distance;
                }
            }
        }
        return best;
    }

    private void survey(Miner miner) {
        if (surveyCenter == null)
            surveyCenter = new Vec3D(miner.x, miner.bb.y0, miner.z);
        if (surveyGoal != null) {
            Vec3D feet = new Vec3D(miner.x, miner.bb.y0, miner.z);
            if (feet.distanceSquared(surveyGoal) > .25f && approachStand(surveyGoal))
                return;
            surveyGoal = null;
        }
        // Check one new lookout per scan to keep large mining crews inexpensive.
        if (scanDelay != 10)
            return;
        int direction = surveyDirection++ & 15;
        double angle = direction * Math.PI / 4;
        int radius = direction < 8 ? 6 : 11;
        int x = (int) Math.floor(surveyCenter.x + Math.cos(angle) * radius);
        int z = (int) Math.floor(surveyCenter.z + Math.sin(angle) * radius);
        int baseY = Math.round(surveyCenter.y);
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            surveyGoal = reachableStand(x, baseY + dy, z);
            if (surveyGoal != null)
                return;
        }
    }

    private MovingObjectPosition miningHit(Vec3D from, Pos pos) {
        Vec3D center = new Vec3D(pos.x() + .5f, pos.y() + .5f, pos.z() + .5f);
        float dx = center.x - from.x, dz = center.z - from.z;
        if (dx * dx + dz * dz > MINING_STANCE_RANGE * MINING_STANCE_RANGE)
            return null;
        var hit = level.clip(from.add(0, 0, 0), center);
        return hit != null && hit.x == pos.x() && hit.y == pos.y() && hit.z == pos.z()
                && from.distanceSquared(hit.vec) <= MINING_HIT_REACH * MINING_HIT_REACH ? hit : null;
    }

    private boolean canMine(Miner miner, Pos pos, Block block) {
        if (block == null)
            return false;
        if (block == Block.COBBLESTONE || block == Block.SANDSTONE) {
            for (Object entity : level.blockMap.all) {
                if (entity instanceof Builder builder && !builder.removed && builder.protects(pos))
                    return false;
            }
        }
        float x = pos.x(), y = pos.y(), z = pos.z();
        AABB floor = new AABB(x, y + .98f, z, x + 1, y + 1.15f, z + 1);
        if (miner.bb.intersects(floor))
            return false;
        for (Object entity : level.findEntities(miner, floor)) {
            if (entity instanceof Mob living && living.health > 0 && !living.removed)
                return false;
        }
        return true;
    }

    private static boolean matches(ToolItem tool, Block block) {
        if (tool.kind() == ToolItem.Kind.AXE)
            return block == Block.LOG;
        return block == Block.STONE || block == Block.COBBLESTONE || block == Block.SANDSTONE
                || block instanceof OreBlock || block == Block.REDSTONE_ORE;
    }

    private static int maxYield(Block block) {
        return block == Block.LOG ? 5 : block == Block.REDSTONE_ORE ? 4 : block == Block.COAL_ORE ? 3 : 1;
    }

    private static boolean cargo(int id) {
        return id == Block.COBBLESTONE.id || id == Block.SANDSTONE.id || id == Block.LOG.id
                || id == Block.WOOD.id || id == Item.COAL_ID + 256 || id == OreMaterials.RAW_IRON + 256
                || id == OreMaterials.RAW_GOLD + 256 || id == OreMaterials.DIAMOND + 256
                || id == Item.REDSTONE_DUST_ID + 256;
    }

    private int cargoCount(Miner miner) {
        int count = 0;
        for (int slot = 0; slot < miner.pack().totalSize(); slot++) {
            if (cargo(miner.pack().getId(slot)))
                count += miner.pack().getCount(slot);
        }
        return count;
    }

    private Builder deliveryTarget(Miner miner) {
        Builder best = null;
        float distance = 18 * 18;
        for (Object entity : level.findEntities(miner, miner.bb.grow(18, 5, 18))) {
            if (!(entity instanceof Builder builder) || !miner.worksWith(builder)
                    || builder == unavailableBuilder && miner.tickCount < retryDelivery)
                continue;
            float candidate = miner.distanceToSqr(builder);
            if (candidate >= distance)
                continue;
            for (int i = 0; i < miner.pack().totalSize(); i++) {
                int id = miner.pack().getId(i);
                if (cargo(id) && miner.pack().getCount(i) > 0 && builder.roomFor(id) > 0) {
                    best = builder;
                    distance = candidate;
                    break;
                }
            }
        }
        return best;
    }

    private void deliver(Miner miner, Builder builder) {
        miner.setActivity("Delivering to builder");
        if (miner.distanceToSqr(builder) > 6.25f || level.clip(eyes(),
                new Vec3D(builder.x, builder.bb.y0 + builder.getEyeHeight(1), builder.z), true) != null) {
            Vec3D stand = deliveryStand(builder);
            if (stand == null || !approachStand(stand)) {
                unavailableBuilder = builder;
                retryDelivery = miner.tickCount + 80;
                miner.setActivity("Looking for a route to builders");
            }
            return;
        }
        finishApproach();
        for (int i = 0; i < miner.pack().totalSize(); i++) {
            int id = miner.pack().getId(i), count = miner.pack().getCount(i);
            if (!cargo(id))
                continue;
            int remaining = count;
            while (remaining > 0 && builder.pack().addResource(id))
                remaining--;
            if (remaining != count)
                miner.pack().setIdCount(i, id, remaining);
        }
    }

    private Vec3D deliveryStand(Builder builder) {
        int cx = (int) Math.floor(builder.x), cy = Math.round(builder.bb.y0),
                cz = (int) Math.floor(builder.z);
        Vec3D builderEyes = new Vec3D(builder.x, builder.bb.y0 + builder.getEyeHeight(1), builder.z);
        Vec3D best = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;
                for (int dy = -1; dy <= 1; dy++) {
                    Vec3D stand = new Vec3D(cx + dx + .5f, cy + dy, cz + dz + .5f);
                    float distance = eyes().distanceSquared(stand);
                    Vec3D from = stand.add(0, mob.getEyeHeight(1), 0);
                    if (distance >= nearest || from.distanceSquared(builderEyes) > 6.25f
                            || level.clip(from, builderEyes, true) != null
                            || reachableStand(cx + dx, cy + dy, cz + dz) == null)
                        continue;
                    best = stand;
                    nearest = distance;
                }
            }
        }
        return best;
    }
}
