package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;

public final class StickFigure extends Mob {

    private static final long serialVersionUID = -2256304914053217799L;
    public static final short REGISTRY_ID = 64;
    public static final int MAX_HEALTH = 200;
    public int strikeTicks;
    public boolean kicking;
    public int dodgeTicks;
    private transient float swimAmount, previousSwimAmount;
    private transient int swimDryTicks;
    private transient java.util.WeakHashMap<net.classicremastered.minecraft.entity.Arrow, Boolean> arrowDecisions = new java.util.WeakHashMap<>();

    public boolean isEnraged() {
        return health > 0 && health <= MAX_HEALTH / 4;
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

    @Override
    public void render(net.classicremastered.minecraft.render.TextureManager tm, float partial) {
        super.render(tm, partial);
        if (ai instanceof StickFigureAI special)
            special.powers.renderPhysicsGun(this, partial);
        if (isEnraged() && !removed)
            net.classicremastered.minecraft.render.RageAuraRenderer.render(this, partial);
    }

    public StickFigure(Level level, float x, float y, float z) {
        super(level);
        modelName = "stickfigure";
        textureName = "/mob/stick-figure.png";
        setSize(.65f, 2f);
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
    public void tick() {
        previousSwimAmount = swimAmount;
        if (ai instanceof StickFigureAI special
                && level.player instanceof net.classicremastered.minecraft.player.Player player
                && net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.getGrabbed(player) == this)
            special.powers.beginPhysicsGun(this, player);
        if (dodgeTicks > 0)
            dodgeTicks--;
        if (ai instanceof StickFigureAI special
                && (health <= 0
                        || removed
                        || net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.isHeld(
                                this)
                        || net.classicremastered.minecraft.level.itemstack.GravityGunItem.grabbedMobs
                                .containsValue(this)))
            special.powers.cancel(this);
        if (strikeTicks > 0)
            strikeTicks--;
        super.tick();
        if (isInWater())
            swimDryTicks = 6;
        else if (swimDryTicks > 0)
            swimDryTicks--;
        float target = swimDryTicks > 0 && (!onGround || isInWater()) ? 1 : 0;
        swimAmount += Math.max(-.12f, Math.min(.12f, target - swimAmount));
        if (health <= 0 && ai instanceof StickFigureAI special)
            special.powers.cancel(this);
    }

    public static StickFigure nearestBoss(
            Level level, net.classicremastered.minecraft.player.Player player) {
        if (level == null || player == null)
            return null;
        StickFigure boss = null;
        float best = 48;
        for (Object o : level.findEntities(player, player.bb.grow(48, 48, 48)))
            if (o instanceof StickFigure s && !s.removed && s.health > 0) {
                float distance = player.distanceTo(s);
                if (distance < best) {
                    boss = s;
                    best = distance;
                }
            }
        return boss;
    }
}
