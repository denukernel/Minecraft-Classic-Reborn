// file: src/com/mojang/minecraft/path/PathNavigator.java
package net.classicremastered.minecraft.path;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.util.MathHelper;

public final class PathNavigator {

    private final Level level;

    private int   losSamples    = 12;
    private float closeStop     = 0.25F;
    private float waypointReach = 0.20F;

    private float gx, gy, gz;
    private boolean hasGoal = false;

    private float wx, wy, wz;
    private boolean hasWaypoint = false;

    public PathNavigator(Level level) { this.level = level; }

    public PathNavigator setGoal(float x, float y, float z) {
        this.gx = x; this.gy = y; this.gz = z;
        this.hasGoal = true;
        this.hasWaypoint = false;
        return this;
    }
    public void clearGoal() { hasGoal = false; hasWaypoint = false; }
    public boolean hasGoal() { return hasGoal; }

    public Result steer(Mob mob) {
        if (!hasGoal) return Result.idle();

        float ddx = gx - mob.x, ddz = gz - mob.z;
        if (ddx*ddx + ddz*ddz <= closeStop*closeStop) {
            hasWaypoint = false; return Result.arrived();
        }

        // Go straight if clear and not a >1-block drop
        if (hasLineOfSightFlat(mob.x, mob.y + mob.heightOffset, mob.z, gx, gy, gz)
                && !unsafeDropAhead(mob)) {
            hasWaypoint = false;
            return steerTo(mob, gx, gy, gz, stepUpAhead(mob));
        }

        if (!hasWaypoint || reached(mob, wx, wz)) pickGreedyNeighbor(mob);

        if (!hasWaypoint) return steerTo(mob, gx, gy, gz, stepUpAhead(mob));
        return steerTo(mob, wx, wy, wz, stepUpAhead(mob));
    }

    // ---------- internals ----------
    private boolean reached(Mob mob, float x, float z) {
        float dx = x - mob.x, dz = z - mob.z;
        return (dx*dx + dz*dz) <= (waypointReach*waypointReach);
    }

    private Result steerTo(Mob mob, float tx, float ty, float tz, boolean wantJump) {
        float dx = tx - mob.x, dz = tz - mob.z;
        float yaw = (float)(Math.atan2((double)dz, (double)dx) * 180.0D / Math.PI) - 90.0F;
        return new Result(yaw, true, wantJump);
    }

    private boolean hasLineOfSightFlat(float sx, float sy, float sz, float tx, float ty, float tz) {
        float dx = tx - sx, dy = (ty - 0.2F) - sy, dz = tz - sz;
        for (int i = 1; i <= losSamples; i++) {
            float t = i / (float)losSamples;
            int bx = (int)Math.floor(sx + dx*t);
            int by = (int)Math.floor(sy + dy*t);
            int bz = (int)Math.floor(sz + dz*t);
            if (solid(level.getTile(bx, by, bz)) || solid(level.getTile(bx, by+1, bz))) return false;
        }
        return true;
    }

    private boolean unsafeDropAhead(Mob mob) {
        float yaw = mob.yRot * ((float)Math.PI / 180F);
        int ax = (int)Math.floor(mob.x - MathHelper.sin(yaw) * 0.8F);
        int az = (int)Math.floor(mob.z + MathHelper.cos(yaw) * 0.8F);
        int fy = (int)Math.floor(mob.y + 0.01F);

        int groundY = fy - 1;
        while (groundY > 0 && !solid(level.getTile(ax, groundY, az))) groundY--;
        int drop = (fy - 1) - groundY;
        return drop >= 2; // avoid dropping 2+ blocks
    }

    /** Greedy 8-dir step; allows ±1 Y. Bias upward if goal is ≥2 blocks above. */
    private void pickGreedyNeighbor(Mob mob) {
        int mx = (int)Math.floor(mob.x);
        int my = (int)Math.floor(mob.y + 0.01F);
        int mz = (int)Math.floor(mob.z);

        boolean goalMuchHigher = (gy - my) >= 2.0F;

        float best = Float.POSITIVE_INFINITY;
        int bx=mx, by=my, bz=mz; boolean found=false;

        for (int dy = -1; dy <= 1; dy++) {
            int ny = my + dy;
            // never allow drops >1 in a single step
            if (dy < -1 || dy > 1) continue;

            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx==0 && dz==0) continue;
                int nx = mx + dx, nz = mz + dz;
                if (!walkable(nx, ny, nz)) continue;

                float gxz = Math.abs(gx - (nx + 0.5F)) + Math.abs(gz - (nz + 0.5F));
                float vPenalty = (dy==0 ? 0.0F : 0.9F);

                // If player is much higher, strongly prefer stepping up over flat/down
                if (goalMuchHigher) {
                    if (dy == 1) vPenalty -= 0.8F; // reward up step
                    if (dy == -1) vPenalty += 1.2F; // discourage drops
                } else {
                    if (dy == -1) vPenalty += 0.4F;  // generally discourage down
                }

                float score = gxz + vPenalty;
                if (score < best) { best=score; bx=nx; by=ny; bz=nz; found=true; }
            }
        }

        if (found) { wx = bx+0.5F; wy = by+0.001F; wz = bz+0.5F; hasWaypoint=true; }
        else hasWaypoint=false;
    }

    private boolean walkable(int x, int y, int z) {
        int feet = level.getTile(x, y, z);
        int head = level.getTile(x, y+1, z);
        int ground = level.getTile(x, y-1, z);
        return !solid(feet) && !solid(head) && solid(ground);
    }

    private boolean stepUpAhead(Mob mob) {
        float yaw = mob.yRot * ((float)Math.PI / 180F);
        int x = (int)Math.floor(mob.x - MathHelper.sin(yaw) * 0.8F);
        int z = (int)Math.floor(mob.z + MathHelper.cos(yaw) * 0.8F);
        int y = (int)Math.floor(mob.y + 0.01F);
        int feet = level.getTile(x, y, z);
        int head = level.getTile(x, y+1, z);
        int upHead = level.getTile(x, y+2, z);
        int ground = level.getTile(x, y, z);
        return solid(feet) && !solid(head) && !solid(upHead) && solid(ground);
    }

    private boolean solid(int id) {
        if (id <= 0) return false;
        try { return net.classicremastered.minecraft.level.tile.Block.blocks[id].isSolid(); }
        catch (Throwable t) { return id != 0; }
    }

    // result
    public static final class Result {
        public final float targetYaw; public final boolean forward; public final boolean wantJump;
        private static final Result IDLE=new Result(0,false,false), ARR=new Result(0,false,false);
        private Result(float a, boolean b, boolean c){targetYaw=a; forward=b; wantJump=c;}
        public static Result idle(){return IDLE;} public static Result arrived(){return ARR;}
    }
}
