package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.Player;

/** Shared navigation for home travel, wandering, fleeing, and hostile villagers. */
public class VillagerAI extends BasicAI {
    private static final long serialVersionUID=0L;
    public float homeX,homeY,homeZ;
    public float homeRadius=3f;
    public boolean enforceInsideAtNight=true;
    private boolean freeWander,returningHome;
    @Override protected boolean usesWanderNavigation(){return false;}
    @Override protected boolean usesPlannedWandering(){return freeWander;}
    public VillagerAI(float x,float y,float z,float radius) {
        homeX=x;homeY=y;homeZ=z;homeRadius=radius;
        runSpeed=0.6f;walkSpeed=0.1f;
    }
    @Override protected void update() {
        freeWander=false;
        xxa=yya=yRotA=0;jumping=running=false;
        if(level==null || !(mob instanceof Villager villager))return;
        boolean home=villager.hasHome;
        if(home) {
            homeX=villager.homeX;homeY=villager.homeY;homeZ=villager.homeZ;homeRadius=villager.homeRadius;
        }
        if(checkAndPerformFlee())return;
        if(villager.isHostile()) {
            runSpeed=1.2f;walkSpeed=0.3f;
            if(home && Math.abs(mob.x-homeX)<homeRadius && Math.abs(mob.z-homeZ)<homeRadius) {
                float separation=(villager.hashCode()%3-1)*0.8f;
                int x=(int)(homeX+separation),y=(int)homeY,z=(int)(homeZ-homeRadius-0.5f);
                int id=level.getTile(x,y,z);
                if(id!=0 && Block.blocks[id]!=null && Block.blocks[id]!=Block.BEDROCK) {
                    level.setTile(x,y,z,0);
                    if(level.minecraft!=null && level.minecraft.hud!=null)level.minecraft.hud.addChat("&4A villager smashes the door!");
                }
                navigateTo(x+0.5f,homeY-1,z+0.5f,runSpeed);
                return;
            }
            Player target=level.getNearestPlayer(mob.x,mob.y,mob.z,40);
            if(target!=null && target.health>0 && !net.classicremastered.minecraft.util.CreativeModeHelper.shouldIgnoreTarget(target)) {
                navigateTo(target.x,target.bb.y0,target.z,runSpeed);return;
            }
        } else if(home) {
            float dx=homeX-mob.x,dz=homeZ-mob.z,distance=dx*dx+dz*dz;
            boolean night=enforceInsideAtNight && !level.isDaytime();
            if(distance>homeRadius*homeRadius*(night?1f:1.6f))returningHome=true;
            if(distance<homeRadius*homeRadius*0.5f)returningHome=false;
            if(returningHome) {navigateTo(homeX,homeY-1,homeZ,runSpeed);return;}
            if(night)return;
        }
        freeWander=true;
        super.update();
    }
}