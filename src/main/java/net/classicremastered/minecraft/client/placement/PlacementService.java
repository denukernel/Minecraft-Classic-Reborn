package net.classicremastered.minecraft.client.placement;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;

public final class PlacementService {
    private static final float EPS = 0.001f;

    public static boolean wouldCollideWithPlayer(Entity player, int x, int y, int z) {
        if (player == null || player.bb == null)
            return false;
        AABB block = new AABB(x + EPS, y + EPS, z + EPS, x + 1 - EPS, y + 1 - EPS, z + 1 - EPS);
        return player.bb.intersects(block);
    }

    public static boolean wouldCollideWithMob(Level level, Entity except, int x, int y, int z) {
        AABB block = new AABB(x + EPS, y + EPS, z + EPS, x + 1 - EPS, y + 1 - EPS, z + 1 - EPS);
        java.util.List list = level.findEntities(except, block);
        if (list == null)
            return false;
        for (Object o : list)
            if (o instanceof Mob) {
                Entity e = (Entity) o;
                if (e.bb != null && e.bb.intersects(block))
                    return true;
            }
        return false;
    }
}