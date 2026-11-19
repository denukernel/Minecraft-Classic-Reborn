package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

/** Clearance shared by mob routing, steering, and collision. */
public final class MobHazards {
    public static final float CLEARANCE = .15f;
    public static final float LIP_CLEARANCE = .08f;

    private MobHazards() {}

    private static boolean isLava(int id) {
        return id == Block.LAVA.id || id == Block.STATIONARY_LAVA.id;
    }

    private static boolean isHeat(int id) {
        return id == Block.FIRE.id || isLava(id) || id == Block.MAGMA.id;
    }

    public static boolean isHazard(int id) {
        return id == Block.CACTUS.id || isHeat(id);
    }

    public static boolean near(Level level, AABB body, Predicate<Pos> removableFire) {
        for (int x = (int) Math.floor(body.x0 - CLEARANCE); x < body.x1 + CLEARANCE; x++)
            for (int y = (int) Math.floor(body.y0 - LIP_CLEARANCE); y < body.y1; y++)
                for (int z = (int) Math.floor(body.z0 - CLEARANCE); z < body.z1 + CLEARANCE; z++) {
                    int id = level.getTile(x, y, z);
                    if (isHazard(id)
                            && (id != Block.FIRE.id
                                    || removableFire == null
                                    || !removableFire.test(new Pos(x, y, z)))) return true;
                }
        return false;
    }

    public static void addCactusClearance(Level level, AABB sweep, List<AABB> collisions) {
        addClearance(level, sweep, collisions, false, 0, false, true);
    }

    public static void addHorizontalHeatClearance(Level level, AABB body, AABB sweep, List<AABB> collisions, float lift,
            boolean avoidLava) {
        // An entity pushed onto a patch must be able to cross its internal block edges.
        boolean onMagma = level.getTile((int) Math.floor((body.x0 + body.x1) * .5f), (int) Math.floor(body.y0 - .01f),
                (int) Math.floor((body.z0 + body.z1) * .5f)) == Block.MAGMA.id;
        addClearance(level, sweep, collisions, true, LIP_CLEARANCE + lift, onMagma, avoidLava);
    }

    private static void addClearance(Level level, AABB sweep, List<AABB> collisions, boolean heat, float lip,
            boolean onMagma, boolean avoidLava) {
        float pad = CLEARANCE;
        for (int x = (int) Math.floor(sweep.x0 - pad); x < sweep.x1 + pad; x++)
            for (int y = (int) Math.floor(sweep.y0 - lip); y < sweep.y1; y++)
                for (int z = (int) Math.floor(sweep.z0 - pad); z < sweep.z1 + pad; z++) {
                    int id = level.getTile(x, y, z);
                    if (heat ? isHeat(id) && (avoidLava || !isLava(id)) && !(onMagma && id == Block.MAGMA.id)
                            : id == Block.CACTUS.id) {
                        AABB box = new AABB(x - pad, y, z - pad, x + 1 + pad, y + 1 + lip, z + 1 + pad);
                        if (sweep.intersectsInner(box))
                            collisions.add(box);
                    }
                }
    }
}
