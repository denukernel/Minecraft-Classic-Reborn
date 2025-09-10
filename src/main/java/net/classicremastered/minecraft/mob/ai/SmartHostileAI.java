// File: src/com/mojang/minecraft/mob/ai/SmartHostileAI.java
package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.item.Arrow;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.util.MathHelper;

/** Adds simple avoidance on top of BasicAttackAI: dodge arrows, avoid cliffs & TNT. */
public class SmartHostileAI extends BasicAttackAI {
    public static final long serialVersionUID = 0L;

    private int dodgeTicks = 0;
    private int avoidTicks = 0;

    @Override
    protected void update() {
        super.update();
        // super.update() will call doAttack() → we hook in there via overrides
    }

    @Override
    protected void doAttack() {
        // if a big cliff is ahead, don't commit to straight chase
        if (mob != null && level != null) {
            int fx = fi(mob.x + MathHelper.cos(mob.yRot * 0.017453292F));
            int fz = fi(mob.z + MathHelper.sin(mob.yRot * 0.017453292F));
            int y  = fi(mob.y);
            if (wouldFallMoreThan1(fx, y, fz)) {
                // brake & re-orient slightly
                this.running = false;
                this.yya = 0.0f;
                // small yaw nudge
                mob.yRot += (random.nextFloat() - 0.5f) * 20.0f;
            }
        }

        // Arrow dodge: if an arrow within 6 blocks approaches, strafe for a short burst
        if (level != null && mob != null) {
            java.util.List list = level.findEntities(mob, mob.bb.grow(6.0F, 3.0F, 6.0F));
            if (list != null) {
                for (Object o : list) {
                    if (!(o instanceof Arrow)) continue;
                    Arrow a = (Arrow) o;
                    if (a.removed) continue;
                    float ax = a.x - mob.x, az = a.z - mob.z;
                    float v  = MathHelper.sqrt(ax*ax + az*az);
                    if (v < 0.001f) continue;
                    // approximate “coming at me” if arrow is within a forward cone
                    float yawToArrow = (float)(Math.atan2((double)az, (double)ax) * 180.0D / Math.PI) - 90.0F;
                    float dyaw = wrap(yawToArrow - mob.yRot);
                    if (Math.abs(dyaw) < 50.0f) {
                        dodgeTicks = 10 + random.nextInt(8);
                        // pick left/right opposite of dyaw sign
                        this.xxa = (dyaw >= 0 ? -1.0f : 1.0f);
                    }
                }
            }
        }

        // TNT avoidance (static): if TNT block within 3 blocks, back away a bit
        if (level != null && mob != null) {
            int bx = fi(mob.x), by = fi(mob.y), bz = fi(mob.z);
            final int R = 3;
            boolean nearTnt = false;
            for (int dy = -R; dy <= R && !nearTnt; dy++)
                for (int dz = -R; dz <= R && !nearTnt; dz++)
                    for (int dx = -R; dx <= R && !nearTnt; dx++) {
                        int id = level.getTile(bx + dx, by + dy, bz + dz);
                        if (id == Block.TNT.id) nearTnt = true;
                    }
            if (nearTnt) {
                avoidTicks = 12;
                // gentle backpedal
                this.yya = -0.6f;
            }
        }

        // apply dodge burst if active
        if (dodgeTicks > 0) {
            dodgeTicks--;
            this.running = true;
            this.yya = 0.6f;
            // strafe set earlier; small random hop to help break line
            if (mob.onGround && random.nextFloat() < 0.1f) this.jumping = true;
            return;
        }

        // apply TNT avoidance if active
        if (avoidTicks > 0) {
            avoidTicks--;
            this.running = false;
            // small yaw wander while backing up
            mob.yRot += (random.nextFloat() - 0.5f) * 10.0f;
            return;
        }

        // fall back to default attack logic
        super.doAttack();
    }

    // --- tiny helpers (duplicate of private ones in base) ---

    private static int fi(float f) { return (int)Math.floor(f); }

    private boolean wouldFallMoreThan1(int x,int y,int z){
        if (solid(x,y-1,z)) return false;
        if (solid(x,y-2,z)) return false;
        return true;
    }

    private boolean solid(int x,int y,int z){ return level.getTile(x,y,z) != 0; }

    private static float wrap(float a) {
        while (a <= -180f) a += 360f;
        while (a >   180f) a -= 360f;
        return a;
    }
}
