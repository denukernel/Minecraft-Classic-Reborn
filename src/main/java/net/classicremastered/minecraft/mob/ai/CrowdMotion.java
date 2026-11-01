package net.classicremastered.minecraft.mob.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.phys.AABB;

/** Local pursuit spacing with bounded pressure, dynamic queues, and per-agent approach points. */
final class CrowdMotion {
    private static final AtomicLong NEXT_ORDER=new AtomicLong();
    private static final AtomicLong NEXT_CLAIM=new AtomicLong();
    private final long order=NEXT_ORDER.incrementAndGet();
    private Entity approachTarget;
    private float approachAngle;
    private float preferredAngle,approachRadius;
    private boolean approachAssigned;
    private long approachClaim;

    float[] approach(BasicAI ai) {
        Mob mob=ai.mob;Entity target=ai.attackTarget;
        if(target==null || Math.hypot(mob.x-target.x,mob.z-target.z)>4.5f) {approachTarget=null;return null;}
        boolean shared=false;
        List<?> nearby=ai.level.findEntities(mob,mob.bb.grow(3,1,3));
        for(Object entity:nearby) {
            if(entity instanceof Mob other && !(other instanceof Player) && active(other)
                    && other.ai instanceof BasicAI peer && peer.attackTarget==target){shared=true;break;}
        }
        if(!shared)return null;
        if(approachTarget!=target) {
            approachTarget=target;
            preferredAngle=(float)((Math.atan2(mob.z-target.z,mob.x-target.x)+order*2.399963229728653)%(2*Math.PI));
            approachAssigned=false;
            approachClaim=0;
        }
        float vertical=target.y-mob.y;
        float radius=Math.min(1.35f,Math.max(0.65f,(float)Math.sqrt(Math.max(0.36f,3.8f-vertical*vertical))-0.15f));
        // Spread only on clear supported ground. Doorways and stair corridors
        // retain their shared center line until there is room to surround a target.
        for(int ring=0;ring<2;ring++)for(int attempt=-1;attempt<8;attempt++) {
            float candidateRadius=radius+ring*0.85f;
            boolean current=attempt==-1;
            if(current && (!approachAssigned || Math.abs(approachRadius-candidateRadius)>0.1f))continue;
            float angle=current?approachAngle:preferredAngle+attempt*(float)(Math.PI/4);
            float x=target.x+(float)Math.cos(angle)*candidateRadius,z=target.z+(float)Math.sin(angle)*candidateRadius;
            if(reserved(mob,target,nearby,x,z,current)
                    || !net.classicremastered.minecraft.path.Pathfinder.hasStandingRoom(ai.level,mob,x,target.bb.y0,z)
                    || !net.classicremastered.minecraft.path.Pathfinder.canWalkStraight(ai.level,mob,x,target.bb.y0,z))continue;
            approachAngle=angle;approachRadius=candidateRadius;approachAssigned=true;
            if(approachClaim==0)approachClaim=NEXT_CLAIM.incrementAndGet();
            return new float[]{x,target.bb.y0,z};
        }
        return null;
    }
    private boolean reserved(Mob mob,Entity target,List<?> nearby,float x,float z,boolean current) {
        for(Object entity:nearby) {
            if(!(entity instanceof Mob other) || other instanceof Player || !active(other)
                    || !(other.ai instanceof BasicAI ai) || ai.attackTarget!=target)continue;
            CrowdMotion peer=ai.crowdMotion();
            if(!peer.approachAssigned || peer.approachTarget!=target)continue;
            if(current && approachClaim!=0 && peer.approachClaim>approachClaim)continue;
            float px=target.x+(float)Math.cos(peer.approachAngle)*peer.approachRadius;
            float pz=target.z+(float)Math.sin(peer.approachAngle)*peer.approachRadius;
            float space=(mob.bbWidth+other.bbWidth)*0.425f;
            if((px-x)*(px-x)+(pz-z)*(pz-z)<space*space)return true;
        }
        return false;
    }

    float forwardFactor(BasicAI ai,float yaw) {
        Mob mob=ai.mob;Entity target=ai.attackTarget;
        if(target==null || Math.hypot(mob.x-target.x,mob.z-target.z)<3)return 1;
        float angle=yaw*(float)Math.PI/180f,fx=-(float)Math.sin(angle),fz=(float)Math.cos(angle);
        float ownRank=rank(mob,target,this),factor=1;
        List<?> nearby=ai.level.findEntities(mob,mob.bb.grow(1.4f,0.5f,1.4f));
        for(Object entity:nearby) {
            if(!(entity instanceof Mob other) || other instanceof Player || !active(other) || !(other.ai instanceof BasicAI peer)
                    || peer.attackTarget!=target)continue;
            // An agent already occupying an attack position must not hold a
            // permanent queue behind it. Soft separation handles passing it.
            if(!peer.pursuingRoute() && !peer.isYieldingToCrowd())continue;
            if(rank(other,target,peer.crowdMotion())>=ownRank)continue;
            float dx=other.x-mob.x,dz=other.z-mob.z;
            float along=dx*fx+dz*fz,lateral=Math.abs(dx*fz-dz*fx);
            float radius=(mob.bbWidth+other.bbWidth)*0.5f;
            if(along<0 || along>radius+0.65f || lateral>radius*0.85f)continue;
            factor=Math.min(factor,Math.max(0,Math.min(1,(along-radius-0.08f)/0.5f)));
        }
        return factor;
    }
    private static float rank(Mob mob,Entity target,CrowdMotion motion) {
        return (float)Math.hypot(mob.x-target.x,mob.z-target.z)+(motion.order%97)*0.002f;
    }
    private static boolean active(Mob mob) {
        return !mob.removed && mob.health>0 && !mob.noPhysics && mob.riding==null;
    }
    void separate(Mob mob,List<?> nearby) {
        if(!active(mob))return;
        float x=0,z=0;
        for(Object entity:nearby) {
            if(!(entity instanceof Mob other) || other instanceof Player || !other.isPushable() || !active(other)
                    || other==mob.rider || mob==other.rider)continue;
            float dx=mob.x-other.x,dz=mob.z-other.z,distance=(float)Math.hypot(dx,dz);
            float space=(mob.bbWidth+other.bbWidth)*0.5f+0.04f;
            if(distance>=space)continue;
            if(distance<0.001f) {
                long peer=other.ai instanceof BasicAI ai?ai.crowdMotion().order:System.identityHashCode(other);
                dx=order<peer?-1:1;dz=0;distance=1;
            } else {dx/=distance;dz/=distance;}
            float force=Math.min(0.025f,Math.max(0,space-(float)Math.hypot(mob.x-other.x,mob.z-other.z))*0.055f);
            x+=dx*force;z+=dz*force;
        }
        float length=(float)Math.hypot(x,z);
        if(length<0.000001f)return;
        if(length>0.045f){x*=0.045f/length;z*=0.045f/length;}
        if(clear(mob,x,z)){mob.xd+=x;mob.zd+=z;}
        else {
            if(clear(mob,x,0))mob.xd+=x;
            if(clear(mob,0,z))mob.zd+=z;
        }
    }
    private static boolean clear(Mob mob,float dx,float dz) {
        AABB next=mob.bb.cloneMove(mob.xd+dx,0,mob.zd+dz);
        for(Object collision:mob.level.getCubes(next))if(next.intersects((AABB)collision))return false;
        return true;
    }
}
