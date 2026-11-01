package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public class Skeleton extends Zombie {
    public static final long serialVersionUID = 0L;
    public static final int BOW_DRAW_TICKS = 16;
    public static final int BOW_RECOVERY_TICKS = 4;
    private boolean drawingBow;
    private int bowDrawTicks, previousBowDrawTicks, bowRecoveryTicks;
    private transient Entity bowTarget;
    public Skeleton(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.modelName   = "skeleton";
        this.textureName = "/mob/skeleton.png";
        this.ai          = new Skeleton$1(this); // use skeleton AI
        this.soundIdle  = "mob/skeleton/say";
        this.soundHurt  = "mob/skeleton/hurt";
        this.soundDeath = "mob/skeleton/death";
        this.coinDrop = 2;
    }

    public void setDrawingBow(boolean flag) {
        drawingBow=flag;bowDrawTicks=previousBowDrawTicks=flag?BOW_DRAW_TICKS:0;
    }
    public boolean isDrawingBow() { return drawingBow; }
    public float getBowDrawProgress(float partial) {
        return drawingBow ? Math.min(1,(previousBowDrawTicks+(bowDrawTicks-previousBowDrawTicks)*partial)/BOW_DRAW_TICKS) : 0;
    }
    public String getBowTexture() {
        if(!drawingBow)return "/items/bow.png";
        float f=getBowDrawProgress(1);
        return f>.75f?"/items/bow_pulling_2.png":f>.4f?"/items/bow_pulling_1.png":"/items/bow_pulling_0.png";
    }
    public float getBowAimYaw() {
        return bowTarget==null?yRot:(float)Math.toDegrees(Math.atan2(bowTarget.z-z,bowTarget.x-x))-90;
    }
    public float getBowAimPitch() {
        if(bowTarget==null)return xRot;
        double dx=bowTarget.x-x,dz=bowTarget.z-z;
        double dy=(bowTarget.bb.y0+bowTarget.bb.y1)*.5-(bb.y0+bbHeight*.8f);
        return (float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
    }
    public void cancelBowAttack() {
        drawingBow=false;bowDrawTicks=previousBowDrawTicks=bowRecoveryTicks=0;bowTarget=null;
    }
    public void updateBowAttack(Entity target,boolean visible) {
        previousBowDrawTicks=bowDrawTicks;
        if(health<=0 || removed || level==null || level.isNetworkMode() || target==null || target.removed
            || target instanceof Mob m && m.health<=0 || CreativeModeHelper.shouldIgnoreTarget(target)
            || !visible || distanceTo(target)>32 || net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.isHeld(this)) {
            cancelBowAttack();return;
        }
        bowTarget=target;
        if(bowRecoveryTicks>0) {bowRecoveryTicks--;drawingBow=false;bowDrawTicks=0;return;}
        drawingBow=true;
        if(++bowDrawTicks>=BOW_DRAW_TICKS) {
            shootArrow(level,target);
            drawingBow=false;bowDrawTicks=previousBowDrawTicks=0;bowRecoveryTicks=BOW_RECOVERY_TICKS;
        }
    }
    @Override public void tick() {
        super.tick();
        if(health<=0 || removed || net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.isHeld(this)) cancelBowAttack();
    }
    public void shootArrow(Level lvl) {
        Entity target=bowTarget;
        if(target==null && ai instanceof BasicAI basic)target=basic.attackTarget;
        shootArrow(lvl,target);
    }
    public void shootArrow(Level lvl,Entity target) {
        if(lvl==null || lvl.isNetworkMode() || health<=0 || removed || target==null || target.removed)return;
        double dx=target.x-x,dz=target.z-z,horizontal=Math.max(.001,Math.hypot(dx,dz));
        float sx=x+(float)(dx/horizontal*.5),sy=bb.y0+bbHeight*.8f,sz=z+(float)(dz/horizontal*.5);
        float tx=target.x,tz=target.z,ty=(target.bb.y0+target.bb.y1)*.5f;
        double leadX=Math.max(-.35,Math.min(.35,target.x-target.xo));
        double leadZ=Math.max(-.35,Math.min(.35,target.z-target.zo));
        double time=Math.max(1,Math.sqrt((tx-sx)*(tx-sx)+(ty-sy)*(ty-sy)+(tz-sz)*(tz-sz))/1.2);
        double vx=0,vy=0,vz=0;
        // Match Arrow.tick: drag first, then gravity, then movement. Predict modest lateral motion.
        for(int i=0;i<8;i++) {
            double dragSum=.998*(1-Math.pow(.998,time))/.002;
            double fall=.02*(time-dragSum)/.002;
            vx=(tx+leadX*time-sx)/dragSum;vz=(tz+leadZ*time-sz)/dragSum;vy=(ty-sy+fall)/dragSum;
            time=Math.max(1,Math.min(50,time*Math.sqrt(vx*vx+vy*vy+vz*vz)/1.2));
        }
        float yaw=(float)Math.toDegrees(Math.atan2(vx,-vz));
        float pitch=(float)-Math.toDegrees(Math.atan2(vy,Math.hypot(vx,vz)));
        Arrow arrow=new Arrow(lvl,this,sx,sy,sz,yaw,pitch,1);
        arrow.setPos(sx,sy,sz);arrow.xo=sx;arrow.yo=sy;arrow.zo=sz;
        arrow.xd=(float)vx;arrow.yd=(float)vy;arrow.zd=(float)vz;
        lvl.addEntity(arrow);
        lvl.playSound("random/bow",this,.8f,1.0f);
    }
    @Override
    public void die(net.classicremastered.minecraft.Entity killer) {
        super.die(killer);
        if (level == null || level.isNetworkMode()) return;
        net.classicremastered.minecraft.entity.DroppedItem salt = new net.classicremastered.minecraft.entity.DroppedItem(
            level,
            this.x + (level.random.nextFloat() - 0.5F),
            this.y + 0.5F,
            this.z + (level.random.nextFloat() - 0.5F),
            net.classicremastered.minecraft.level.itemstack.Item.BAG_OF_SALT.id
        );
        level.addEntity(salt);
    }
}

