package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.structure.GeneratedHouse;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.util.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;

public class ZombieIgniter extends Zombie {
    private static final long serialVersionUID = 2754867595941002277L;
    public static final short REGISTRY_ID = 12;
    private int ignitionCooldown;
    private transient int houseSearchCooldown;
    private transient Vec3D ignitionLook;
    private transient int ignitionLookTicks;

    public ZombieIgniter(Level level, float x, float y, float z) {
        super(level, x, y, z);
        net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();
        equipment[4] = Item.FLINT_AND_STEEL.id + 256;
        ai = new HouseIgnitionAI();
    }

    public boolean isPreparingHouseIgnition() {
        return !removed
                && health > 0
                && !corruptedToDefendPlayer
                && ignitionCooldown == 0
                && ai instanceof HouseIgnitionAI houseAI
                && houseAI.approach != null
                && !houseAI.isUsingEmergencyMovement();
    }

    @Override
    public void tick() {
        if (ai != null && ai.getClass() == BasicAttackAI.class) {
            BasicAttackAI previous = (BasicAttackAI) ai;
            HouseIgnitionAI replacement = new HouseIgnitionAI();
            replacement.attackTarget = previous.attackTarget;
            replacement.retaliationTicks = previous.retaliationTicks;
            replacement.attackDelay = previous.attackDelay;
            ai = replacement;
        }
        super.tick();
        if (ignitionLookTicks > 0 && --ignitionLookTicks == 0) ignitionLook = null;
        if (ignitionCooldown > 0) ignitionCooldown--;
        if (houseSearchCooldown > 0) houseSearchCooldown--;
        if (health <= 0
                || removed
                || corruptedToDefendPlayer
                || level.isNetworkMode()
                || ai instanceof HouseIgnitionAI houseAI && houseAI.isUsingEmergencyMovement()
                || ignitionCooldown > 0
                || isInWaterOrOnWater()) {
            turnTowardIgnition();
            return;
        }
        if (igniteTarget() || igniteNearbyHouse()) {
            ignitionCooldown = 60;
            attackTime = 10;
            level.playSound("random/click", this, 1f, 1f);
        }
        turnTowardIgnition();
    }

    private boolean igniteTarget() {
        if (!(ai instanceof BasicAttackAI combat)
                || !(combat.attackTarget instanceof Mob target)
                || target.health <= 0
                || target.removed
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || distanceToSqr(target) > 6.25f
                || !combat.hasLineOfSight(this, target)) return false;
        if (!target.ignite(100)) return false;
        lookAtIgnition(new Vec3D(target.x, target.bb.y0 + target.bbHeight * .5f, target.z));
        return true;
    }

    private boolean igniteNearbyHouse() {
        if (houseSearchCooldown > 0
                || CombatRules.difficulty(level) != Difficulty.EXTREME
                || ai instanceof HouseIgnitionAI houseAI
                        && (houseAI.houseTarget != null || houseAI.outsidePlayer != null)
                || overlapsFire(bb.grow(.5f, 0, .5f))) return false;
        houseSearchCooldown = 10;

        var occupied =
                level.generatedHouses.stream()
                        .filter(
                                house ->
                                        Math.abs(house.x() + .5f - x) <= 6
                                                && Math.abs(house.z() + .5f - z) <= 6
                                                && Math.abs(house.floor() + 1 - bb.y0) <= 6
                                                && hasOccupant(house))
                        .toList();
        if (occupied.isEmpty()) return false;
        float eyeY = bb.y0 + bbHeight * .85f;
        int bx = MathHelper.floor(x), by = MathHelper.floor(eyeY), bz = MathHelper.floor(z);
        MovingObjectPosition closest = null;
        float closestDistance = 6.25f;
        for (int ty = by - 2; ty <= by + 2; ty++) {
            for (int tz = bz - 2; tz <= bz + 2; tz++) {
                for (int tx = bx - 2; tx <= bx + 2; tx++) {
                    float dx = tx + .5f - x, dy = ty + .5f - eyeY, dz = tz + .5f - z;
                    float distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= closestDistance) continue;
                    int material = level.getTile(tx, ty, tz);
                    if (material != Block.WOOD.id && material != Block.GLASS.id) continue;
                    MovingObjectPosition hit =
                            level.clip(
                                    new Vec3D(x, eyeY, z), new Vec3D(tx + .5f, ty + .5f, tz + .5f));
                    if (hit == null
                            || hit.x != tx
                            || hit.y != ty
                            || hit.z != tz
                            || !occupiedWall(occupied, tx, ty, tz)) continue;
                    if (material == Block.WOOD.id && !canLightFace(firePosition(hit), bb)) continue;
                    closest = hit;
                    closestDistance = distance;
                }
            }
        }
        if (closest == null) return false;
        if (level.getTile(closest.x, closest.y, closest.z) == Block.GLASS.id) {
            if (!level.setTile(closest.x, closest.y, closest.z, Block.SAND.id)) return false;
            lookAtIgnition(closest.vec);
            return true;
        }

        Pos fire = firePosition(closest);
        if (!level.setTile(fire.x(), fire.y(), fire.z(), Block.FIRE.id)
                || level.getTile(fire.x(), fire.y(), fire.z()) != Block.FIRE.id) return false;
        lookAtIgnition(closest.vec);
        return true;
    }

    private static boolean occupiedWall(
            java.util.List<GeneratedHouse> houses, int x, int y, int z) {
        for (GeneratedHouse house : houses) {
            if (Math.abs(x - house.x()) <= 3
                    && Math.abs(z - house.z()) <= 3
                    && y > house.floor()
                    && y < house.floor() + 4) return true;
        }
        return false;
    }

    private static AABB houseInterior(GeneratedHouse house) {
        return new AABB(
                house.x() - 2,
                house.floor() + 1,
                house.z() - 2,
                house.x() + 3,
                house.floor() + 4,
                house.z() + 3);
    }

    private boolean hasOccupant(GeneratedHouse house) {
        AABB interior = houseInterior(house);
        for (Object entity : level.findEntities(this, interior)) {
            if (entity instanceof Mob target
                    && validOccupant(target)
                    && interior.contains(new Vec3D(target.x, target.bb.y0 + .1f, target.z)))
                return true;
        }
        Player player = level.getNearestPlayer(x, y, z, 35f);
        return validOccupant(player)
                && isOnRoof(house, player)
                && HostileTargets.hasLineOfSight(this, player);
    }

    private static boolean isOnRoof(GeneratedHouse house, Player player) {
        float roof = house.floor() + 5;
        return player.x >= house.x() - 3
                && player.x < house.x() + 4
                && player.z >= house.z() - 3
                && player.z < house.z() + 4
                && player.bb.y0 >= roof - .1f
                && player.bb.y0 <= roof + 2;
    }

    private boolean isAtHouse(Player player) {
        for (GeneratedHouse house : level.generatedHouses) {
            if (isOnRoof(house, player)
                    || houseInterior(house)
                            .contains(new Vec3D(player.x, player.bb.y0 + .1f, player.z)))
                return true;
        }
        return false;
    }

    private static boolean validOccupant(Mob target) {
        return (target instanceof Player || target instanceof Villager)
                && !target.removed
                && target.health > 0
                && !CreativeModeHelper.shouldIgnoreTarget(target);
    }

    private void lookAtIgnition(Vec3D point) {
        ignitionLook = point;
        ignitionLookTicks = 8;
    }

    private float ignitionYaw() {
        return (float) Math.toDegrees(Math.atan2(ignitionLook.z - z, ignitionLook.x - x)) - 90f;
    }

    private void turnTowardIgnition() {
        if (ignitionLook == null || health <= 0 || removed) return;
        float delta = (float) Math.IEEEremainder(ignitionYaw() - yBodyRot, 360);
        yBodyRot += delta - MathHelper.clamp(delta, -55f, 55f);
        yBodyRotO = yBodyRot - (float) Math.IEEEremainder(yBodyRot - yBodyRotO, 360);
    }

    @Override
    public float renderHeadYaw(float partial) {
        if (ignitionLook == null || health <= 0) return super.renderHeadYaw(partial);
        return (float) Math.IEEEremainder(ignitionYaw() - renderBodyYaw(partial), 360);
    }

    @Override
    public float renderHeadPitch(float partial) {
        if (ignitionLook == null || health <= 0) return super.renderHeadPitch(partial);
        double rise = ignitionLook.y - (bb.y0 + getEyeHeight(partial));
        double distance = Math.hypot(ignitionLook.x - x, ignitionLook.z - z);
        return (float) -Math.toDegrees(Math.atan2(rise, distance));
    }

    private static Pos firePosition(MovingObjectPosition hit) {
        return new Pos(hit.x, hit.y, hit.z).step(hit.face);
    }

    private boolean canLightFace(Pos fire, AABB bounds) {
        return level.isInBounds(fire.x(), fire.y(), fire.z())
                && !bounds.grow(.5f, 0, .5f)
                        .intersects(
                                fire.x(),
                                fire.y(),
                                fire.z(),
                                fire.x() + 1,
                                fire.y() + 1,
                                fire.z() + 1)
                && level.getTile(fire.x(), fire.y(), fire.z()) == 0
                && FireBlock.canStay(level, fire.x(), fire.y(), fire.z());
    }

    private static final class HouseIgnitionAI extends BasicAttackAI {
        private static final long serialVersionUID = 0L;
        private static final int SEARCH_RANGE = 24;
        private static final int PURSUIT_RECHECK_TICKS = 5;
        private static final int HOUSE_SEARCH_TICKS = 20;
        private transient Pos approach;
        private transient int searchTicks;
        private transient Mob houseTarget;
        private transient Player outsidePlayer;

        HouseIgnitionAI() {
            super(true, true, true);
        }

        @Override
        protected void update() {
            ZombieIgniter igniter = (ZombieIgniter) mob;
            outsidePlayer = null;
            if (CombatRules.difficulty(level) != Difficulty.EXTREME
                    || mob.corruptedToDefendPlayer
                    || level.isNetworkMode()
                    || igniter.isInWaterOrOnWater()
                    || hasRetaliationTarget() && attackTarget != houseTarget) {
                approach = null;
                houseTarget = null;
                searchTicks = 0;
                super.update();
                return;
            }
            Player player = level.getNearestPlayer(mob.x, mob.y, mob.z, getSightRange());
            if (validOccupant(player)
                    && HostileTargets.hasLineOfSight(mob, player)
                    && !igniter.isAtHouse(player)) {
                if (approach != null || houseTarget != null) clearNavigation();
                approach = null;
                houseTarget = null;
                searchTicks = 0;
                outsidePlayer = player;
                setPursuitTarget(player);
                super.update();
                return;
            }
            if (--searchTicks <= 0 || houseTarget != null && !validOccupant(houseTarget)) {
                Mob previousTarget = houseTarget;
                houseTarget = findReachableOccupant();
                if (previousTarget != null && houseTarget == null) {
                    if (attackTarget == previousTarget) {
                        attackTarget = null;
                        retaliationTicks = 0;
                    }
                    clearNavigation();
                    igniter.houseSearchCooldown = 0;
                }
                approach = houseTarget == null ? findApproach(igniter) : null;
                searchTicks = houseTarget == null ? HOUSE_SEARCH_TICKS : PURSUIT_RECHECK_TICKS;
            }
            if (validOccupant(houseTarget)) {
                attackTarget = houseTarget;
                super.update();
                attackTarget = houseTarget;
                return;
            }
            houseTarget = null;
            if (approach == null) {
                super.update();
                return;
            }
            attackTarget = null;
            running = true;
            runMultiplier = 1.05f;
            navigateTo(approach.x() + .5f, approach.y(), approach.z() + .5f, runSpeed);
        }

        private Mob findReachableOccupant() {
            for (GeneratedHouse house : level.generatedHouses) {
                if (Math.abs(mob.x - house.x() - .5f) > SEARCH_RANGE
                        || Math.abs(mob.z - house.z() - .5f) > SEARCH_RANGE
                        || Math.abs(mob.bb.y0 - house.floor() - 1) > 8) continue;
                AABB interior = houseInterior(house);
                for (Object entity : level.findEntities(mob, interior).toArray()) {
                    if (!(entity instanceof Mob target)
                            || !validOccupant(target)
                            || !interior.contains(
                                    new Vec3D(target.x, target.bb.y0 + .1f, target.z))) continue;
                    var path =
                            Pathfinder.findPathWithRemovableFire(
                                    level,
                                    mob,
                                    target.x,
                                    target.bb.y0,
                                    target.z,
                                    768,
                                    fire -> canDouse(house, fire));
                    if (path == null || path.isEmpty()) continue;
                    var end = path.get(path.size() - 1);
                    float offset = Pathfinder.offset(mob.movementClearanceWidth());
                    if (!interior.contains(
                                    new Vec3D(end.x + offset, end.feetY + .1f, end.z + offset))
                            || Math.abs(end.x + offset - target.x) > 1f
                            || Math.abs(end.z + offset - target.z) > 1f
                            || Math.abs(end.feetY - target.bb.y0) > 1f) continue;
                    douseHouse(house);
                    return target;
                }
            }
            return null;
        }

        private boolean canDouse(GeneratedHouse house, Pos fire) {
            return Math.abs(fire.x() - house.x()) <= 4
                    && Math.abs(fire.z() - house.z()) <= 4
                    && fire.y() > house.floor()
                    && fire.y() <= house.floor() + 5
                    && mob.distanceTo(fire.x() + .5f, fire.y() + .5f, fire.z() + .5f) <= 3f;
        }

        private void douseHouse(GeneratedHouse house) {
            boolean doused = false;
            for (int x = house.x() - 4; x <= house.x() + 4; x++) {
                for (int z = house.z() - 4; z <= house.z() + 4; z++) {
                    for (int y = house.floor() + 1; y <= house.floor() + 5; y++) {
                        if (!canDouse(house, new Pos(x, y, z))
                                || level.getTile(x, y, z) != Block.FIRE.id) continue;
                        FireBlock.extinguish(level, x, y, z);
                        doused = true;
                    }
                }
            }
            if (doused) FireBlock.playExtinguishSound(level, mob.x, mob.y, mob.z);
        }

        private Pos findApproach(ZombieIgniter igniter) {
            var candidates = new ArrayList<Pos>();
            for (GeneratedHouse house : level.generatedHouses) {
                if (Math.abs(house.x() + .5f - mob.x) > SEARCH_RANGE
                        || Math.abs(house.z() + .5f - mob.z) > SEARCH_RANGE
                        || Math.abs(house.floor() + 1 - mob.bb.y0) > 8
                        || !igniter.hasOccupant(house)) continue;
                for (int offset = -3; offset <= 3; offset++) {
                    addApproaches(
                            igniter,
                            candidates,
                            house.x() - 3,
                            house.z() + offset,
                            house.floor(),
                            -1,
                            0);
                    addApproaches(
                            igniter,
                            candidates,
                            house.x() + 3,
                            house.z() + offset,
                            house.floor(),
                            1,
                            0);
                    addApproaches(
                            igniter,
                            candidates,
                            house.x() + offset,
                            house.z() - 3,
                            house.floor(),
                            0,
                            -1);
                    addApproaches(
                            igniter,
                            candidates,
                            house.x() + offset,
                            house.z() + 3,
                            house.floor(),
                            0,
                            1);
                }
            }
            candidates.sort(
                    Comparator.comparingDouble(
                            pos ->
                                    mob.distanceTo(
                                            pos.x() + .5f,
                                            pos.y() + mob.heightOffset,
                                            pos.z() + .5f)));
            for (int i = 0; i < Math.min(12, candidates.size()); i++) {
                Pos pos = candidates.get(i);
                var route = Pathfinder.findPath(level, mob, pos.x(), pos.y(), pos.z(), 512);
                if (route != null && !route.isEmpty()) {
                    var end = route.get(route.size() - 1);
                    if (end.x == pos.x() && end.feetY == pos.y() && end.z == pos.z()) return pos;
                }
            }
            return null;
        }

        private void addApproaches(
                ZombieIgniter igniter,
                ArrayList<Pos> candidates,
                int wallX,
                int wallZ,
                int floor,
                int dx,
                int dz) {
            int x = wallX + 2 * dx, z = wallZ + 2 * dz;
            for (int y = floor; y <= floor + 2; y++) {
                if (!Pathfinder.hasStandingRoom(level, mob, x + .5f, y, z + .5f)) continue;
                AABB bounds = mob.bb.cloneMove(x + .5f - mob.x, y - mob.bb.y0, z + .5f - mob.z);
                if (mob.overlapsFire(bounds.grow(1f, 0, 1f))) continue;
                float eyeY = y + mob.bbHeight * .85f;
                for (int wallY = floor + 1; wallY <= floor + 3; wallY++) {
                    if (level.getTile(wallX, wallY, wallZ) != Block.WOOD.id) continue;
                    float rise = wallY + .5f - eyeY;
                    if (4 + rise * rise >= 6.25f) continue;
                    var hit =
                            level.clip(
                                    new Vec3D(x + .5f, eyeY, z + .5f),
                                    new Vec3D(wallX + .5f, wallY + .5f, wallZ + .5f));
                    if (hit != null
                            && hit.x == wallX
                            && hit.y == wallY
                            && hit.z == wallZ
                            && igniter.canLightFace(firePosition(hit), bounds)) {
                        candidates.add(new Pos(x, y, z));
                        break;
                    }
                }
            }
        }
    }
}
