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
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class DefenderAI extends BasicAttackAI {
    private static final float DEFENDER_SIGHT_RANGE = 30;
    private static final float CONVERTED_MOB_SIGHT_RANGE = 16;
    private static final int BOW_SHOT_INTERVAL = 25;
    private static final float BOW_RANGE = 14;
    private static final float WORKER_PROTECTION_RANGE = 16;
    private static final float ESCORT_RANGE = 14;
    private static final float ESCORT_LEASH = 22;
    private static final float GRAVITY_RANGE = 8;
    private static final float ARROW_SPEED = 1.8f;
    private static final int FOLLOW_SPRINT_DISTANCE_SQUARED = 64;
    private static final int OWNER_LEASH_DISTANCE_SQUARED = 196;
    private static final int PEARL_FOLLOW_DISTANCE_SQUARED = 324;
    private static final int FIRE_HELP_RANGE = 10;
    private static final int ENEMY_SEARCH_TICKS = 60;
    private static final int ENEMY_LOOK_TICKS = 10;
    private static final int FOLLOW_STALL_TICKS = 60;
    private static final float CLIFF_APPROACH_RANGE = 3.25f;
    private static final float CLIFF_STANCE_DISTANCE = 1.35f;
    private static final float CLIFF_KNOCKBACK_DISTANCE = 1.75f;
    private static final float CLIFF_MIN_DROP = 3.5f;
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
    private transient Mob cliffAttackTarget;
    private transient Builder.Site guardTower;
    private transient Vec3D guardPost, firingPost;
    private transient Vec3D workerFiringPost;
    private transient int nextTowerCheck, nextFiringPostCheck;
    private transient int nextWorkerCheck, nextWorkerPostCheck;
    private transient Set<Mob> workerThreats;
    private transient Worker escort;
    private transient int nextEscortCheck, nextDoorGuardCheck;
    private transient boolean escortWalking;
    private transient Builder.Site escortShelter;
    private transient Vec3D doorGuardPost;

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
            refreshWorkerThreats(defender);
            updateEscort(defender);
            experience = defender.skillLevel(Defender.Skill.COMBAT_EXPERIENCE);
            if (defender.isUsingBucket()
                    || defender.isThrowingPearl()
                    || mob.tickCount < dodgeUntil) {
                defender.faceBucketTarget();
                if (defender.isThrowingPearl()) defender.holdPearlAim();
                holdPosition(mob.yRot);
                return;
            }
            if (experience >= 2 && !atTowerRoof() && dodgeIncomingArrow(defender)) return;
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
        Mob workerTarget = null;
        if (defender != null) {
            if (!resumingPearlAttack && escort == null)
                workerTarget = nearestWorkerThreat(defender);
            boolean roofArcher =
                    atTowerRoof()
                            && defender.hasBow()
                            && defender.packedItemSlot(Equipment.ARROW) >= 0;
            boolean protectingWorker =
                    workerTarget != null
                            || roofArcher && workerThreats != null && !workerThreats.isEmpty();
            if (mob.tickCount >= retreatUntil && helpBurningAlly(defender, owner)) return;
            if (usePearl(defender, protectingWorker || escort != null ? null : followPlayer, false)
                    || defender.isEating()
                    || mob.tickCount < retreatUntil) {
                holdPosition(mob.yRot);
                return;
            }
            if (followPlayer != null
                    && !resumingPearlAttack
                    && !protectingWorker
                    && escort == null
                    && mob.distanceToSqr(followPlayer) > OWNER_LEASH_DISTANCE_SQUARED) {
                attackTarget = null;
                guardTower = null;
                guardPost = firingPost = workerFiringPost = null;
                forgetEnemy();
                followOwner(followPlayer);
                return;
            }
            if (!resumingPearlAttack
                    && escort == null
                    && (workerTarget == null || roofArcher)
                    && guardTower(defender)) return;
        }
        boolean assistingOwner = false;
        if (resumingPearlAttack) {
            attackTarget = pearlAttackTarget;
        } else if (defender != null
                && owner != null
                && (attackTarget == null || MobBattle.target(defender) != attackTarget)) {
            Mob ownerTarget = owner.recentMobAttacker();
            if (!canAssistOwner(defender, ownerTarget)) {
                ownerTarget = owner.recentMobAttack();
            }
            if (canAssistOwner(defender, ownerTarget)) {
                assistingOwner = true;
                if (ownerTarget != attackTarget) {
                    attackTarget = ownerTarget;
                    progressTicks = 0;
                }
                workerTarget = null;
            }
        }
        if (defender != null
                && escort != null
                && !resumingPearlAttack
                && !assistingOwner
                && (attackTarget == null || MobBattle.target(defender) != attackTarget)) {
            Mob escortThreat = nearestEscortThreat(defender);
            if (escortThreat == null) {
                attackTarget = null;
                forgetEnemy();
                escortWorker(defender);
                return;
            }
            workerTarget = escortThreat;
        }
        if (workerTarget != null
                && workerTarget != attackTarget
                && (attackTarget == null || MobBattle.target(defender) != attackTarget)) {
            attackTarget = workerTarget;
            progressTicks = 0;
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
        if (attackTarget != cliffAttackTarget)
            cliffAttackTarget = null;
        if (attackTarget != null) {
            if (defender != null && (attackTarget != lastSeenEnemy || enemySearchUntil > 0)) {
                rememberEnemy();
            }
            float dx = attackTarget.x - mob.x, dz = attackTarget.z - mob.z;
            if (experience > 0 && attackTarget instanceof Mob enemy
                    && tryCliffAttack(defender, enemy, experience))
                return;
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

    private void updateEscort(Defender defender) {
        if (!defender.hasSword() || level.isNetworkMode() || defender.riding != null) {
            setEscort(null);
            nextEscortCheck = 0;
            return;
        }
        if (escort != null
                && escort.canBeEscortedBy(defender)
                && defender.distanceToSqr(escort) <= ESCORT_LEASH * ESCORT_LEASH) return;
        setEscort(null);
        if (mob.tickCount < nextEscortCheck) return;
        nextEscortCheck = mob.tickCount + 15;
        float range = ESCORT_RANGE;
        var nearby =
                new ArrayList<>(level.findEntities(defender, defender.bb.grow(range, 8, range)));
        Set<Worker> covered = new HashSet<>();
        for (Object candidate : nearby) {
            if (candidate instanceof Defender other
                    && !other.removed
                    && other.health > 0
                    && other.hasSword()
                    && other.ai instanceof DefenderAI ai
                    && ai.escort != null) covered.add(ai.escort);
        }
        Worker nearest = null;
        float best = range * range;
        for (Object candidate : nearby) {
            if (!(candidate instanceof Worker worker)
                    || covered.contains(worker)
                    || !worker.canBeEscortedBy(defender)) continue;
            float distance = mob.distanceToSqr(worker);
            if (distance < best) {
                nearest = worker;
                best = distance;
            }
        }
        if (nearest != null) {
            setEscort(nearest);
            guardTower = null;
            guardPost = firingPost = workerFiringPost = null;
            clearNavigation();
        }
    }

    private void setEscort(Worker worker) {
        if (escort == worker) return;
        escort = worker;
        escortShelter = null;
        doorGuardPost = null;
        escortWalking = false;
    }

    String escortActivity() {
        if (escort == null) return "";
        if (escortShelter != null && !level.isDaytime()) return "Guarding shelter";
        String role = escort instanceof Miner ? "miner" : "builder";
        if (escort.ai instanceof WorkerAI ai
                && (ai.escortThreat() != null || ai.escortShelter() != null))
            return "Covering " + role;
        return "Escorting " + role;
    }

    private Mob nearestEscortThreat(Defender defender) {
        if (CombatRules.difficulty(level) == Difficulty.PEACEFUL) return null;
        Mob retreatThreat = escort.ai instanceof WorkerAI ai ? ai.escortThreat() : null;
        Mob lastAttacker = escort.recentHostileAttacker();
        Mob nearest = null;
        boolean urgent = false;
        float best = Float.POSITIVE_INFINITY;
        for (Object candidate : level.findEntities(escort, escort.bb.grow(8, 5, 8))) {
            if (!(candidate instanceof Mob enemy)
                    || enemy == stalledTarget
                    || !defender.canAttack(enemy)) continue;
            boolean attacker =
                    enemy == retreatThreat
                            || enemy == lastAttacker
                            || enemy.ai instanceof BasicAI ai && ai.attackTarget == escort;
            float distance = mob.distanceToSqr(enemy);
            float radius = attacker ? 8 : 4;
            if (distance <= 12 * 12
                    && escort.distanceToSqr(enemy) <= radius * radius
                    && (attacker && !urgent || attacker == urgent && distance < best)
                    && hasLineOfSight(defender, enemy)) {
                best = distance;
                nearest = enemy;
                urgent = attacker;
            }
        }
        return nearest;
    }

    private void escortWorker(Defender defender) {
        WorkerAI ai = escort.ai instanceof WorkerAI workerAI ? workerAI : null;
        Builder.Site shelter = ai == null ? null : ai.escortShelter();
        Mob threat = ai == null ? null : ai.escortThreat();
        if (shelter != null
                && escort.z >= shelter.z() - .5f
                && Math.abs(escort.x - shelter.entrance().x() - .5f) < 2) {
            guardShelterDoor(shelter, threat);
            return;
        }
        escortShelter = null;
        doorGuardPost = null;
        float distance = defender.distanceToSqr(escort);
        float followDistance = escortWalking ? 2.6f : 3.6f;
        escortWalking =
                distance > followDistance * followDistance || !hasLineOfSight(defender, escort);
        if (escortWalking) {
            running = threat != null || shelter != null || distance > 7 * 7;
            navigateTo(escort.x, escort.bb.y0, escort.z, running ? 1f : .8f);
        } else {
            holdEscortPosition(threat == null ? escort : threat);
        }
    }

    private void guardShelterDoor(Builder.Site shelter, Mob threat) {
        var door = shelter.entrance();
        if (!shelter.equals(escortShelter)) {
            escortShelter = shelter;
            doorGuardPost = null;
            nextDoorGuardCheck = 0;
        }
        if (mob.tickCount >= nextDoorGuardCheck) {
            nextDoorGuardCheck = mob.tickCount + 20;
            if (doorGuardPost != null
                    && !Pathfinder.canStandAt(
                            level, mob, doorGuardPost.x, doorGuardPost.y, doorGuardPost.z))
                doorGuardPost = null;
            if (doorGuardPost == null) {
                for (int side : new int[] {-1, 1}) {
                    int x = door.x() + side, z = door.z() - 2;
                    if (Pathfinder.findPath(level, mob, x, door.y(), z, 384) != null) {
                        doorGuardPost = new Vec3D(x + .5f, door.y(), z + .5f);
                        break;
                    }
                }
            }
        }
        Vec3D post = doorGuardPost;
        if (post != null && mob.distanceTo(post.x, post.y + mob.heightOffset, post.z) > .7f) {
            navigateTo(post.x, post.y, post.z, .9f);
        } else {
            holdEscortPosition(threat);
        }
    }

    private void holdEscortPosition(Mob watch) {
        float yaw =
                watch == null
                        ? 180
                        : (float) Math.toDegrees(Math.atan2(mob.x - watch.x, watch.z - mob.z));
        holdPosition(yaw);
        mob.xd *= .45f;
        mob.zd *= .45f;
    }

    private void refreshWorkerThreats(Defender defender) {
        if (mob.tickCount < nextWorkerCheck) return;
        nextWorkerCheck = mob.tickCount + 5;
        if (workerThreats == null) workerThreats = new HashSet<>();
        workerThreats.clear();
        if (CombatRules.difficulty(level) == Difficulty.PEACEFUL) return;
        float range = WORKER_PROTECTION_RANGE;
        for (Object candidate :
                level.findEntities(defender, defender.bb.grow(range, range, range))) {
            if (candidate instanceof Worker worker && nearbyWorker(defender, worker)) {
                Mob attacker = worker.recentHostileAttacker();
                if (attacker != null && defender.distanceToSqr(attacker) <= range * range)
                    workerThreats.add(attacker);
            } else if (candidate instanceof Mob hostile
                    && hostile.ai instanceof BasicAI ai
                    && ai.attackTarget instanceof Worker worker
                    && nearbyWorker(defender, worker)
                    && Defender.isWorkerEnemy(hostile)
                    && defender.distanceToSqr(hostile) <= range * range) {
                workerThreats.add(hostile);
            }
        }
    }

    private boolean nearbyWorker(Defender defender, Worker worker) {
        return !worker.removed
                && worker.health > 0
                && worker.level == level
                && defender.distanceToSqr(worker)
                        <= WORKER_PROTECTION_RANGE * WORKER_PROTECTION_RANGE;
    }

    boolean isProtectingWorkerFrom(Entity enemy) {
        return workerThreats != null && workerThreats.contains(enemy);
    }

    private Mob nearestWorkerThreat(Defender defender) {
        if (workerThreats == null) return null;
        Mob nearest = null;
        float best = Float.POSITIVE_INFINITY;
        for (Mob enemy : workerThreats) {
            float distance = defender.distanceToSqr(enemy);
            if (distance < best
                    && enemy != stalledTarget
                    && defender.canAttack(enemy)
                    && hasLineOfSight(defender, enemy)) {
                nearest = enemy;
                best = distance;
            }
        }
        return nearest;
    }

    private boolean atTowerRoof() {
        return guardTower != null
                && mob.bb.y0 >= guardTower.y() + 3.9f
                && mob.x >= guardTower.x() + 1
                && mob.x < guardTower.x() + guardTower.width()
                && mob.z >= guardTower.z()
                && mob.z < guardTower.z() + guardTower.width();
    }

    private boolean guardTower(Defender defender) {
        if (level.isNetworkMode()
                || defender.riding != null
                || defender.isInWater()
                || defender.isInLava()) return false;
        if (mob.tickCount >= nextTowerCheck) {
            nextTowerCheck = mob.tickCount + 40;
            if (guardTower != null && !guardTower.isStairTower(level)) {
                guardTower = null;
                guardPost = firingPost = workerFiringPost = null;
            }
            if (guardTower == null) findGuardTower();
        }
        if (guardTower == null) return false;
        if (!atTowerRoof()) {
            attackTarget = null;
            firingPost = workerFiringPost = null;
            nextFiringPostCheck = 0;
            navigateTo(guardPost.x, guardPost.y, guardPost.z, .85f);
            return true;
        }
        guardFromRoof(defender);
        return true;
    }

    private void findGuardTower() {
        float nearest = Float.POSITIVE_INFINITY;
        for (Builder.Site site : Builder.sitesNear(mob, 14)) {
            if (!site.isStairTower(level)) continue;
            Vec3D post = availableGuardPost(site);
            if (post == null) continue;
            float distance = mob.distanceTo(post.x, post.y, post.z);
            if (distance >= nearest) continue;
            nearest = distance;
            guardTower = site;
            guardPost = post;
        }
    }

    private Vec3D availableGuardPost(Builder.Site site) {
        int y = site.y() + 4;
        for (int z : new int[] {site.z() + 3, site.z() + 1}) {
            for (int x = site.x() + 2; x <= site.x() + 4; x += 2) {
                Vec3D post = new Vec3D(x + .5f, y, z + .5f);
                if (postAvailable(post) && Pathfinder.findPath(level, mob, x, y, z, 768) != null)
                    return post;
            }
        }
        return null;
    }

    private boolean postAvailable(Vec3D post) {
        for (Object entity : level.findEntities(mob, mob.bb.grow(14, 7, 14))) {
            if (!(entity instanceof Defender ally)
                    || ally.removed
                    || ally.health <= 0
                    || !(ally.ai instanceof DefenderAI ai)) continue;
            if (post.distanceSquared(new Vec3D(ally.x, ally.bb.y0, ally.z)) < .65f * .65f
                    || ai.guardPost != null && post.distanceSquared(ai.guardPost) < .65f * .65f
                    || ai.firingPost != null && post.distanceSquared(ai.firingPost) < .65f * .65f
                    || ai.workerFiringPost != null
                            && post.distanceSquared(ai.workerFiringPost) < .65f * .65f)
                return false;
        }
        return true;
    }

    private void guardFromRoof(Defender defender) {
        int ammo = defender.hasBow() ? defender.packedItemSlot(Equipment.ARROW) : -1;
        float range = ammo >= 0 ? BOW_RANGE : 2;
        Entity enemy = null;
        boolean workerThreat = false;
        float nearest = range * range;
        for (Object candidate :
                level.findEntities(defender, defender.bb.grow(range, range, range))) {
            if (!(candidate instanceof Mob hostile)
                    || !defender.canAttack(hostile)
                    || ammo >= 0 && hostile instanceof Enderman && !withinMeleeReach(hostile, 2f))
                continue;
            float distance = mob.distanceToSqr(hostile);
            boolean priority = isProtectingWorkerFrom(hostile);
            if (mob.onGround
                    && distance < range * range
                    && (priority && !workerThreat || priority == workerThreat && distance < nearest)
                    && hasLineOfSight(defender, hostile)
                    && (ammo < 0 || clearShot(mob.x, mob.bb.y0, mob.z, hostile))) {
                enemy = hostile;
                nearest = distance;
                workerThreat = priority;
            }
        }
        attackTarget = enemy;
        if (ammo >= 0 && !workerThreat && workerThreats != null && !workerThreats.isEmpty()) {
            if (mob.tickCount >= nextWorkerPostCheck) {
                nextWorkerPostCheck = mob.tickCount + 10;
                workerFiringPost = findFiringPost(defender, true);
            }
            if (workerFiringPost != null) {
                attackTarget = null;
                walkToRoofPost(defender, workerFiringPost);
                return;
            }
        } else {
            workerFiringPost = null;
            nextWorkerPostCheck = 0;
        }
        if (enemy != null) {
            firingPost = null;
            nextFiringPostCheck = 0;
            float dx = enemy.x - mob.x, dz = enemy.z - mob.z;
            holdRoofPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
            if (attackDelay == 0 && CombatRules.difficulty(level) != Difficulty.PEACEFUL) {
                if (ammo >= 0 && !(enemy instanceof Enderman)) drawBow(defender, ammo, dx, dz);
                else if (withinMeleeReach(enemy, 2f)) attack(enemy);
            }
            return;
        }
        if (ammo >= 0 && mob.tickCount >= nextFiringPostCheck) {
            nextFiringPostCheck = mob.tickCount + 10;
            firingPost = findFiringPost(defender, false);
        }
        Vec3D post = ammo >= 0 && firingPost != null ? firingPost : guardPost;
        walkToRoofPost(defender, post);
    }

    private void walkToRoofPost(Defender defender, Vec3D post) {
        float dx = post.x - mob.x, dz = post.z - mob.z;
        float distance = dx * dx + dz * dz;
        boolean atHeight = Math.abs(post.y - mob.bb.y0) < .15f;
        if (atHeight && distance <= .08f * .08f) {
            holdRoofPosition(mob.yRot);
        } else if (atHeight
                && distance <= .65f * .65f
                && Pathfinder.canWalkStraight(level, defender, post.x, post.y, post.z)) {
            holdPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
            mob.xd = dx * .2f;
            mob.zd = dz * .2f;
        } else {
            navigateTo(post.x, post.y, post.z, .75f);
        }
    }

    private Vec3D findFiringPost(Defender defender, boolean protectingWorker) {
        Vec3D best = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (Object candidate :
                new ArrayList<>(
                        level.findEntities(
                                defender, defender.bb.grow(BOW_RANGE, BOW_RANGE, BOW_RANGE)))) {
            if (!(candidate instanceof Mob hostile)
                    || hostile instanceof Enderman
                    || protectingWorker && !isProtectingWorkerFrom(hostile)
                    || !defender.canAttack(hostile)) continue;
            int east = guardTower.x() + guardTower.width() - 1;
            int south = guardTower.z() + guardTower.width() - 1;
            for (int x = guardTower.x() + 1; x <= east; x++) {
                for (int z = guardTower.z(); z <= south; z++) {
                    boolean edge =
                            x == guardTower.x() + 1
                                    || x == east
                                    || z == guardTower.z()
                                    || z == south;
                    int y = guardTower.y() + (edge ? 5 : 4);
                    float px = x + .5f, pz = z + .5f;
                    float distance = mob.distanceTo(px, y + mob.heightOffset, pz);
                    if (distance >= nearest
                            || hostile.distanceTo(px, y, pz) > BOW_RANGE
                            || !Pathfinder.canStandAt(level, defender, px, y, pz)
                            || !clearShot(px, y, pz, hostile)
                            || Pathfinder.findPath(level, defender, x, y, z, 96) == null) continue;
                    Vec3D post = new Vec3D(px, y, pz);
                    if (!postAvailable(post)) continue;
                    nearest = distance;
                    best = post;
                }
            }
        }
        return best;
    }

    private boolean clearShot(float x, float feet, float z, Mob enemy) {
        float y = feet + mob.bbHeight * .8f;
        float dx = enemy.x - x, dz = enemy.z - z;
        float rise = (enemy.bb.y0 + enemy.bb.y1) * .5f - y;
        float distance = (float) Math.hypot(dx, dz);
        if (distance < .01f) return false;
        double angle = bowElevation(distance, rise);
        float horizontalSpeed = ARROW_SPEED * (float) Math.cos(angle);
        float vx = dx / distance * horizontalSpeed, vz = dz / distance * horizontalSpeed;
        float vy = ARROW_SPEED * (float) Math.sin(angle);
        AABB arrow =
                new AABB(
                        x - Arrow.HALF_WIDTH,
                        y - Arrow.HALF_HEIGHT,
                        z - Arrow.HALF_WIDTH,
                        x + Arrow.HALF_WIDTH,
                        y + Arrow.HALF_HEIGHT,
                        z + Arrow.HALF_WIDTH);
        int flightTicks = (int) Math.ceil(distance / horizontalSpeed) + 1;
        for (int tick = 0; tick < flightTicks; tick++) {
            vx *= Arrow.FLIGHT_DRAG;
            vy = vy * Arrow.FLIGHT_DRAG - .02f;
            vz *= Arrow.FLIGHT_DRAG;
            AABB sweep = arrow.expand(vx, vy, vz);
            if (!sweep.intersectsInner(enemy.bb) && level.getCubes(sweep).isEmpty()) {
                arrow.move(vx, vy, vz);
                continue;
            }
            int steps = 1 + (int) (Math.sqrt(vx * vx + vy * vy + vz * vz) / .2f);
            for (int step = 0; step < steps; step++) {
                AABB segment = arrow.expand(vx / steps, vy / steps, vz / steps);
                if (!level.getCubes(segment).isEmpty()) return false;
                if (segment.intersectsInner(enemy.bb)) return true;
                arrow.move(vx / steps, vy / steps, vz / steps);
            }
        }
        return false;
    }

    private void holdRoofPosition(float yaw) {
        holdPosition(yaw);
        mob.xd = mob.zd = 0;
    }

    private boolean tryCliffAttack(Defender defender, Mob enemy, int experience) {
        if (level.isNetworkMode() || CombatRules.difficulty(level) == Difficulty.PEACEFUL
                || !defender.onGround || defender.isInKnockback() || defender.riding != null
                || defender.isInWater() || defender.isInLava())
            return false;

        if (enemy == cliffAttackTarget) {
            if (enemy.isInKnockback() || !enemy.onGround) {
                stopAtCliff(defender, enemy.x - defender.x, enemy.z - defender.z);
                return true;
            }
            cliffAttackTarget = null;
            if (enemy.bb.y0 < defender.bb.y0 - .5f) {
                stalledTarget = enemy;
                retryTicks = 40;
                attackTarget = null;
                forgetEnemy();
                stopAtCliff(defender, enemy.x - defender.x, enemy.z - defender.z);
                return true;
            }
        }

        float dx = enemy.x - defender.x, dz = enemy.z - defender.z;
        if (!enemy.onGround || enemy.isInKnockback() || enemy.noPhysics || enemy.riding != null
                || Math.abs(defender.bb.y0 - enemy.bb.y0) > .12f
                || dx * dx + dz * dz > CLIFF_APPROACH_RANGE * CLIFF_APPROACH_RANGE
                || !hasLineOfSight(defender, enemy))
            return false;

        float feet = defender.bb.y0;
        if (withinMeleeReach(enemy, 2f) && knockbackReachesDrop(enemy, dx, dz)
                && Pathfinder.hasStandingRoom(level, defender, defender.x, feet, defender.z)) {
            stopAtCliff(defender, dx, dz);
            int previousHealth = enemy.health;
            if (attackDelay == 0 && attack(enemy)) {
                if (experience >= 3)
                    attackDelay = 5;
                if (enemy.health > 0 && enemy.health < previousHealth && enemy.isInKnockback()) {
                    enemy.markForcedFall(defender);
                    cliffAttackTarget = enemy;
                }
            }
            return true;
        }

        // Circle on the supported shelf and line up a hit away from the defender.
        double bearing = Math.atan2(dz, dx);
        for (int turn : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
            double angle = bearing + turn * Math.PI / 4;
            float pushX = (float) Math.cos(angle), pushZ = (float) Math.sin(angle);
            if (!knockbackReachesDrop(enemy, pushX, pushZ))
                continue;
            float x = enemy.x - pushX * CLIFF_STANCE_DISTANCE;
            float z = enemy.z - pushZ * CLIFF_STANCE_DISTANCE;
            if (!Pathfinder.hasStandingRoom(level, defender, x, feet, z)
                    || !Pathfinder.canWalkStraight(level, defender, x, feet, z))
                continue;
            stopAtCliff(defender, x - defender.x, z - defender.z);
            yya = .65f;
            return true;
        }
        return false;
    }

    private boolean knockbackReachesDrop(Mob enemy, float dx, float dz) {
        float length = (float) Math.hypot(dx, dz);
        if (length < .01f)
            return false;
        float x = dx * CLIFF_KNOCKBACK_DISTANCE / length;
        float z = dz * CLIFF_KNOCKBACK_DISTANCE / length;
        AABB body = enemy.bb.cloneMove(0, .001f, 0);
        return level.isInBounds((int) Math.floor(enemy.x + x), (int) Math.floor(body.y0),
                (int) Math.floor(enemy.z + z)) && level.getCubes(body.expand(x, 0, z)).isEmpty()
                && level.getCubes(body.cloneMove(x, 0, z).expand(0, -CLIFF_MIN_DROP, 0)).isEmpty();
    }

    private void stopAtCliff(Defender defender, float dx, float dz) {
        progressTicks = 0;
        holdPosition((float) Math.toDegrees(Math.atan2(-dx, dz)));
        defender.xd = defender.zd = 0;
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
        float rise = (attackTarget.bb.y0 + attackTarget.bb.y1) * .5f - startY;
        mob.xRot = (float) -Math.toDegrees(bowElevation(distance, rise));
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

    private static double bowElevation(float distance, float rise) {
        double angle = Math.atan2(rise, distance);
        for (int i = 0; i < 2; i++) {
            double ticks = distance / (ARROW_SPEED * Math.max(.1, Math.cos(angle)));
            angle = Math.atan2(rise + .01 * ticks * (ticks + 1), distance);
        }
        return angle;
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
