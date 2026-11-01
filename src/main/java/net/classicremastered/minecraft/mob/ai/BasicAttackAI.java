package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Target acquisition and melee combat; shared BasicAI navigation owns route steering. */
public class BasicAttackAI extends BasicAI {
    public static final long serialVersionUID = 0L;
    public int damage = 6;

    @Override protected void update() {
        super.update();
        if (mob == null || level == null || mob.health <= 0) return;
        doAttack();
    }

    protected void doAttack() {
        if (mob == null || level == null) return;
        if (attackTarget != null && (!isAliveEntity(attackTarget) || CreativeModeHelper.shouldIgnoreTarget(attackTarget))) attackTarget = null;
        Entity player = level.getPlayer();
        if (attackTarget == null && isAliveEntity(player) && !CreativeModeHelper.shouldIgnoreTarget(player)) {
            float dx=player.x-mob.x,dy=player.y-mob.y,dz=player.z-mob.z;
            if(dx*dx+dy*dy+dz*dz<16f*16f) attackTarget=player;
        }
        if (attackTarget == null) { running=false; runMultiplier=1f; return; }
        float dx=attackTarget.x-mob.x,dy=attackTarget.y-mob.y,dz=attackTarget.z-mob.z;
        if(dx*dx+dy*dy+dz*dz>32f*32f) {attackTarget=null;running=false;return;}
        mob.xRot=Math.max(-60f,Math.min(60f,(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))));
        running=true; runMultiplier=1.1f;
        if(dx*dx+dy*dy+dz*dz<4f && attackDelay==0 && hasLineOfSight(mob,attackTarget)) attack(attackTarget);
    }

    public boolean attack(Entity target) {
        if (this.level == null || this.mob == null) return false;
        if (net.classicremastered.minecraft.util.CreativeModeHelper.isCreativePlayer(target)) return false;
        if (!hasLineOfSight(this.mob, target)) return false;

        this.mob.attackTime = 5;
        this.attackDelay = this.random.nextInt(20) + 10;

        int dmgRoll = (int)((this.random.nextFloat() + this.random.nextFloat()) / 2.0F * (float)this.damage + 1.0F);
        target.hurt(this.mob, dmgRoll);
        this.noActionTime = 0;
        return true;
    }

    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (this.level == null || this.mob == null) return;
        if (net.classicremastered.minecraft.util.CreativeModeHelper.isCreativePlayer(src)) return;

        if (src instanceof Arrow) {
            src = ((Arrow)src).getOwner();
        }
        if (src != null && !src.getClass().equals(this.mob.getClass())) {
            this.attackTarget = src;
        }
    }

    private static boolean isAliveEntity(Entity e) {
        if (e == null) return false;
        if (e instanceof net.classicremastered.minecraft.player.Player) {
            return ((net.classicremastered.minecraft.player.Player) e).health > 0 && !e.removed;
        }
        if (e instanceof net.classicremastered.minecraft.mob.Mob) {
            return ((net.classicremastered.minecraft.mob.Mob) e).health > 0 && !e.removed;
        }
        return !e.removed;
    }

    protected boolean hasLineOfSight(Entity from, Entity to) {
        if (from == null || to == null || level == null) return false;
        float fromFeetY = from.y - from.heightOffset;
        float toFeetY   = to.y - to.heightOffset;
        float fromEyeY  = fromFeetY + (from.bbHeight > 0 ? from.bbHeight * 0.85f : 0.9f);
        float toMidY    = toFeetY + (to.bbHeight > 0 ? to.bbHeight * 0.50f : 0.5f);
        Vec3D a = new Vec3D(from.x, fromEyeY, from.z);
        Vec3D b = new Vec3D(to.x,   toMidY,   to.z);
        return level.clip(a, b) == null;
    }
}
