package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.SmartHostileAI;

final class Skeleton$1 extends SmartHostileAI {
    public static final long serialVersionUID = 0L;
    final Skeleton parent;
    private boolean rangedStance;
    private int strafeTicks;
    private float strafeDirection = 1;

    Skeleton$1(Skeleton var1) {
        this.parent = var1;
    }

    @Override
    public void tick(Level level, Mob mob) {
        rangedStance = false;
        super.tick(level, mob);

        parent.updateBowAttack(this.attackTarget,
            this.attackTarget != null && hasLineOfSight(mob,this.attackTarget));
    }

    @Override protected boolean navigationAllowed() {
        return !rangedStance && super.navigationAllowed();
    }

    @Override protected void doAttack() {
        super.doAttack();
        if (attackTarget == null || attackTarget.removed || !super.navigationAllowed()
                || !hasLineOfSight(mob,attackTarget)) return;
        float dx=attackTarget.x-mob.x,dz=attackTarget.z-mob.z;
        float distance=(float)Math.hypot(dx,dz);
        if (distance>16 || Math.abs(attackTarget.bb.y0-mob.bb.y0)>5) return;
        rangedStance=true;
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90;
        float turn=yaw-mob.yRot;
        while(turn>180)turn-=360;while(turn<-180)turn+=360;
        mob.yRot+=Math.max(-15,Math.min(15,turn));
        yRotA=0;running=false;jumping=false;
        if (++strafeTicks>=35) {
            strafeTicks=0;if(random.nextBoolean())strafeDirection=-strafeDirection;
        }
        float side=strafeDirection*.5f,forward=distance<7?-.65f:distance>12?.4f:0;
        double angle=Math.toRadians(mob.yRot);
        float sx=(float)Math.cos(angle),sz=(float)Math.sin(angle);
        float fx=-sz,fz=sx;
        if (!safeStep(sx*side,sz*side)) {strafeDirection=-strafeDirection;side=strafeDirection*.5f;}
        if (!safeStep(sx*side,sz*side)) side=0;
        if (!safeStep(fx*forward,fz*forward)) forward=0;
        if (!safeStep(sx*side+fx*forward,sz*side+fz*forward)) side=forward=0;
        xxa=side;yya=forward;
        mob.xd*=.8f;mob.zd*=.8f;
    }

    private boolean safeStep(float dx,float dz) {
        if (dx==0 && dz==0) return true;
        float length=(float)Math.hypot(dx,dz);
        float x=mob.x+dx/length*.7f,z=mob.z+dz/length*.7f;
        int foot=(int)Math.floor(mob.bb.y0-.05f);
        return level.isSolidTile((int)Math.floor(x),foot,(int)Math.floor(z))
            && level.getCubes(mob.bb.cloneMove(x-mob.x,0,z-mob.z)).isEmpty();
    }

    @Override public boolean attack(net.classicremastered.minecraft.Entity target) { return false; }

    @Override
    public void beforeRemove() {
        parent.cancelBowAttack();
    }
}
