package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.ThrownEnderPearl;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.BowItem;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.MobBattle;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

import java.util.ArrayList;

public final class DefenderAI extends BasicAttackAI {
    private static final float DEFENDER_SIGHT_RANGE = 30;
    private static final float CONVERTED_MOB_SIGHT_RANGE = 16;
    private static final int BOW_SHOT_INTERVAL = 25;
    private static final float BOW_RANGE = 14;
    private static final float GRAVITY_RANGE = 8;
    private static final float ARROW_SPEED = 1.8f;
    private static final int FOLLOW_SPRINT_DISTANCE_SQUARED = 64;
    private static final int OWNER_LEASH_DISTANCE_SQUARED = 196;
    private static final int PEARL_FOLLOW_DISTANCE_SQUARED = 324;
    private static final int FIRE_HELP_RANGE = 10;
    private static final int ENEMY_SEARCH_TICKS = 60;
    private static final int ENEMY_LOOK_TICKS = 10;
    private static final int FOLLOW_STALL_TICKS = 60;
    private transient Entity stalledTarget;
    private transient int retryTicks;
    private transient int progressTicks;
    private transient float progressX, progressY, progressZ;
    private transient Entity bowTarget;
    private transient Entity retaliationTarget;
    private transient Entity lastSeenEnemy;
    private transient Vec3D lastSeenEnemyPosition;
    private transient int enemySearchUntil, enemyLookUntil;
    private transient int bowDrawTicks, lastBowAimTick;
    private transient int nextDodgeTick, dodgeUntil;
    private transient int nextPearlTick, retreatUntil;
    private transient Mob pearlAttackTarget;
    private transient int pearlAttackUntil;
    private transient int nextNearbyRouteCheck;
    private transient Player followedOwner;
    private transient int lastFollowTick, followProgressTick;
    private transient Vec3D followProgressPosition;
    private transient int damageWindowEnd;
    private transient long recentDamage;
    private transient Mob damageThreat;

    public static boolean canConvert(Mob mob) {
        return !mob.corruptedToDefendPlayer
                && (mob instanceof Spider
                        || mob instanceof Enderman
                        || mob instanceof Zombie
                                && !(mob instanceof BossZombieBase)
                                && !(mob instanceof ScaryMindZombie)
                                && !(mob instanceof ZombieBuilder)
                                && !(mob instanceof HouseVandalizer));
    }

    public static void convert(Mob mob) {
        if (canConvert(mob)) restore(mob);
    }

    static void restore(Mob mob) {
        mob.corruptedToDefendPlayer = true;
        mob.persistent = true;
        mob.ai = new DefenderAI();
    }

    int bowDrawTicks() {
        return bowTarget != null && lastBowAimTick == mob.tickCount ? bowDrawTicks : -1;
    }

    @Override
    public float getSightRange() {
        return mob == null || mob instanceof Defender
                ? DEFENDER_SIGHT_RANGE
                : CONVERTED_MOB_SIGHT_RANGE;
    }

    @Override
    public void tick(Level level, Mob mob) {
        if (mob instanceof Defender defender && attackTarget != null) {
            if (!hasLineOfSight(mob, attackTarget)) {
                if (attackTarget == lastSeenEnemy && enemySearchUntil == 0) {
                    enemySearchUntil = mob.tickCount + ENEMY_SEARCH_TICKS;
                }
                attackTarget = null;
                bowTarget = null;
                progressTicks = 0;
                if (this.level == level) clearNavigation();
            } else if (defender.canAttack(attackTarget)) {
                rememberEnemy();
            }
        }
        super.tick(level, mob);
    }

    @Override
    protected void doAttack() {
        Defender defender = mob instanceof Defender companion ? companion : null;
        int experience = 0;
        if (defender != null) {
            damage = 0;
            running = false;
            experience = defender.skillLevel(Defender.Skill.COMBAT_EXPERIENCE);
            if (defender.isUsingBucket()
                    || defender.isThrowingPearl()
                    || mob.tickCount < dodgeUntil) {
                defender.faceBucketTarget();
                if (defender.isThrowingPearl()) defender.holdPearlAim();
                holdPosition(mob.yRot);
                return;
            }
            if (experience >= 2 && dodgeIncomingArrow(defender)) return;
        }
        if (retryTicks > 0 && --retryTicks == 0) stalledTarget = null;
        Player owner =
                defender != null
                        ? defender.ownerPlayer()
                        : level.player instanceof Player p ? p : null;
        Player followPlayer = followTarget(owner);
        boolean resumingPearlAttack =
                mob.tickCount < pearlAttackUntil
                        && validEnemy(pearlAttackTarget)
                        && hasLineOfSight(mob, pearlAttackTarget);
        if (!resumingPearlAttack) pearlAttackTarget = null;
        if (defender != null) {
            if (mob.tickCount >= retreatUntil && helpBurningAlly(defender, owner)) return;
            if (usePearl(defender, followPlayer, false)
                    || defender.isEating()
                    || mob.tickCount < retreatUntil) {
                holdPosition(mob.yRot);
                return;
            }
            if (followPlayer != null
                    && !resumingPearlAttack
                    && mob.distanceToSqr(followPlayer) > OWNER_LEASH_DISTANCE_SQUARED) {
                attackTarget = null;
                forgetEnemy();
                followOwner(followPlayer);
                return;
            }
        }
        if (resumingPearlAttack) {
            attackTarget = pearlAttackTarget;
        } else if (defender != null
                && owner != null
                && (attackTarget == null || MobBattle.target(defender) != attackTarget)) {
            Mob ownerTarget = owner.recentMobAttacker();
            if (!canAssistOwner(defender, ownerTarget)) {
                ownerTarget = owner.recentMobAttack();
            }
            if (ownerTarget != attackTarget && canAssistOwner(defender, ownerTarget)) {
                attackTarget = ownerTarget;
                progressTicks = 0;
            }
        }
        if (defender != null
                && attackTarget == null
                && mob.tickCount < enemySearchUntil
                && validEnemy(lastSeenEnemy)
                && hasLineOfSight(mob, lastSeenEnemy)) {
            attackTarget = lastSeenEnemy;
        }
        float sightRange = getSightRange();
        float sightRangeSquared = sightRange * sightRange;
        if (!validEnemy(attackTarget) || mob.distanceToSqr(attackTarget) > sightRangeSquared) {
            attackTarget = null;
            progressTicks = 0;
            float nearest = sightRangeSquared;
            float verticalRange = defender != null ? sightRange : 8;
            for (Object candidate :
                    new ArrayList<>(
                            level.findEntities(
                                    mob, mob.bb.grow(sightRange, verticalRange, sightRange)))) {
                if (candidate instanceof Mob enemy
                        && enemy != stalledTarget
                        && (defender == null ? validEnemy(enemy) : Defender.isEnemy(enemy))) {
                    float distance = mob.distanceToSqr(enemy);
                    if ((distance < nearest || attackTarget == null && distance == nearest)
                            && (defender == null || hasLineOfSight(defender, enemy))) {
                        nearest = distance;
                        attackTarget = enemy;
                    }
                }
            }
        }
        if (attackTarget != null) {
            if (defender != null && (attackTarget != lastSeenEnemy || enemySearchUntil > 0)) {
                rememberEnemy();
            }
            float dx = attackTarget.x - mob.x, dz = attackTarget.z - mob.z;
            int ammo =
                    defender != null && defender.hasBow()
                            ? defender.packedItemSlot(Equipment.ARROW)
                            : -1;
            float distance = mob.distanceToSqr(attackTarget);
            boolean rangedCombat =
                    defender != null
                            && !level.isNetworkMode()
                            && CombatRules.difficulty(level) != Difficulty.PEACEFUL;
            boolean canShoot =
                    rangedCombat
                            && ammo >= 0
                            && !(attackTarget instanceof Enderman)
                            && distance <= BOW_RANGE * BOW_RANGE;
            boolean canPulse =
                    rangedCombat
                            && defender.hasGravityGun()
                            && distance <= GRAVITY_RANGE * GRAVITY_RANGE;
            boolean meleeSprint = experience >= 1 && !canShoot && !canPulse;
            if ((canShoot || canPulse || withinMeleeReach(attackTarget, 2f))
                    && hasLineOfSight(mob, attackTarget)) {
                if (meleeSprint && dx * dx + dz * dz > 1.4f * 1.4f) {
                    running = true;
                    navigateTo(attackTarget.x, attackTarget.bb.y0, attackTarget.z, 1f);
                } else {
                    holdPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
                }
                progressTicks = 0;
                if (attackDelay == 0) {
                    if (canShoot) {
                        drawBow(defender, ammo, dx, dz);
                    } else if (canPulse) {
                        GravityGunItem.defenderPulse(defender, (Mob) attackTarget);
                        mob.attackTime = Mob.ATTACK_DURATION;
                        attackDelay = 32;
                        noActionTime = 0;
                    } else if (attack(attackTarget) && experience >= 3) {
                        attackDelay = 5;
                    }
                }
            } else {
                float moved =
                        Math.abs(mob.x - progressX)
                                + Math.abs(mob.y - progressY)
                                + Math.abs(mob.z - progressZ);
                if (progressTicks == 0 || moved > .6f) {
                    progressX = mob.x;
                    progressY = mob.y;
                    progressZ = mob.z;
                    progressTicks = 1;
                } else {
                    progressTicks++;
                }
                if (defender != null) running = meleeSprint;
                navigateTo(attackTarget.x, attackTarget.bb.y0, attackTarget.z, 1f);
                if (defender != null
                        && attackTarget instanceof Mob enemy
                        && (isWaitingForNavigationRoute() || progressTicks >= 60)
                        && pearlToEnemy(defender, enemy)) {
                    holdPosition(mob.yRot);
                    return;
                }
                if (progressTicks >= 60) {
                    stalledTarget = attackTarget;
                    attackTarget = null;
                    retryTicks = 40;
                    progressTicks = 0;
                    return;
                }
            }
        } else if (defender != null && checkLastSeenPosition()) {
            return;
        } else if (followPlayer != null
                && followPlayer.health > 0
                && (defender != null || mob.distanceToSqr(followPlayer) > 9)) {
            followOwner(followPlayer);
        } else if (mob instanceof Defender) {
            holdPosition(mob.yRot);
        }
    }

    private void rememberEnemy() {
        lastSeenEnemy = attackTarget;
        lastSeenEnemyPosition = new Vec3D(attackTarget.x, attackTarget.bb.y0, attackTarget.z);
        enemySearchUntil = enemyLookUntil = 0;
    }

    private void forgetEnemy() {
        lastSeenEnemy = null;
        lastSeenEnemyPosition = null;
        enemySearchUntil = enemyLookUntil = 0;
    }

    private boolean checkLastSeenPosition() {
        if (enemySearchUntil == 0) return false;
        Vec3D spot = lastSeenEnemyPosition;
        if (mob.tickCount < enemySearchUntil && lastSeenEnemy.level == level) {
            if (Math.hypot(mob.x - spot.x, mob.z - spot.z) < .85
                    && Math.abs(mob.bb.y0 - spot.y) < .75f) {
                if (enemyLookUntil == 0) enemyLookUntil = mob.tickCount + ENEMY_LOOK_TICKS;
                if (mob.tickCount < enemyLookUntil) {
                    holdPosition(mob.yRot);
                    return true;
                }
            } else if (enemyLookUntil == 0) {
                navigateTo(spot.x, spot.y, spot.z, .9f);
                if (!isWaitingForNavigationRoute()) return true;
            }
        }
        forgetEnemy();
        clearNavigation();
        return false;
    }

    private boolean helpBurningAlly(Defender defender, Player owner) {
        if (level.isNetworkMode()
                || defender.remainingFireTicks > 0
                || defender.packedItemSlot(Item.BUCKET_WATER) < 0) return false;
        float range = FIRE_HELP_RANGE * FIRE_HELP_RANGE;
        if (defender.needsExtinguishing(owner)
                && defender.distanceToSqr(owner) <= range
                && helpWithBucket(defender, owner)) return true;

        Defender nearest = null;
        for (Entity entity :
                level.blockMap.getEntities(
                        defender,
                        defender.bb.grow(FIRE_HELP_RANGE, FIRE_HELP_RANGE, FIRE_HELP_RANGE),
                        new ArrayList<>())) {
            if (!(entity instanceof Defender ally) || !defender.needsExtinguishing(ally)) continue;
            float distance = defender.distanceToSqr(ally);
            if (distance < range) {
                range = distance;
                nearest = ally;
            }
        }
        return nearest != null && helpWithBucket(defender, nearest);
    }

    private boolean helpWithBucket(Defender defender, Mob target) {
        if (defender.beginFireBucket(target)) {
            holdPosition(mob.yRot);
            return true;
        }
        return navigateTo(target.x, target.bb.y0, target.z, .9f) || hasActiveRoute();
    }

    private void followOwner(Player owner) {
        Defender defender = mob instanceof Defender companion ? companion : null;
        float distance = mob.distanceToSqr(owner);
        if (distance <= 9 && defender != null) {
            holdPosition(mob.yRot);
            if (canThrowPearl(defender) && mob.tickCount >= nextNearbyRouteCheck) {
                nextNearbyRouteCheck = mob.tickCount + 15;
                if (!Pathfinder.canWalkStraight(level, mob, owner.x, owner.bb.y0, owner.z)
                        && Pathfinder.findChasePath(level, mob, owner.x, owner.bb.y0, owner.z, 1024)
                                == null) {
                    usePearl(defender, owner, true);
                }
            }
            return;
        }
        boolean inWater = mob.isInWater();
        if (defender != null) {
            running = !inWater && distance > FOLLOW_SPRINT_DISTANCE_SQUARED;
        }
        boolean moving = navigateTo(owner.x, owner.bb.y0, owner.z, running ? 1f : .8f);
        if (defender != null
                && (!inWater && followStalled(owner) || !moving && isWaitingForNavigationRoute())
                && usePearl(defender, owner, true)) {
            followedOwner = null;
            holdPosition(mob.yRot);
            return;
        }
    }

    private Player followTarget(Player owner) {
        // Ground navigation cannot reach a hovering player.
        return level.doDefendersFollowPlayer
                        && owner != null
                        && !owner.isSpectator()
                        && !owner.isFlying
                ? owner
                : null;
    }

    @Override
    protected Entity waterPursuitTarget() {
        Entity target = super.waterPursuitTarget();
        if (target != null) return target;
        Player owner =
                mob instanceof Defender defender
                        ? defender.ownerPlayer()
                        : level.player instanceof Player player ? player : null;
        owner = followTarget(owner);
        return owner != null && owner.health > 0 ? owner : null;
    }

    private boolean followStalled(Player owner) {
        int tick = mob.tickCount;
        Vec3D position = followProgressPosition;
        if (followedOwner != owner
                || tick - lastFollowTick != 1
                || position == null
                || mob.distanceTo(position.x, position.y, position.z) >= .75f) {
            followedOwner = owner;
            followProgressPosition = new Vec3D(mob.x, mob.y, mob.z);
            followProgressTick = tick;
        }
        lastFollowTick = tick;
        return tick - followProgressTick >= FOLLOW_STALL_TICKS;
    }

    private boolean canDodge(Defender defender) {
        return defender.health > 0
                && defender.level != null
                && defender.onGround
                && defender.riding == null
                && !defender.level.isNetworkMode()
                && !defender.isUsingBucket()
                && !defender.isThrowingPearl()
                && defender.skillLevel(Defender.Skill.COMBAT_EXPERIENCE) >= 2
                && defender.tickCount >= nextDodgeTick;
    }

    private boolean dodgeIncomingArrow(Defender defender) {
        if (!canDodge(defender)) return false;
        AABB hitbox = defender.bb.grow(Arrow.HALF_WIDTH, Arrow.HALF_HEIGHT, Arrow.HALF_WIDTH);
        // The clearance check for a sidestep performs nested entity queries.
        for (Entity entity :
                level.blockMap.getEntities(
                        defender, defender.bb.grow(5, 2.5f, 5), new ArrayList<>())) {
            if (!(entity instanceof Arrow arrow)
                    || arrow.isEmbedded()
                    || arrow.isBullet()
                    || !defender.canRespondTo(arrow.getOwner())) continue;
            Vec3D start = new Vec3D(arrow.x, arrow.y, arrow.z);
            float speed = (float) Math.hypot(arrow.xd, arrow.zd);
            if (speed < .01f) continue;
            float ticks = Math.min(5 / speed, 5);
            Vec3D end = start.add(arrow.xd * ticks, arrow.yd * ticks, arrow.zd * ticks);
            var hit = hitbox.clip(start, end);
            if (hit != null
                    && level.clip(start, hit.vec, true) == null
                    && sidestep(defender, arrow.xd, arrow.zd)) {
                holdPosition(defender.yRot);
                return true;
            }
        }
        return false;
    }

    boolean dodgeHit(Defender defender, Entity source, Entity responsible) {
        if (!canDodge(defender) || !defender.canRespondTo(responsible)) return false;
        if (source instanceof Arrow arrow) {
            return !arrow.isBullet() && sidestep(defender, arrow.xd, arrow.zd);
        }
        return source == responsible
                && defender.distanceToSqr(responsible) < 9
                && sidestep(defender, responsible.x - defender.x, responsible.z - defender.z);
    }

    private boolean sidestep(Defender defender, float dx, float dz) {
        float length = (float) Math.hypot(dx, dz);
        if (length < .01f) return false;
        float sideX = dz / length, sideZ = -dx / length;
        for (int side : new int[] {1, -1}) {
            float stepX = sideX * side, stepZ = sideZ * side;
            if (!Pathfinder.canWalkStraight(
                    defender.level,
                    defender,
                    defender.x + stepX,
                    defender.bb.y0,
                    defender.z + stepZ)) continue;
            defender.move(stepX, 0, stepZ);
            defender.onGround = true;
            defender.xd = stepX * .14f;
            defender.zd = stepZ * .14f;
            nextDodgeTick = defender.tickCount + 24;
            dodgeUntil = defender.tickCount + 4;
            return true;
        }
        return false;
    }

    void recordDamage(Defender defender, Mob threat, int damage) {
        if (!defender.canRespondTo(threat)) return;
        if (defender.tickCount > damageWindowEnd) recentDamage = 0;
        recentDamage += damage;
        damageWindowEnd = defender.tickCount + 60;
        damageThreat = threat;
    }

    private boolean canThrowPearl(Defender defender) {
        return defender.skillLevel(Defender.Skill.ENDER_PEARL_USE) > 0
                && mob.tickCount >= nextPearlTick
                && mob.onGround
                && mob.riding == null
                && !level.isNetworkMode()
                && defender.packedItemSlot(Item.ENDER_PEARL) >= 0;
    }

    private boolean usePearl(Defender defender, Player owner, boolean routeBlocked) {
        if (!canThrowPearl(defender)) return false;

        boolean escape =
                damageThreat != null
                        && damageThreat.level == level
                        && !damageThreat.removed
                        && damageThreat.health > 0
                        && mob.tickCount <= damageWindowEnd
                        && mob.distanceToSqr(damageThreat) < 144
                        && (defender.health * 2L <= defender.maxHealth()
                                || recentDamage * 3 >= defender.maxHealth());
        boolean catchUp =
                owner != null
                        && !owner.isSpectator()
                        && !defender.isEating()
                        && (routeBlocked
                                || mob.distanceToSqr(owner) > PEARL_FOLLOW_DISTANCE_SQUARED);
        if (!escape && !catchUp) return false;
        nextPearlTick = mob.tickCount + 15;

        if (!(escape
                ? throwEscapePearl(defender)
                : throwToward(defender, owner, routeBlocked, false))) return false;
        nextPearlTick = mob.tickCount + 70;
        if (escape) {
            pearlAttackTarget = null;
            attackTarget = null;
            progressTicks = 0;
            recentDamage = 0;
            damageThreat = null;
            retreatUntil = mob.tickCount + 50;
        }
        return true;
    }

    private boolean pearlToEnemy(Defender defender, Mob enemy) {
        if (!canThrowPearl(defender) || CombatRules.difficulty(level) == Difficulty.PEACEFUL)
            return false;
        nextPearlTick = mob.tickCount + 15;
        if (!throwToward(defender, enemy, true, true)) return false;
        nextPearlTick = mob.tickCount + 70;
        pearlAttackTarget = enemy;
        pearlAttackUntil = nextPearlTick;
        progressTicks = 0;
        return true;
    }

    private boolean throwEscapePearl(Defender defender) {
        double away = Math.atan2(mob.z - damageThreat.z, mob.x - damageThreat.x);
        for (float turn : new float[] {0, .65f, -.65f, 1.3f, -1.3f}) {
            float x = mob.x + (float) Math.cos(away + turn) * 9;
            float z = mob.z + (float) Math.sin(away + turn) * 9;
            if (throwPearlNear(defender, x, mob.bb.y0, z, true, null)) return true;
        }
        return false;
    }

    private boolean throwToward(
            Defender defender, Mob target, boolean routeBlocked, boolean attack) {
        if (mob.distanceToSqr(target) > 48 * 48) return false;
        double toward = Math.atan2(mob.z - target.z, mob.x - target.x);
        float[] radii =
                attack
                        ? new float[] {1.25f, 2.5f}
                        : routeBlocked ? new float[] {2.5f, 1.25f} : new float[] {2.5f};
        for (float radius : radii) {
            for (float turn : new float[] {0, 1.57f, -1.57f, 3.14f}) {
                float x = target.x + (float) Math.cos(toward + turn) * radius;
                float z = target.z + (float) Math.sin(toward + turn) * radius;
                if (throwPearlNear(
                        defender, x, target.bb.y0, z, false, routeBlocked ? target : null))
                    return true;
            }
        }
        return false;
    }

    private boolean throwPearlNear(
            Defender defender, float x, float y, float z, boolean retreat, Mob reconnectTo) {
        float feet = Math.round(y * 2) / 2f;
        for (float height : new float[] {0, -.5f, .5f, -1, 1, -2}) {
            float landingY = feet + height;
            Vec3D landing = new Vec3D(x, landingY, z);
            if (Pathfinder.canStandAt(level, defender, x, landingY, z)
                    && (!retreat || !hasThreatNear(defender, x, landingY, z))
                    && (reconnectTo == null
                            || Pathfinder.canReachFrom(level, defender, landing, reconnectTo))
                    && ThrownEnderPearl.prepareForDefender(defender, landing)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasThreatNear(Defender defender, float x, float y, float z) {
        AABB area = new AABB(x - 3, y - 2, z - 3, x + 3, y + 3, z + 3);
        for (Object candidate : level.findEntities(defender, area)) {
            if (candidate instanceof Mob threat
                    && (threat == damageThreat || Defender.isEnemy(threat))) return true;
        }
        return false;
    }

    private void drawBow(Defender defender, int ammo, float dx, float dz) {
        float startY = mob.bb.y0 + mob.bbHeight * .8f;
        float distance = (float) Math.hypot(dx, dz);
        float flightTicks = distance / ARROW_SPEED;
        float rise = (attackTarget.bb.y0 + attackTarget.bb.y1) * .5f - startY;
        mob.xRot =
                (float)
                        -Math.toDegrees(
                                Math.atan2(
                                        rise + .01f * flightTicks * (flightTicks + 1), distance));
        int ticksSinceAim = mob.tickCount - lastBowAimTick;
        lastBowAimTick = mob.tickCount;
        if (bowTarget != attackTarget || ticksSinceAim > 1) {
            bowTarget = attackTarget;
            bowDrawTicks = 0;
            return;
        }
        if (bowDrawTicks < BowItem.DRAW_TICKS) bowDrawTicks++;
        if (bowDrawTicks < BowItem.DRAW_TICKS || defenderInBowLane(startY)) return;
        shootBow(defender, ammo, startY);
        bowTarget = null;
        attackDelay = BOW_SHOT_INTERVAL - BowItem.DRAW_TICKS;
        noActionTime = 0;
    }

    private boolean defenderInBowLane(float startY) {
        Vec3D start = new Vec3D(mob.x, startY, mob.z);
        Vec3D end =
                new Vec3D(
                        attackTarget.x,
                        (attackTarget.bb.y0 + attackTarget.bb.y1) * .5f,
                        attackTarget.z);
        AABB lane =
                new AABB(start.x, start.y, start.z, start.x, start.y, start.z)
                        .expand(end.x - start.x, end.y - start.y, end.z - start.z)
                        .grow(Arrow.HALF_WIDTH, Arrow.HALF_HEIGHT, Arrow.HALF_WIDTH);
        for (Object candidate : level.findEntities(mob, lane)) {
            if (!(candidate instanceof Defender ally) || ally.health <= 0 || ally.removed) continue;
            AABB bounds = ally.bb.grow(Arrow.HALF_WIDTH, Arrow.HALF_HEIGHT, Arrow.HALF_WIDTH);
            if (bounds.contains(start) || bounds.clip(start, end) != null) return true;
        }
        return false;
    }

    private void shootBow(Defender defender, int ammo, float startY) {
        Arrow arrow =
                new Arrow(
                        level,
                        defender,
                        mob.x,
                        startY,
                        mob.z,
                        mob.yRot + 180f,
                        mob.xRot,
                        ARROW_SPEED);
        arrow.setPos(mob.x, startY, mob.z);
        arrow.xo = mob.x;
        arrow.yo = startY;
        arrow.zo = mob.z;
        level.addEntity(arrow);
        defender.pack()
                .setIdCount(ammo, Equipment.ARROW.id + 256, defender.pack().getCount(ammo) - 1);
        level.playSound("random/bow", mob, 1f, 1f);
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (source instanceof net.classicremastered.minecraft.entity.Arrow arrow) {
            source = arrow.getOwner();
        }
        boolean allowed =
                mob instanceof Defender defender
                        ? defender.canRespondTo(source) && hasLineOfSight(defender, source)
                        : validEnemy(source);
        if (allowed
                && (!validEnemy(attackTarget)
                        || mob instanceof Defender && !hasLineOfSight(mob, attackTarget))) {
            super.hurt(source, damage);
            retaliationTarget = source;
            attackTarget = source;
            progressTicks = 0;
        }
    }

    boolean isRetaliatingAgainst(Entity entity) {
        return retaliationTicks > 0 && retaliationTarget == entity;
    }

    @Override
    protected boolean navigationAllowed() {
        return super.navigationAllowed()
                && !(mob instanceof Defender defender
                        && (defender.isUsingBucket()
                                || defender.isThrowingPearl()
                                || mob.tickCount < dodgeUntil
                                || mob.tickCount < retreatUntil));
    }

    @Override
    protected boolean usesWanderNavigation() {
        return false;
    }

    @Override
    protected boolean handlesTargetMovement() {
        return mob instanceof Defender || super.handlesTargetMovement();
    }

    private boolean canAssistOwner(Defender defender, Mob target) {
        float sightRange = getSightRange();
        return target != stalledTarget
                && defender.canAttack(target)
                && defender.distanceToSqr(target) <= sightRange * sightRange
                && hasLineOfSight(defender, target);
    }

    private boolean validEnemy(Entity entity) {
        if (mob instanceof Defender defender) return defender.canAttack(entity);
        return entity instanceof Mob enemy
                && !enemy.removed
                && enemy.health > 0
                && !(enemy instanceof Player)
                && !(enemy instanceof CrossbowTurret)
                && !(enemy instanceof Defender)
                && !enemy.corruptedToDefendPlayer
                && MobRegistry.isTurretHostile(enemy);
    }
}
