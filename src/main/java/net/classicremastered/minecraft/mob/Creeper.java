// File: src/com/mojang/minecraft/mob/Creeper.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.particle.Particle;

public class Creeper extends Mob {
    public static final long serialVersionUID = 0L;
    // New fields for leaf transformation
    private boolean isLeafForm = false;
    private int leafTimer = 0; // counts down while creeper is leaves
    private int leafCooldown = 0; // delay before it can turn again
    private String normalTexture = "/mob/creeper.png";

    public Creeper(Level level, float x, float y, float z) {
        super(level);
        this.heightOffset = 1.62F;
        this.modelName = "creeper";
        this.textureName = "/mob/creeper.png";
        this.ai = new net.classicremastered.minecraft.mob.ai.SmartHostileAI();
        this.deathScore = 200;
        this.setPos(x, y, z);
        this.coinDrop = 4;
        this.footSize = 0.45F;

    }

    private int fuseTicks = 0;

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.health <= 0)
            return;

        // === Leaves transform logic (daytime only, one-way) ===
        if (!isLeafForm && leafCooldown == 0 && this.level.shouldUndeadBurnAt(this)) {
            // start 5s countdown before becoming leaves
            isLeafForm = true;
            leafTimer = 100; // 5s @ 20 TPS
            leafCooldown = 200; // prevent retriggering loops
        }

        if (isLeafForm) {
            if (--leafTimer <= 0) {
                // Replace creeper with a leaves block
                int bx = (int) Math.floor(this.x);
                int by = (int) Math.floor(this.y);
                int bz = (int) Math.floor(this.z);

                if (this.level.isInBounds(bx, by, bz)) {
                    int leafId = net.classicremastered.minecraft.level.tile.Block.LEAVES.id;
                    if (this.level.setTile(bx, by, bz, leafId)) {
                        // force decay scheduling
                        this.level.addToTickNextTick(bx, by, bz, leafId);
                    }
                }

                this.remove(); // creeper gone forever
            }
            return; // no AI while in transform countdown
        }

        if (leafCooldown > 0) {
            leafCooldown--;
        }

        // === Explosion fuse logic (disabled in Creative) ===
        if (this.level.creativeMode)
            return;

        Difficulty diff = (level.minecraft != null && level.minecraft.settings != null)
                ? level.minecraft.settings.difficulty
                : Difficulty.NORMAL;

        boolean trigger = false;
        float radius = 3.0f;

        switch (diff) {
        case EASY:
            trigger = this.hurtTime > 0;
            radius = 3.0f;
            break;
        case NORMAL:
            trigger = nearPlayer(3.0f);
            radius = 3.4f;
            break;
        case HARD:
            trigger = nearPlayer(5.0f);
            radius = 6.0f;
            break;
        default:
            return;
        }

        if (trigger) {
            if (++fuseTicks > 40)
                explode(radius); // ~2s fuse
        } else {
            fuseTicks = 0;
        }
    }

    private boolean nearPlayer(float dist) {
        // In Creative worlds we already early-return in tick(), but keep this safe:
        if (this.level.creativeMode)
            return false;
        net.classicremastered.minecraft.player.Player p = this.level.getNearestPlayer(this.x, this.y, this.z, dist);
        return p != null && !p.removed && p.health > 0;
    }

    private void explode(float radius) {
        if (this.level == null || this.level.creativeMode)
            return; // guard
        this.level.explode(this, this.x, this.y, this.z, radius);
        this.remove();
    }
}
