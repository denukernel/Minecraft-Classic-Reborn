package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.mob.Cow;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Pig;
import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Spider;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

import org.lwjgl.input.Keyboard;

public final class MobRiding {
    private MobRiding() {
    }

    private static boolean available(Entity mount) {
        return mount instanceof Mob mob && mob.health > 0 && !mob.removed && !mob.noPhysics
                && mob.rider == null && mob.riding == null
                && (!(mount instanceof Cow cow) || cow.isSaddled());
    }

    public static boolean canRide(Player player, Entity mount) {
        return mount instanceof Cow && available(mount) && player.riding == null;
    }

    public static void findJockeyMount(Mob mob) {
        if (!(mob instanceof Zombie)
                || mob instanceof net.classicremastered.minecraft.mob.ZombieBuilder builder
                        && builder.isTowerResident()
                || !mob.isBaby() && CombatRules.difficulty(mob.level) != Difficulty.EXTREME
                || mob.riding != null || mob.rider != null
                || mob.level.isNetworkMode() || mob.tickCount % 20 != 0
                || mob instanceof Zombie zombie && mob.level.shouldUndeadBurnAt(zombie)
                        && zombie.canBurnInSunlight()
                || !(mob.ai instanceof BasicAttackAI combat) || combat.attackTarget == null)
            return;
        Entity nearest = null;
        float best = 1.2f * 1.2f;
        for (Object candidate : mob.level.findEntities(mob, mob.bb.grow(1.2f, .2f, 1.2f))) {
            if (!(candidate instanceof Entity mount) || !available(mount) || !(mount instanceof Chicken
                    || mount instanceof Pig || mount instanceof Cow || mount instanceof Sheep))
                continue;
            float dx = mount.x - mob.x, dz = mount.z - mob.z;
            float distance = dx * dx + dz * dz;
            if (distance < best && Math.abs(mount.bb.y0 - mob.bb.y0) < .6f) {
                nearest = mount;
                best = distance;
            }
        }
        if (nearest != null)
            mob.mount(nearest);
    }

    public static void tickRider(Mob rider) {
        if (rider instanceof Player player) {
            if (Keyboard.isCreated()
                    && (player.minecraft == null || player.minecraft.currentScreen == null)) {
                int sneak = player.minecraft != null && player.minecraft.settings != null
                        ? player.minecraft.settings.descendKey.key
                        : Keyboard.KEY_LCONTROL;
                if (Keyboard.isKeyDown(sneak) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    player.unmount();
                    return;
                }
            }
            if (player.input != null) {
                if (player.minecraft != null && player.minecraft.currentScreen != null) {
                    player.input.resetKeys();
                    player.input.updateMovement();
                } else {
                    player.input.updateMovement();
                }
            }
        } else if (rider.ai instanceof BasicAttackAI combat) {
            if (combat.attackTarget == rider.riding)
                combat.attackTarget = null;
            combat.tickMountedCombat(rider);
            if (rider instanceof Skeleton skeleton) {
                Entity target = combat.attackTarget;
                skeleton.updateBowAttack(
                        target, target != rider.riding && combat.hasLineOfSight(skeleton, target));
            }
        }
    }

    public static boolean driveMount(Mob mount) {
        if (mount.rider instanceof Player player && player.input != null) {
            float forward = player.input.yya, strafe = player.movementStrafe();
            float speed = (float) Math.hypot(forward, strafe);
            if (speed > .001f) {
                float turn = (float) Math.toDegrees(Math.atan2(-strafe, forward));
                mount.yRot = player.yRot + turn;
            }
            if (player.input.jumping && mount.onGround)
                mount.yd = .42f;
            float slow = player.frostbiteMovementMultiplier();
            mount.travel(0f, speed * slow);
            return true;
        }
        if (mount instanceof Spider && mount.rider instanceof Skeleton skeleton
                && skeleton.ai instanceof BasicAttackAI combat
                && mount.ai instanceof BasicAI movement) {
            steerSpider(mount, skeleton, combat, movement);
            return true;
        }
        if (mount.rider instanceof Mob rider
                && rider.ai instanceof BasicAttackAI combat
                && mount.ai instanceof BasicAI movement) {
            Entity target = combat.attackTarget;
            movement.bind(mount.level, mount);
            if (!validTarget(mount, target)) {
                movement.attackTarget = null;
                movement.clearNavigation();
                mount.travel(0, 0);
                return true;
            }
            movement.attackTarget = target;
            movement.navigateTo(target.x, target.bb.y0, target.z, .85f);
            if (movement.jumping && mount.onGround)
                mount.yd = .42f;
            mount.travel(movement.xxa, movement.yya);
            return true;
        }
        return false;
    }

    private static void steerSpider(
            Mob spider, Skeleton skeleton, BasicAttackAI combat, BasicAI movement) {
        movement.bind(spider.level, spider);
        Entity target = combat.attackTarget;
        if (!validTarget(spider, target)) {
            movement.attackTarget = null;
            movement.clearNavigation();
            spider.travel(0, 0);
            return;
        }
        movement.attackTarget = target;
        if (skeleton.distanceToSqr(target) <= 12 * 12 && combat.hasLineOfSight(skeleton, target)) {
            movement.clearNavigation();
            spider.travel(0, 0);
            return;
        }
        movement.navigateTo(target.x, target.bb.y0, target.z, .85f);
        if (movement.jumping && spider.onGround)
            spider.yd = .42f;
        spider.travel(movement.xxa, movement.yya);
    }

    private static boolean validTarget(Mob mount, Entity target) {
        return target != null
                && target != mount
                && target != mount.rider
                && !target.removed
                && target.bb != null
                && target.level == mount.level
                && (!(target instanceof Mob mob) || mob.health > 0)
                && !CreativeModeHelper.shouldIgnoreTarget(target);
    }
}
