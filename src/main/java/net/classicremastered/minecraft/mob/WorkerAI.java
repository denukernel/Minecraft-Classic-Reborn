package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.BuilderDoorBlock;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;

abstract class WorkerAI extends BasicAI {
    private static final long serialVersionUID = 1L;
    private transient Vec3D workGoal, lastProgress, rejectedStand;
    private transient int progressTick, retryAfter;
    private transient Mob threat;
    private transient Builder.Site refuge;
    private transient Vec3D retreatGoal;
    private transient int nextThreatCheck, nextShelterCheck;

    Builder.Site escortShelter() {
        return level != null && !level.isDaytime() ? refuge : null;
    }

    Mob escortThreat() {
        return level != null
                        && !level.isDaytime()
                        && threat != null
                        && !threat.removed
                        && threat.health > 0
                ? threat
                : null;
    }

    @Override
    protected final void update() {
        Worker worker = (Worker) mob;
        holdPosition(worker.yRot);
        if (level.isNetworkMode() || worker.isInKnockback() || worker.isInWater() || worker.isInLava())
            return;
        if (worker.onGround
                || worker instanceof Builder builder && BuilderPlacement.onScaffold(builder)) {
            if (!retreatAtNight(worker)) work(worker);
        } else if (workGoal != null) navigateTo(workGoal.x, workGoal.y, workGoal.z, .8f);
    }

    private boolean retreatAtNight(Worker worker) {
        if (level.isDaytime() || CombatRules.difficulty(level) == Difficulty.PEACEFUL) {
            if (threat != null || refuge != null || retreatGoal != null) finishApproach();
            threat = null;
            refuge = null;
            retreatGoal = null;
            nextThreatCheck = nextShelterCheck = 0;
            return false;
        }
        if (mob.tickCount >= nextThreatCheck) {
            nextThreatCheck = mob.tickCount + 8;
            threat = nearbyThreat(worker);
        }
        if (refuge != null) {
            if (!BuilderDoorBlock.isInstalled(level, refuge.entrance()) || threatInside(refuge)) {
                refuge = null;
                retreatGoal = null;
                finishApproach();
            } else if (atRetreatGoal(worker)) {
                finishApproach();
                BuilderDoorBlock.close(level, refuge.entrance());
                worker.setActivity("Hiding until daylight");
                return true;
            } else if (approachStand(retreatGoal, 1f)) {
                worker.setActivity("Running for shelter");
                return true;
            } else {
                refuge = null;
                retreatGoal = null;
            }
        }
        if (threat == null) {
            if (retreatGoal != null) {
                finishApproach();
                retreatGoal = null;
            }
            return false;
        }
        if (mob.tickCount >= nextShelterCheck) {
            nextShelterCheck = mob.tickCount + 32;
            refuge = findShelter(worker);
            if (refuge != null) {
                worker.setActivity("Running for shelter");
                approachStand(retreatGoal, 1f);
                return true;
            }
        }
        worker.setActivity("Fleeing hostile mobs");
        if (retreatGoal == null
                || atRetreatGoal(worker)
                || threat.distanceTo(retreatGoal.x, retreatGoal.y, retreatGoal.z) < 3)
            retreatGoal = escapeStand();
        if (retreatGoal != null && !approachStand(retreatGoal, 1.15f)) retreatGoal = null;
        return true;
    }

    private Mob nearbyThreat(Worker worker) {
        Mob nearest = null;
        float distance = 12 * 12;
        for (Object entity : level.findEntities(worker, worker.bb.grow(12, 6, 12))) {
            if (!(entity instanceof Mob hostile)
                    || hostile.removed
                    || hostile.health <= 0
                    || hostile.corruptedToDefendPlayer) continue;
            boolean pursuing = hostile.ai instanceof BasicAI ai && ai.attackTarget == worker;
            float candidate = worker.distanceToSqr(hostile);
            if (candidate < distance
                    && (pursuing
                            || Defender.isEnemy(hostile)
                                    && HostileTargets.hasLineOfSight(worker, hostile))) {
                distance = candidate;
                nearest = hostile;
            }
        }
        return nearest;
    }

    private Builder.Site findShelter(Worker worker) {
        Builder.Site nearest = null;
        float best = 16;
        for (Builder.Site site : Builder.sitesNear(worker, 16)) {
            Pos door = site.entrance();
            int z = door.z() + 2;
            int roof = site.y() + 3;
            float threatAtDoor = threat.distanceTo(door.x() + .5f, door.y(), door.z() + .5f);
            if (!BuilderDoorBlock.isInstalled(level, door)
                    || threatInside(site)
                    || threatAtDoor < 3
                            && threatAtDoor
                                    < worker.distanceTo(door.x() + .5f, door.y(), door.z() + .5f)
                    || !level.isSolidTile(door.x(), roof, z)) continue;
            float distance = worker.distanceTo(door.x() + .5f, site.y(), z + .5f);
            if (distance >= best) continue;
            Vec3D stand = reachableStand(door.x(), site.y(), z);
            if (stand != null) {
                retreatGoal = stand;
                nearest = site;
                best = distance;
            }
        }
        return nearest;
    }

    private boolean threatInside(Builder.Site site) {
        return threat != null
                && threat.bb.y0 >= site.y() - .1f
                && threat.bb.y0 < site.y() + 2
                && threat.x >= site.x() + 1
                && threat.x < site.x() + site.width() - 1
                && threat.z >= site.z() + 1
                && threat.z < site.z() + site.width() - 1;
    }

    private boolean atRetreatGoal(Worker worker) {
        return retreatGoal != null
                && retreatGoal.distanceSquared(new Vec3D(worker.x, worker.bb.y0, worker.z))
                        < .65f * .65f;
    }

    private Vec3D escapeStand() {
        float currentDistance = mob.distanceTo(threat);
        double away = Math.atan2(mob.z - threat.z, mob.x - threat.x);
        int y = Math.round(mob.bb.y0);
        for (int distance : new int[] {7, 4, 2}) {
            for (int direction : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                double angle = away + direction * Math.PI / 6;
                int x = (int) Math.floor(mob.x + Math.cos(angle) * distance);
                int z = (int) Math.floor(mob.z + Math.sin(angle) * distance);
                for (int dy : new int[] {0, 1, -1, 2, -2}) {
                    if (threat.distanceTo(x + .5f, y + dy, z + .5f) <= currentDistance + .25f)
                        continue;
                    Vec3D stand = reachableStand(x, y + dy, z);
                    if (stand != null) return stand;
                }
            }
        }
        return null;
    }

    abstract void work(Worker worker);

    final Vec3D eyes() {
        return new Vec3D(mob.x, mob.bb.y0 + mob.getEyeHeight(1), mob.z);
    }

    final void face(Pos block) {
        face(new Vec3D(block.x() + .5f, block.y() + .5f, block.z() + .5f));
    }

    final void face(Vec3D point) {
        finishApproach();
        Vec3D delta = point.subtract(eyes());
        holdPosition((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        mob.xRot = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
    }

    final void finishApproach() {
        if (workGoal == null)
            return;
        workGoal = null;
        clearNavigation();
    }

    final Vec3D reachableStand(int x, int y, int z) {
        float px = x + .5f, pz = z + .5f;
        Vec3D feet = new Vec3D(px, y, pz);
        if (rejectedStand != null && mob.tickCount < retryAfter && feet.distanceSquared(rejectedStand) < .01f)
            return null;
        if (Pathfinder.canWalkStraight(level, mob, px, y, pz) || Pathfinder.canStandAt(level, mob, px, y, pz)
                && Pathfinder.findPath(level, mob, x, y, z, 192) != null)
            return feet;
        return null;
    }

    final boolean approachStand(Vec3D stand) {
        return approachStand(stand, .8f);
    }

    private boolean approachStand(Vec3D stand, float speed) {
        Vec3D feet = new Vec3D(mob.x, mob.bb.y0, mob.z);
        if (workGoal == null || workGoal.distanceSquared(stand) > .1f
                || feet.distanceSquared(lastProgress) > .09f) {
            workGoal = stand;
            lastProgress = feet;
            progressTick = mob.tickCount;
        } else if (mob.tickCount - progressTick > 40) {
            rejectedStand = stand;
            retryAfter = mob.tickCount + 100;
            workGoal = null;
            clearNavigation();
            return false;
        }
        float dx = stand.x - mob.x, dz = stand.z - mob.z;
        if (mob instanceof Builder builder && BuilderPlacement.onScaffold(builder)
                && dx * dx + dz * dz < .4f * .4f && BuilderPlacement.canClimbTo(builder, stand)) {
            holdPosition(mob.yRot);
            mob.xd = Math.max(-.10f, Math.min(.10f, dx));
            mob.zd = Math.max(-.10f, Math.min(.10f, dz));
            mob.yd = dx * dx + dz * dz < .1f * .1f ? Math.max(-.12f, Math.min(.12f, stand.y - mob.bb.y0)) : 0;
            return true;
        }
        if (dx * dx + dz * dz < .55f * .55f
                && Pathfinder.canWalkStraight(level, mob, stand.x, stand.y, stand.z)) {
            holdPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mob.xd = mob.zd = 0;
            yya = .55f;
        } else {
            navigateTo(stand.x, stand.y, stand.z, speed);
        }
        return true;
    }
}
