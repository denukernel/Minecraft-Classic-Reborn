package net.classicremastered.minecraft.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;
import net.classicremastered.minecraft.player.DeathMessages;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.player.Player;

public final class StickFigure extends Mob {
    public static final float STANDING_HEIGHT = 2f;
    private static final float SWIMMING_HEIGHT = .7f;

    private static final long serialVersionUID = -2256304914053217799L;
    public static final short REGISTRY_ID = 64;
    public static final int MAX_HEALTH = 200;
    public int strikeTicks;
    public boolean kicking;
    public int dodgeTicks;
    private transient float swimAmount, previousSwimAmount;
    private transient java.util.WeakHashMap<net.classicremastered.minecraft.entity.Arrow, Boolean>
            arrowDecisions = new java.util.WeakHashMap<>();

    public boolean isEnraged() {
        return health > 0 && health <= MAX_HEALTH / 4;
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (StickmansHouse.canFightBoss(level, DeathMessages.resolveAttacker(source))) {
            super.hurt(source, damage);
        }
    }

    public int attackDamage(int damage) {
        return switch (CombatRules.difficulty(level)) {
            case VERY_HARD -> Math.round(damage * 1.4f);
            case EXTREME -> Math.round(damage * 1.75f);
            default -> damage;
        };
    }

    public int attackInterval(int ticks) {
        return switch (CombatRules.difficulty(level)) {
            case VERY_HARD -> Math.round(ticks * .8f);
            case EXTREME -> Math.round(ticks * .65f);
            default -> ticks;
        };
    }

    public float getSwimAmount(float partial) {
        return previousSwimAmount + (swimAmount - previousSwimAmount) * partial;
    }

    @Override
    public void knockback(
            net.classicremastered.minecraft.Entity source, int damage, float dx, float dz) {
        // Damage still applies, but impacts never interrupt boss movement.
    }

    /**
     * Decide once per incoming arrow; successful dodges move the body or visibly
     * deflect a blocked shot.
     */
    public boolean evadeArrow(net.classicremastered.minecraft.entity.Arrow arrow) {
        if (arrowDecisions == null)
            arrowDecisions = new java.util.WeakHashMap<>();
        if (health <= 0 || removed || arrow.getOwner() == this || arrowDecisions.containsKey(arrow))
            return false;
        boolean evade = ((net.classicremastered.minecraft.mob.ai.BasicAI) ai).random.nextFloat() < .9f;
        arrowDecisions.put(arrow, evade);
        if (!evade)
            return false;
        float length = (float) Math.hypot(arrow.xd, arrow.zd);
        float sx = length < .001f ? 1 : -arrow.zd / length,
                sz = length < .001f ? 0 : arrow.xd / length;
        boolean moved = false;
        for (int sign : new int[]{1, -1}) {
            float dx = sx * sign * .85f, dz = sz * sign * .85f;
            if (level.getCubes(bb.cloneMove(dx, 0, dz)).isEmpty()
                    && (!onGround
                            || net.classicremastered.minecraft.path.Pathfinder.canWalkStraight(
                                    level, this, x + dx, bb.y0, z + dz))) {
                move(dx, 0, dz);
                xd += sx * sign * .12f;
                zd += sz * sign * .12f;
                moved = true;
                break;
            }
        }
        if (!moved) {
            float vx = arrow.xd;
            arrow.xd = -arrow.zd;
            arrow.zd = vx;
            if (length < .001f) {
                arrow.xd = Math.abs(arrow.yd);
                arrow.yd = 0;
            }
        }
        if (blockMap != null)
            blockMap.moved(this);
        dodgeTicks = 8;
        level.playSound("random/bow", this, .5f, 1.7f);
        return true;
    }

    public StickFigure(Level level, float x, float y, float z) {
        super(level);
        textureName = "/mob/stick-figure.png";
        setSize(.65f, STANDING_HEIGHT);
        heightOffset = 1f;
        bobStrength = 0;
        health = lastHealth = MAX_HEALTH;
        coinDrop = 30;
        equipmentRolled = true;
        ai = new StickFigureAI();
        setPos(x, y, z);
        soundHurt = "random/anvil_land";
        soundDeath = "random/anvil_break";
    }

    @Override
    public void setPos(float x, float y, float z) {
        super.setPos(x, y, z);
        if (bbHeight == SWIMMING_HEIGHT) {
            // Keep the standing position anchor used by saves while shrinking from the top.
            bb.move(0, (SWIMMING_HEIGHT - STANDING_HEIGHT) * .5f, 0);
        }
    }

    @Override
    public boolean isInWater() {
        return touchesWaterSurface();
    }

    private void updateSwimBounds() {
        boolean climbing =
                ai instanceof StickFigureAI special && special.movement().isClimbingBank();
        boolean swimming = isInWater() || climbing && bbHeight == SWIMMING_HEIGHT && !onGround;
        float height = health > 0 && swimming ? SWIMMING_HEIGHT : STANDING_HEIGHT;
        if (bbHeight == height) return;
        if (height == STANDING_HEIGHT) {
            var standing =
                    new net.classicremastered.minecraft.phys.AABB(
                            bb.x0, bb.y0, bb.z0, bb.x1, bb.y0 + height, bb.z1);
            for (Object box : level.getCubes(standing)) {
                if (standing.intersects((net.classicremastered.minecraft.phys.AABB) box)) return;
            }
        }
        setSize(bbWidth, height);
        bb.y1 = bb.y0 + height;
    }

    @Override
    public void tick() {
        updateSwimBounds();
        previousSwimAmount = swimAmount;
        if (ai instanceof StickFigureAI special
                && level.player instanceof net.classicremastered.minecraft.player.Player player
                && net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.getGrabbed(player)
                        == this) special.powers.beginPhysicsGun(this, player);
        if (dodgeTicks > 0) dodgeTicks--;
        if (ai instanceof StickFigureAI special
                && (health <= 0
                        || removed
                        || net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.isHeld(
                                this)
                        || net.classicremastered.minecraft.level.itemstack.GravityGunItem
                                .grabbedMobs
                                .containsValue(this))) special.powers.cancel(this);
        if (strikeTicks > 0) strikeTicks--;
        super.tick();
        updateSwimBounds();
        float target = health > 0 && bbHeight == SWIMMING_HEIGHT ? 1 : 0;
        swimAmount += Math.max(-.2f, Math.min(.16f, target - swimAmount));
        if (health <= 0 && ai instanceof StickFigureAI special) special.powers.cancel(this);
    }

    public static List<StickFigure> nearbyBosses(Level level, Player player) {
        if (level == null || player == null || level.blockMap == null) return List.of();
        List<StickFigure> bosses = new ArrayList<>();
        for (Object entity : level.findEntities(player, player.bb.grow(48, 48, 48))) {
            if (entity instanceof StickFigure boss
                    && !boss.removed
                    && boss.health > 0
                    && player.distanceToSqr(boss) < 48 * 48) {
                bosses.add(boss);
            }
        }
        bosses.sort(Comparator.comparingDouble(player::distanceToSqr));
        return bosses;
    }
}
