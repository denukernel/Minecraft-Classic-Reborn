package net.classicremastered.minecraft.mob.ai.stickfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.entity.*;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.StickFigureModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/**
 * Server-side combat phases. A canceled combo never leaves player damage
 * protection behind.
 */
public final class StickFigurePowers implements java.io.Serializable {

    private static final long serialVersionUID = -588989935522282400L;

    public enum Phase {
        IDLE, THROW, FIREBALL, UPPERCUT, BARRAGE, SLAM, PHYSICS_GUN
    }

    public static final int THROW_RELEASE_TICK = 12;
    public static final int THROW_END_TICK = 20;
    private static final float MIN_THROW_RANGE = 5f, MAX_THROW_RANGE = 40f;
    public Phase phase = Phase.IDLE;
    public int phaseTicks, comboHits, rushTicks;
    private int throwCooldown = 100, rushCooldown = 120, comboCooldown = 200, fireballCooldown = 40;
    private transient Player victim;
    private transient BlockThrow heldBlock;
    private transient Mob pendingMob;
    private static final Map<Player, StickFigure> physicsVictims = new java.util.WeakHashMap<>();
    private int physicsCooldown;
    private boolean stolenPhysicsGun;
    private float anchorX, anchorY, anchorZ;

    private record BlockThrow(int id, int size, List<int[]> cells) {
    }

    public boolean blocksNavigation() {
        return phase != Phase.IDLE;
    }

    public int heldBlockId() {
        return heldBlock == null ? 0 : heldBlock.id();
    }

    public int heldBlockSize() {
        return heldBlock == null ? 0 : heldBlock.size();
    }

    public static boolean protects(Player player) {
        StickFigure boss = player.bossComboOwner;
        boolean valid = boss != null
                && !boss.removed
                && boss.health > 0
                && boss.level == player.level
                && !player.level.creativeMode
                && boss.ai instanceof StickFigureAI ai
                && ai.powers.phase == Phase.BARRAGE
                && ai.powers.victim == player;
        if (!valid)
            player.bossComboOwner = null;
        return valid;
    }

    public void cancel(StickFigure boss) {
        if (stolenPhysicsGun) {
            boss.level.addEntity(new DroppedItem(boss.level, boss.x, boss.bb.y0 + 1, boss.z, 10));
            stolenPhysicsGun = false;
            physicsCooldown = 300;
        }
        if (victim != null && physicsVictims.get(victim) == boss) {
            physicsVictims.remove(victim);
            victim.xd = victim.yd = victim.zd = 0;
            victim.fallDistance = 0;
        }
        if (victim != null && victim.bossComboOwner == boss)
            victim.bossComboOwner = null;
        victim = null;
        heldBlock = null;
        pendingMob = null;
        phase = Phase.IDLE;
        phaseTicks = 0;
        rushTicks = 0;
        boss.strikeTicks = 0;
    }

    private static void sound(StickFigure boss, String key, float pitch) {
        if (boss.level.minecraft == null
                || boss.level.minecraft.settings == null
                || boss.level.minecraft.settings.allowMobSounds)
            boss.level.playSound(key, boss, .8f, pitch);
    }

    private static boolean visible(StickFigure boss, Entity target) {
        return boss.level.clip(
                new Vec3D(boss.x, boss.bb.y0 + 1.5f, boss.z),
                new Vec3D(target.x, target.bb.y0 + target.bbHeight * .5f, target.z),
                true) == null;
    }

    private static boolean canThrowAt(StickFigure boss, Entity target) {
        if (target == null
                || target.removed
                || target.level != boss.level
                || target instanceof Mob mob && mob.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(target))
            return false;
        float distance = boss.distanceTo(target);
        return distance > MIN_THROW_RANGE && distance < MAX_THROW_RANGE && visible(boss, target);
    }

    private static void faceThrow(StickFigure boss, float dx, float dz) {
        if (dx * dx + dz * dz < .0001f)
            return;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        boss.yRot += (float) Math.IEEEremainder(yaw - boss.yRot, 360);
        boss.yBodyRot = boss.yRot;
    }

    public boolean beginCombo(StickFigure boss, Player player) {
        if (phase != Phase.IDLE
                || boss.health <= 0
                || player.health <= 0
                || player.level != boss.level
                || CreativeModeHelper.shouldIgnoreTarget(player)
                || protects(player)
                || CombatRules.difficulty(boss.level) == Difficulty.PEACEFUL
                || boss.distanceTo(player) > 3
                || !visible(boss, player)
                || !boss.level.getCubes(player.bb.expand(0, 3.5f, 0)).isEmpty())
            return false;
        victim = player;
        comboHits = 0;
        phase = Phase.UPPERCUT;
        phaseTicks = 0;
        comboCooldown = 1800;
        boss.strikeTicks = 14;
        boss.kicking = false;
        sound(boss, "random/fuse", 1.5f);
        return true;
    }

    public boolean tick(StickFigure boss, Entity target, Random random) {
        if (physicsCooldown > 0)
            physicsCooldown--;
        if (throwCooldown > 0)
            throwCooldown--;
        if (rushCooldown > 0)
            rushCooldown--;
        if (comboCooldown > 0)
            comboCooldown--;
        if (fireballCooldown > 0)
            fireballCooldown--;
        if (rushTicks > 0)
            rushTicks--;
        if (boss.isEnraged())
            throwCooldown = Math.min(throwCooldown, 60);
        if (boss.health <= 0
                || boss.removed
                || CombatRules.difficulty(boss.level) == Difficulty.PEACEFUL) {
            cancel(boss);
            return false;
        }
        if (phase == Phase.PHYSICS_GUN) {
            if (!validPhysicsVictim(boss, victim) || ++phaseTicks >= 100) {
                cancel(boss);
                return true;
            }
            faceThrow(boss, victim.x - boss.x, victim.z - boss.z);
            boss.xd = boss.zd = 0;
            return true;
        }
        if (phase != Phase.IDLE && phase != Phase.THROW && phase != Phase.FIREBALL) {
            if (victim == null
                    || victim.removed
                    || victim.level != boss.level
                    || CreativeModeHelper.shouldIgnoreTarget(victim)) {
                cancel(boss);
                return false;
            }
            if (phase == Phase.SLAM && (victim.onGround || victim.isInWater())) {
                float x = victim.x, y = victim.bb.y0 + .1f, z = victim.z;
                cancel(boss);
                boss.level.playSound("random/explode", x, y, z, 1, .8f);
                if (boss.level.particleEngine != null)
                    for (int i = 0; i < 48; i++) {
                        float dx = (boss.level.random.nextFloat() - .5f) * .6f,
                                dz = (boss.level.random.nextFloat() - .5f) * .6f;
                        boss.level.particleEngine.spawnParticle(
                                new net.classicremastered.minecraft.particle.SmokeParticle(
                                        boss.level, x + dx, y + .2f, z + dz));
                        boss.level.particleEngine.spawnParticle(
                                new net.classicremastered.minecraft.particle.TerrainParticle(
                                        boss.level,
                                        x,
                                        y,
                                        z,
                                        dx,
                                        .2f + boss.level.random.nextFloat() * .4f,
                                        dz,
                                        Block.STONE));
                    }
                boss.level.explode(boss, x, y, z, 2.5f);
                return true;
            }
            if (victim.health <= 0 || boss.distanceTo(victim) > 24) {
                cancel(boss);
                return false;
            }
            phaseTicks++;
            if (phase == Phase.UPPERCUT && phaseTicks >= 20) {
                if (boss.distanceTo(victim) > 3.5f
                        || victim.bossComboOwner != null && victim.bossComboOwner != boss) {
                    cancel(boss);
                    return false;
                }
                anchorX = victim.x;
                anchorY = victim.y + 3;
                anchorZ = victim.z;
                victim.yd = .9f;
                victim.bossComboOwner = boss;
                victim.health = victim.lastHealth = 1;
                victim.fallDistance = 0;
                phase = Phase.BARRAGE;
                phaseTicks = 0;
                boss.strikeTicks = 10;
                sound(boss, "mob/irongolem/hit", 1);
            } else if (phase == Phase.BARRAGE) {
                victim.health = victim.lastHealth = 1;
                victim.fallDistance = 0;
                victim.xd = (anchorX - victim.x) * .2f;
                victim.zd = (anchorZ - victim.z) * .2f;
                victim.yd = Math.max(-.2f, Math.min(.35f, (anchorY - victim.y) * .25f + .08f));
                boss.xd = (anchorX - 1 - boss.x) * .2f;
                boss.zd = (anchorZ - boss.z) * .2f;
                boss.yd = Math.max(-.25f, Math.min(.5f, (anchorY - .3f - boss.y) * .25f + .08f));
                boss.fallDistance = 0;
                if (phaseTicks % 5 == 0) {
                    comboHits++;
                    boss.strikeTicks = 12;
                    boss.kicking = false;
                    victim.hurtTime = victim.hurtDuration = 5;
                    sound(boss, "damage/hurtflesh", 1.2f);
                }
                if (phaseTicks >= 100) {
                    phase = Phase.SLAM;
                    phaseTicks = 0;
                    victim.bossComboOwner = null;
                    victim.yd = -1.2f;
                    victim.xd = victim.zd = 0;
                    victim.fallDistance = 0;
                    boss.strikeTicks = 12;
                    boss.kicking = true;
                    sound(boss, "mob/irongolem/hit", .65f);
                }
            } else if (phase == Phase.SLAM && phaseTicks > 80)
                cancel(boss);
            return true;
        }
        if (phase == Phase.THROW) {
            if (phaseTicks < THROW_RELEASE_TICK) {
                if ((heldBlock == null && pendingMob == null) || !canThrowAt(boss, target)) {
                    throwCooldown = 20;
                    cancel(boss);
                    return false;
                }
                faceThrow(boss, target.x - boss.x, target.z - boss.z);
            }
            phaseTicks++;
            if (phaseTicks == THROW_RELEASE_TICK) {
                if (heldBlock != null)
                    releaseBlock(boss, target, heldBlock);
                else if (canThrowMob(boss, pendingMob))
                    launchMob(boss, target, pendingMob);
                heldBlock = null;
                pendingMob = null;
                throwCooldown = boss.isEnraged() ? 60 : 180;
            } else if (phaseTicks >= THROW_END_TICK) {
                phase = Phase.IDLE;
                phaseTicks = 0;
            }
            return true;
        }
        if (phase == Phase.FIREBALL) {
            if (target == null
                    || target.removed
                    || target.level != boss.level
                    || CreativeModeHelper.shouldIgnoreTarget(target)) {
                cancel(boss);
                return false;
            }
            if (++phaseTicks >= 12) {
                if (boss.isEnraged())
                    shootFireball(boss, target);
                fireballCooldown = 50;
                phase = Phase.IDLE;
                phaseTicks = 0;
            }
            return true;
        }
        if (target == null || target.removed || CreativeModeHelper.shouldIgnoreTarget(target))
            return false;
        if (target instanceof Player player && beginPhysicsGun(boss, player))
            return true;
        dodgeArrow(boss);
        float distance = boss.distanceTo(target);
        if (boss.isEnraged()
                && fireballCooldown == 0
                && distance > 3
                && distance < 40
                && visible(boss, target)) {
            phase = Phase.FIREBALL;
            phaseTicks = 0;
            boss.strikeTicks = 14;
            sound(boss, "random/fuse", 1.7f);
            return true;
        }
        if (comboCooldown == 0
                && target instanceof Player player
                && distance < 2.5f
                && random.nextInt(1000) == 0
                && beginCombo(boss, player))
            return true;
        if (rushTicks == 0
                && throwCooldown == 0
                && canThrowAt(boss, target)
                && (boss.isEnraged() || random.nextInt(80) == 0)) {
            if (boss.isEnraged()) {
                if (random.nextInt(6) == 0)
                    heldBlock = findBlock(boss, 2);
            } else if (random.nextBoolean())
                pendingMob = findThrowableMob(boss);
            if (heldBlock == null && pendingMob == null)
                heldBlock = findBlock(boss, 1);
            if (heldBlock != null || pendingMob != null) {
                phase = Phase.THROW;
                phaseTicks = 0;
                boss.strikeTicks = 0;
                faceThrow(boss, target.x - boss.x, target.z - boss.z);
                sound(boss, "random/fuse", 1.3f);
                return true;
            }
            throwCooldown = 20;
        }
        if (rushCooldown == 0
                && distance > 4
                && distance < 16
                && boss.onGround
                && random.nextInt(100) == 0) {
            rushTicks = 40;
            rushCooldown = 240;
            sound(boss, "random/bow", .7f);
        }
        return false;
    }

    public boolean beginPhysicsGun(StickFigure boss, Player player) {
        if (phase != Phase.IDLE
                || physicsCooldown > 0
                || !validPhysicsVictim(boss, player)
                || physicsVictims.containsKey(player)
                || player.bossComboOwner != null
                || player.inventory.getSelected() != 266
                || !visible(boss, player))
            return false;
        if (!player.inventory.removeSelected(1))
            return false;
        PhysicsGunItem.drop(player);
        victim = player;
        stolenPhysicsGun = true;
        phase = Phase.PHYSICS_GUN;
        phaseTicks = 0;
        boss.strikeTicks = 0;
        float length = Math.max(.01f, boss.distanceTo(player));
        anchorX = (player.x - boss.x) / length;
        anchorZ = (player.z - boss.z) / length;
        physicsVictims.put(player, boss);
        sound(boss, "random/pop", .8f);
        return true;
    }

    private static boolean validPhysicsVictim(StickFigure boss, Player player) {
        return player != null
                && !player.removed
                && player.health > 0
                && boss.health > 0
                && !boss.removed
                && player.level == boss.level
                && boss.distanceTo(player) <= PhysicsGunItem.MAX_RANGE
                && player.riding == null
                && player.rider == null
                && !CreativeModeHelper.shouldIgnoreTarget(player)
                && CombatRules.difficulty(boss.level) != Difficulty.PEACEFUL;
    }

    public static boolean movePhysicsVictim(Player player) {
        StickFigure boss = physicsVictims.get(player);
        if (boss == null)
            return false;
        if (!(boss.ai instanceof StickFigureAI ai)) {
            physicsVictims.remove(player);
            return false;
        }
        StickFigurePowers powers = ai.powers;
        if (powers.phase != Phase.PHYSICS_GUN
                || powers.victim != player
                || !validPhysicsVictim(boss, player)) {
            powers.cancel(boss);
            return false;
        }
        float sway = (float) Math.sin(powers.phaseTicks * .13f) * 3;
        Vec3D start = new Vec3D(boss.x, boss.bb.y0 + 1.5f, boss.z);
        Vec3D target = new Vec3D(
                boss.x + powers.anchorX * 4 - powers.anchorZ * sway,
                boss.bb.y0 + 2.5f + (float) Math.sin(powers.phaseTicks * .09f),
                boss.z + powers.anchorZ * 4 + powers.anchorX * sway);
        MovingObjectPosition wall = boss.level.clip(new Vec3D(start.x, start.y, start.z), target, true);
        if (wall != null) {
            float distance = Math.max(0, start.distance(wall.vec) - player.bbWidth - .2f);
            float fraction = distance / Math.max(.01f, start.distance(target));
            target = start.add(
                    (target.x - start.x) * fraction,
                    (target.y - start.y) * fraction,
                    (target.z - start.z) * fraction);
        }
        player.xd = player.xd * .45f + (target.x - player.x) * .22f;
        player.yd = player.yd * .45f + (target.y - (player.bb.y0 + player.bb.y1) * .5f) * .22f;
        player.zd = player.zd * .45f + (target.z - player.z) * .22f;
        float speed = (float) Math.sqrt(
                player.xd * player.xd
                        + player.yd * player.yd
                        + player.zd * player.zd);
        if (speed > .9f) {
            player.xd *= .9f / speed;
            player.yd *= .9f / speed;
            player.zd *= .9f / speed;
        }
        player.fallDistance = 0;
        player.move(player.xd, player.yd, player.zd);
        player.fallDistance = 0;
        return true;
    }

    public void renderPhysicsGun(StickFigure boss, float partial) {
        if (!stolenPhysicsGun || phase != Phase.PHYSICS_GUN || victim == null)
            return;
        float yaw = boss.yRotO + (boss.yRot - boss.yRotO) * partial;
        double angle = Math.toRadians(yaw);
        float x = boss.xo + (boss.x - boss.xo) * partial;
        float y = boss.yo + (boss.y - boss.yo) * partial + boss.bb.y0 - boss.y + 1.3f;
        float z = boss.zo + (boss.z - boss.zo) * partial;
        var pose = new net.classicremastered.minecraft.render.GunRenderer.Pose(
                x + (float) Math.cos(angle) * .25f - (float) Math.sin(angle) * .4f,
                y,
                z + (float) Math.sin(angle) * .25f + (float) Math.cos(angle) * .4f,
                180 - yaw,
                0,
                0,
                .68f);
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            pose.apply();
            net.classicremastered.minecraft.model.GunModel.render(
                    266, boss.getRenderBrightness(partial));
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
        net.classicremastered.minecraft.render.GunBeamRenderer.render(
                pose.transform(net.classicremastered.minecraft.model.GunModel.MUZZLE),
                victim,
                partial,
                false);
    }

    private void dodgeArrow(StickFigure boss) {
        for (Object o : boss.level.findEntities(boss, boss.bb.grow(7, 3, 7)))
            if (o instanceof Arrow arrow && !arrow.isEmbedded() && arrow.getOwner() != boss) {
                float vx = arrow.xd,
                        vz = arrow.zd,
                        speed = vx * vx + vz * vz,
                        dx = arrow.x - boss.x,
                        dz = arrow.z - boss.z;
                if (speed < .001f || dx * vx + dz * vz >= 0)
                    continue;
                float time = -(dx * vx + dz * vz) / speed;
                if (time > 12
                        || (dx + vx * time) * (dx + vx * time) + (dz + vz * time) * (dz + vz * time) > 1.5f)
                    continue;
                boss.evadeArrow(arrow);
            }
    }

    public boolean shootFireball(StickFigure boss, Entity target) {
        if (!boss.isEnraged()
                || target == null
                || target.removed
                || target.level != boss.level
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || !visible(boss, target))
            return false;
        float px = boss.x, py = boss.bb.y0 + 1.4f, pz = boss.z;
        float dx = target.x - px,
                dy = target.bb.y0 + target.bbHeight * .5f - py,
                dz = target.z - pz,
                length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .01f)
            return false;
        boss.level.addEntity(
                new BossFireball(
                        boss.level,
                        boss,
                        px,
                        py,
                        pz,
                        new Vec3D(dx / length * .65f, dy / length * .65f, dz / length * .65f)));
        sound(boss, "random/bow", .55f);
        return true;
    }

    public boolean throwLargeBlock(StickFigure boss, Entity target) {
        return canThrowAt(boss, target) && releaseBlock(boss, target, findBlock(boss, 2));
    }

    public boolean throwAt(StickFigure boss, Entity target, boolean preferMob) {
        if (!canThrowAt(boss, target))
            return false;
        Mob passive = preferMob ? findThrowableMob(boss) : null;
        if (passive != null) {
            launchMob(boss, target, passive);
            return true;
        }
        return releaseBlock(boss, target, findBlock(boss, 1));
    }

    private static BlockThrow findBlock(StickFigure boss, int size) {
        int bx = (int) Math.floor(boss.x),
                by = (int) Math.floor(boss.bb.y0),
                bz = (int) Math.floor(boss.z);
        int reach = size + 2;
        Map<Integer, List<int[]>> candidates = new LinkedHashMap<>();
        for (int y = by + size + 1; y >= by - 1; y--)
            for (int z = bz - reach; z <= bz + reach; z++)
                for (int x = bx - reach; x <= bx + reach; x++) {
                    int id = boss.level.getTile(x, y, z);
                    if (!GravityGunItem.canGrabBlock(id)
                            || Math.abs(x + .5f - boss.x) < 1 && Math.abs(z + .5f - boss.z) < 1)
                        continue;
                    int[] cell = {x, y, z};
                    if (size == 1)
                        return new BlockThrow(id, size, List.of(cell));
                    List<int[]> cells = candidates.computeIfAbsent(id, k -> new ArrayList<>());
                    cells.add(cell);
                    if (cells.size() == 8)
                        return new BlockThrow(id, size, cells);
                }
        return null;
    }

    private static boolean releaseBlock(StickFigure boss, Entity target, BlockThrow block) {
        if (block == null)
            return false;
        int id = block.id();
        for (int[] cell : block.cells())
            if (boss.level.getTile(cell[0], cell[1], cell[2]) != id)
                return false;
        List<int[]> removed = new ArrayList<>();
        for (int[] cell : block.cells()) {
            if (boss.level.getTile(cell[0], cell[1], cell[2]) != id
                    || !boss.level.setTile(cell[0], cell[1], cell[2], 0)
                    || boss.level.getTile(cell[0], cell[1], cell[2]) != 0) {
                for (int[] taken : removed) {
                    if (!boss.level.setTile(taken[0], taken[1], taken[2], id))
                        boss.level.addEntity(
                                new DroppedBlock(
                                        boss.level,
                                        taken[0] + .5f,
                                        taken[1] + .5f,
                                        taken[2] + .5f,
                                        id));
                }
                return false;
            }
            removed.add(cell);
        }
        launchBlock(boss, target, id, block.size());
        return true;
    }

    private static Mob findThrowableMob(StickFigure boss) {
        for (Object o : boss.level.findEntities(boss, boss.bb.grow(6, 3, 6)))
            if (o instanceof Mob passive && canThrowMob(boss, passive))
                return passive;
        return null;
    }

    private static boolean canThrowMob(StickFigure boss, Mob passive) {
        return (passive instanceof Pig || passive instanceof Sheep || passive instanceof Chicken)
                && !passive.removed
                && passive.health > 0
                && passive.level == boss.level
                && boss.bb.grow(6, 3, 6).intersects(passive.bb)
                && !(passive.ai instanceof BossThrownMobAI)
                && passive.rider == null
                && passive.riding == null
                && !PhysicsGunItem.isHeld(passive)
                && !GravityGunItem.grabbedMobs.containsValue(passive)
                && visible(boss, passive);
    }

    private static void launchMob(StickFigure boss, Entity target, Mob passive) {
        passive.ai = new BossThrownMobAI(passive, boss, target);
        Vec3D v = velocity(passive.x, passive.y, passive.z, target, .9f, .08f);
        passive.xd = v.x;
        passive.yd = v.y;
        passive.zd = v.z;
        sound(boss, "random/bow", .75f);
    }

    private static void launchBlock(StickFigure boss, Entity target, int id, int size) {
        Vec3D grip = StickFigureModel.releaseOffset(size);
        float y = boss.y - boss.heightOffset + boss.renderOffset + grip.y;
        float dx = target.x - boss.x, dz = target.z - boss.z;
        float speed = size == 2 ? 1.15f : 1.35f;
        // Bound the lead so a sudden knockback does not send the throw far off target.
        float motionScale = .4f / Math.max(.4f, (float) Math.hypot(target.xd, target.zd));
        float vx = target.xd * motionScale, vz = target.zd * motionScale;
        float time = Math.max(1, ((float) Math.hypot(dx, dz) - grip.z) / speed);
        for (int i = 0; i < 5; i++)
            time = Math.max(
                    1,
                    ((float) Math.hypot(dx + vx * time, dz + vz * time) - grip.z) / speed);
        dx += vx * time;
        dz += vz * time;
        float distance = (float) Math.hypot(dx, dz);
        float offsetX = distance == 0 ? 0 : dx * grip.z / distance;
        float offsetZ = distance == 0 ? 0 : dz * grip.z / distance;
        float x = boss.x + offsetX, z = boss.z + offsetZ;
        float dy = (target.bb.y0 + target.bb.y1) * .5f - y;
        // Gravity is applied before each movement step.
        Vec3D velocity = new Vec3D(
                (dx - offsetX) / time,
                dy / time + BossThrownBlock.GRAVITY * (time + 1) * .5f,
                (dz - offsetZ) / time);
        faceThrow(boss, dx, dz);
        BossThrownBlock block = size == 2
                ? new BossThrownBlock(boss.level, boss, id, x, y, z, velocity, size)
                : new BossThrownBlock(boss.level, boss, id, x, y, z, velocity);
        boss.level.addEntity(block);
        sound(boss, "random/bow", size == 2 ? .5f : .8f);
    }

    private static Vec3D velocity(
            float x, float y, float z, Entity target, float speed, float gravity) {
        float dx = target.x - x,
                dy = (target.bb.y0 + target.bbHeight * .5f) - y,
                dz = target.z - z,
                time = Math.max(1, (float) Math.hypot(dx, dz) / speed);
        return new Vec3D(dx / time, dy / time + gravity * time * .5f, dz / time);
    }
}
