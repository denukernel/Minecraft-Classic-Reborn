package net.classicremastered.minecraft.mob.ai.stickfigure;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.AI;
import net.classicremastered.minecraft.model.Vec3D;

/**
 * Temporary ballistic motion for an actual passive mob, with its normal AI
 * restored on landing.
 */
public final class BossThrownMobAI extends AI {

    private final AI original;
    private final Mob owner;
    private final Entity target;
    private int age;
    public BossThrownMobAI(Mob mob, Mob owner, Entity target) {
        original = mob.ai;
        this.owner = owner;
        this.target = target;
    }

    @Override
    public void tick(Level level, Mob mob) {
        if (mob.health <= 0) {
            mob.ai = original;
            return;
        }
        Vec3D start = new Vec3D(mob.x, mob.y, mob.z), end = start.add(mob.xd, mob.yd, mob.zd);
        var wall = level.clip(new Vec3D(start.x, start.y, start.z), end, true);
        var hit = target != null && !target.removed && target.level == level
                ? target.bb.grow(mob.bbWidth * .5f, mob.bbHeight * .5f, mob.bbWidth * .5f).clip(start, end)
                : null;
        if (hit != null && (wall == null || start.distance(hit.vec) < start.distance(wall.vec))) {
            target.hurt(owner, 4);
            mob.ai = original;
            mob.xd *= .2f;
            mob.zd *= .2f;
            return;
        }
        mob.travel(0, 0);
        mob.fallDistance = 0;
        if (!mob.isInWater() && !mob.isInLava()) {
            mob.xd *= .99f / .91f;
            mob.zd *= .99f / .91f;
        }
        if (++age > 80 || mob.onGround || mob.horizontalCollision || owner.removed || owner.health <= 0)
            mob.ai = original;
    }
}
