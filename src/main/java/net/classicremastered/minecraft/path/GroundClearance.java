package net.classicremastered.minecraft.path;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;

/** Collision and support checks shared by route search and live route following. */
final class GroundClearance {
    final Level level;
    final float width, height, offset;
    private final boolean swimming;
    final float maxDrop;
    GroundClearance(Level level, float width, float height) {
        this(level, width, height, false);
    }

    GroundClearance(Level level, float width, float height, boolean swimming) {
        this(level, width, height, swimming, 3);
    }

    GroundClearance(Level level, float width, float height, boolean swimming, float maxDrop) {
        this.level = level;
        this.width = Math.max(0.1f, width);
        this.height = Math.max(0.1f, height);
        this.swimming = swimming;
        this.maxDrop = maxDrop;
        offset = Pathfinder.offset(width);
    }

    private boolean hazardous(int id) {
        if (id <= 0)
            return false;
        Block block = Block.blocks[id];
        return block == null || block == Block.FIRE || block == Block.CACTUS || block.getLiquidType() == LiquidType.LAVA
                || !swimming && block.getLiquidType() == LiquidType.WATER;
    }

    boolean bodyClear(float x, float feet, float z) {
        float half = width * 0.5f - 0.002f;
        AABB body = new AABB(x - half, feet + 0.002f, z - half, x + half, feet + height - 0.002f, z + half);
        for (int bx = (int) Math.floor(body.x0); bx <= (int) Math.floor(body.x1); bx++)
            for (int bz = (int) Math.floor(body.z0); bz <= (int) Math.floor(body.z1); bz++)
                for (int by = (int) Math.floor(body.y0); by <= (int) Math.floor(body.y1); by++) {
                    if (!level.isInBounds(bx, by, bz))
                        return false;
                    int id = level.getTile(bx, by, bz);
                    if (hazardous(id))
                        return false;
                    if (id > 0) {
                        AABB collision = Block.blocks[id].getCollisionBox(bx, by, bz);
                        if (collision != null && body.intersectsInner(collision))
                            return false;
                    }
                }
        return true;
    }

    boolean standable(int x, float y, int z) {
        return supportedAt(x + offset, y, z + offset);
    }

    Pathfinder.Node chaseGoal(float x, float feet, float z) {
        int cx = Pathfinder.cell(x, width), cz = Pathfinder.cell(z, width);
        float base = Math.round(feet * 2) / 2f;
        if (swimming)
            for (int y = (int) Math.floor(feet); y <= Math.min(level.depth - 2, feet + 12); y++)
                if (level.isWater(cx, y, cz) && !level.isWater(cx, y + 1, cz) && standable(cx, y + 1, cz))
                    return new Pathfinder.Node(cx, y + 1, cz, null);
        float[] heights = {0, -0.5f, -1, -1.5f, -2, -2.5f, -3, 0.5f, 1};
        for (float dy : heights)
            if (dy <= 0 && standable(cx, base + dy, cz))
                return new Pathfinder.Node(cx, base + dy, cz, null);
        // A target can fit a doorway or stand near a ledge that this body cannot.
        // Select nearby supported ground, rather than treating its airborne or
        // incompatible exact bounding-box position as an unreachable destination.
        Pathfinder.Node best = null;
        float score = Float.POSITIVE_INFINITY;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                for (float dy : heights) {
                    float y = base + dy;
                    if (!standable(cx + dx, y, cz + dz))
                        continue;
                    float gx = cx + dx + offset - x, gz = cz + dz + offset - z;
                    float distance = gx * gx + gz * gz + (y - feet) * (y - feet) * 4f;
                    if (distance < score) {
                        score = distance;
                        best = new Pathfinder.Node(cx + dx, y, cz + dz, null);
                    }
                    break;
                }
            }
        return best;
    }

    boolean supportedAt(float cx, float y, float cz) {
        if (y < 1 || y + height > level.depth + 0.001f)
            return false;
        if (!bodyClear(cx, y, cz))
            return false;
        float half = width * 0.5f - 0.002f;
        int below = (int) Math.floor(y - 0.001f);
        boolean centerSupported = false;
        for (int bx = (int) Math.floor(cx - half); bx <= (int) Math.floor(cx + half); bx++)
            for (int bz = (int) Math.floor(cz - half); bz <= (int) Math.floor(cz + half); bz++) {
                if (!level.isInBounds(bx, below, bz))
                    return false;
                int id = level.getTile(bx, below, bz);
                if (hazardous(id))
                    return false;
                if (id <= 0)
                    continue;
                if (swimming && level.isWater(bx, below, bz) && !level.isWater(bx, (int) Math.floor(y + .001f), bz)) {
                    if (cx >= bx && cx <= bx + 1 && cz >= bz && cz <= bz + 1)
                        centerSupported = true;
                    continue;
                }
                AABB support = Block.blocks[id].getCollisionBox(bx, below, bz);
                if (support != null && Math.abs(support.y1 - y) < 0.01f && cx >= support.x0 && cx <= support.x1
                        && cz >= support.z0 && cz <= support.z1)
                    centerSupported = true;
            }
        // A wide body can overhang a lower tread while its center remains supported.
        // Requiring support below the center still rejects unsupported gaps.
        return centerSupported;
    }

    boolean walkableLine(float x, float feet, float z, float nx, float ny, float nz) {
        if (Math.abs(feet - ny) > 0.12f)
            return false;
        if (!bodyLineClear(x, ny, z, nx, nz))
            return false;
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(nx - x, nz - z) * 5));
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            if (!supportedAt(x + (nx - x) * t, ny, z + (nz - z) * t))
                return false;
        }
        return true;
    }

    boolean bodyLineClear(float x, float feet, float z, float nx, float nz) {
        float half = width * 0.5f - 0.0001f;
        if (!bodyClear(x, feet, z) || !bodyClear(nx, feet, nz))
            return false;
        for (int bx = (int) Math.floor(Math.min(x, nx) - half); bx <= (int) Math.floor(Math.max(x, nx) + half); bx++)
            for (int bz = (int) Math.floor(Math.min(z, nz) - half); bz <= (int) Math
                    .floor(Math.max(z, nz) + half); bz++)
                for (int by = (int) Math.floor(feet + 0.002f); by <= (int) Math.floor(feet + height - 0.002f); by++) {
                    if (!level.isInBounds(bx, by, bz))
                        continue;
                    int id = level.getTile(bx, by, bz);
                    if (id == 0)
                        continue;
                    AABB box = hazardous(id)
                            ? new AABB(bx, by, bz, bx + 1, by + 1, bz + 1)
                            : Block.blocks[id].getCollisionBox(bx, by, bz);
                    if (box == null || box.y1 <= feet + 0.002f || box.y0 >= feet + height - 0.002f)
                        continue;
                    // Sweep the complete footprint, so a glancing corner cannot slip
                    // between point samples and then block the route follower.
                    float enter = 0, leave = 1;
                    float[] start = {x, z}, delta = {nx - x, nz - z}, min = {box.x0 - half, box.z0 - half},
                            max = {box.x1 + half, box.z1 + half};
                    boolean hit = true;
                    for (int axis = 0; axis < 2; axis++) {
                        if (Math.abs(delta[axis]) < 0.000001f) {
                            if (start[axis] <= min[axis] || start[axis] >= max[axis]) {
                                hit = false;
                                break;
                            }
                        } else {
                            float a = (min[axis] - start[axis]) / delta[axis],
                                    b = (max[axis] - start[axis]) / delta[axis];
                            enter = Math.max(enter, Math.min(a, b));
                            leave = Math.min(leave, Math.max(a, b));
                            if (enter >= leave) {
                                hit = false;
                                break;
                            }
                        }
                    }
                    if (hit && leave > 0 && enter < 1)
                        return false;
                }
        return true;
    }

    boolean transition(int x, float y, int z, int nx, float ny, int nz) {
        if (ny - y > 1 || y - ny > maxDrop || !standable(nx, ny, nz))
            return false;
        if (ny < y)
            for (float height = ny; height < y; height += .5f)
                if (!bodyClear(nx + offset, height, nz + offset))
                    return false;
        float upper = Math.max(y, ny);
        // A step needs room to rise before moving across the block edge.
        if (!bodyClear(x + offset, upper, z + offset))
            return false;
        for (int i = 1; i <= 4; i++) {
            float t = i / 4f;
            if (!bodyClear(x + offset + (nx - x) * t, upper, z + offset + (nz - z) * t))
                return false;
        }
        return true;
    }
}
