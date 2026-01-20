// File: src/net/classicremastered/minecraft/mob/IronGolem.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class IronGolem extends Mob {

    public boolean builtByPlayer = false; // added

    public IronGolem(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);

        this.modelName   = "irongolem";             // mapped in ModelManager
        this.textureName = "/mob/iron_golem.png";   // 128×128 texture atlas

        this.health      = 100;
        this.coinDrop    = 5;
        this.bbWidth     = 1.4F;
        this.bbHeight    = 2.9F;
        this.footSize    = 0.9F;
        this.speed       = 0.20F;
        this.heightOffset = 1.0F;
        this.hurtPitchMin = 0.3F;
        this.hurtPitchMax = 0.3F;
        this.soundHurt   = "random/anvil_land";
        this.soundDeath  = "random/anvil_break";
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level == null) return;
        if (this.health <= 0) return;

        if (builtByPlayer) {
            // Defender mode: attack hostile mobs
            Mob target = findNearestHostile(16.0f);
            if (target != null) {
                moveTowardAndAttack(target);
            }
        } else {
            // Hostile mode: attack player
            Player p = level.minecraft != null ? level.minecraft.player : null;
            if (p == null || p.removed || p.health <= 0) return;
            if (level.creativeMode) return;
            moveTowardAndAttack(p);
        }
    }

    // --- helper: move toward & attack any entity ---
    private void moveTowardAndAttack(Entity e) {
        float dx = e.x - this.x;
        float dz = e.z - this.z;
        float dist = (float)Math.sqrt(dx*dx + dz*dz);

        if (dist > 0.001f) {
            this.xd += (dx / dist) * 0.05f;
            this.zd += (dz / dist) * 0.05f;
        }
        this.yRot = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

        if (dist < 3.0f && this.attackTime == 0) {
            this.attackTime = 40; // 2s cooldown
            e.hurt(this, 10);

            if (dist > 0.0f) {
                dx /= dist; dz /= dist;
                e.xd += dx * 1.0F;
                e.yd += 0.5F;
                e.zd += dz * 1.0F;
            }
        }
    }

    // --- helper: find nearest hostile mob ---
    private Mob findNearestHostile(float radius) {
        if (level.blockMap == null || level.blockMap.all == null) return null;
        float bestDist2 = radius * radius;
        Mob best = null;

        for (Object o : level.blockMap.all) {
            if (!(o instanceof Mob)) continue;
            if (o instanceof Player) continue;

            Mob m = (Mob) o;
            if (!(m instanceof Zombie || m instanceof Skeleton || m instanceof Creeper ||
                  m instanceof Spider || m instanceof Enderman || m instanceof BabyZombie)) {
                continue; // only hostiles
            }

            if (m.removed || m.health <= 0) continue;

            float dx = m.x - this.x;
            float dy = m.y - this.y;
            float dz = m.z - this.z;
            float d2 = dx*dx + dy*dy + dz*dz;
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = m;
            }
        }
        return best;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return true; }

    @Override
    public void die(Entity killer) {
        super.die(killer);
    }
}
