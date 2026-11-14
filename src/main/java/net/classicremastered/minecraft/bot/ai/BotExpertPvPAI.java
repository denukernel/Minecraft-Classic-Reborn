package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;

public class BotExpertPvPAI extends BotPvPAI {
    private int nextBuildTick;

    public BotExpertPvPAI(Mob bot) {
        super(bot, 2f, 8, true);
    }

    @Override
    protected boolean tryBuild(float rise, float horizontalDistanceSquared) {
        if (!mob.onGround
                || rise < 1.5f
                || horizontalDistanceSquared > 5f * 5f
                || mob.tickCount < nextBuildTick
                || level.isNetworkMode()) return false;

        int x = (int) Math.floor(mob.x);
        int y = (int) Math.floor(mob.bb.y0 + .00001f);
        int z = (int) Math.floor(mob.z);
        if (!level.isInBounds(x, y, z)
                || level.getTile(x, y, z) != 0
                || !level.isSolidTile(x, y - 1, z)) return false;

        float lift = y + 1f - mob.bb.y0;
        AABB sweptBounds = mob.bb.expand(0, lift, 0);
        sweptBounds.y0 += .00001f;
        AABB stoneBounds = Block.COBBLESTONE.getCollisionBox(x, y, z);
        if (sweptBounds.y1 > level.depth
                || !level.getCubes(sweptBounds).isEmpty()
                || !level.findEntities(mob, sweptBounds).isEmpty()
                || !level.findEntities(mob, stoneBounds).isEmpty()
                || !level.setTile(x, y, z, Block.COBBLESTONE.id)) return false;

        mob.xd = mob.yd = mob.zd = 0;
        mob.move(0, lift, 0);
        nextBuildTick = mob.tickCount + 10;
        return true;
    }
}
