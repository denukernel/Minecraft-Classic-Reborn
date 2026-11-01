package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;

/** Player following through the same grounded navigator used by combat mobs. */
public class PathChaseAI extends BasicAI {
    public static final long serialVersionUID = 0L;
    public float chaseRange = 32f;
    @Override protected void update() {
        Player target=level.getNearestPlayer(mob.x,mob.y,mob.z,chaseRange);
        if(target==null || target.removed || target.health<=0 || CreativeModeHelper.shouldIgnoreTarget(target)) {
            xxa=yya=yRotA=0;jumping=false;return;
        }
        navigateTo(target.x,target.bb.y0,target.z,runSpeed);
        mob.xRot=defaultLookAngle;
    }
    @Override protected boolean usesWanderNavigation(){return false;}
}
