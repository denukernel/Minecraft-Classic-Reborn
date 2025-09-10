// File: src/net/classicremastered/minecraft/mob/IronGolem.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class IronGolem extends Mob {

    public IronGolem(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);

        this.modelName   = "irongolem";             // mapped in ModelManager
        this.textureName = "/mob/iron_golem.png";   // 128×128 texture atlas

        this.health      = 100;                     // tanky mob
        this.coinDrop    = 5;                       // optional reward
        this.bbWidth     = 1.4F;                    // wider hitbox
        this.bbHeight    = 2.9F;                    // taller than player
        this.footSize    = 0.9F;                    // selection radius
        this.speed       = 0.20F;                   // slower than player
        this.heightOffset = 1.0F;                   // eye offset
        this.hurtPitchMin = 0.3F;
        this.hurtPitchMax = 0.3F;
        this.soundHurt   = "random/anvil_land";
        this.soundDeath  = "random/anvil_break";
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (level == null || level.minecraft == null) return;
        Player p = level.minecraft.player;
        if (p == null || p.removed || p.health <= 0) return;

        // Ignore Creative players
        if (level.creativeMode) return;

        float dist = this.distanceTo(p);

        // Always hostile (classic-style)
        if (dist < 16.0f) {
            float dx = p.x - this.x;
            float dz = p.z - this.z;
            float mag = (float) Math.sqrt(dx * dx + dz * dz);
            if (mag > 0.001f) {
                this.xd += (dx / mag) * 0.05f;
                this.zd += (dz / mag) * 0.05f;
            }

            // Rotate to face player
            this.yRot = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        }

        // Attack if close
        if (dist < 3.0f && this.attackTime == 0) {
            // Slow attack: 2s cooldown (40 ticks)
            this.attackTime = 40;

            // Deal damage
            p.hurt(this, 10);

            // Knockback punch
            float dx = p.x - this.x;
            float dz = p.z - this.z;
            float mag = (float)Math.sqrt(dx * dx + dz * dz);
            if (mag > 0.001f) {
                dx /= mag;
                dz /= mag;

                p.xd += dx * 1.0F;
                p.yd += 0.5F;
                p.zd += dz * 1.0F;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public void die(Entity killer) {
        super.die(killer);
        // Classic placeholder: no item drop yet
    }
}
