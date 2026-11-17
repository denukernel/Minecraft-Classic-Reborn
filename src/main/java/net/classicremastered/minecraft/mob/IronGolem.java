// File: src/net/classicremastered/minecraft/mob/IronGolem.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;

public final class IronGolem extends Mob {
    private static final long serialVersionUID = -6818494539615206007L;
    public static final float HEIGHT = 2.9f;

    public boolean builtByPlayer;

    public IronGolem(Level level, float x, float y, float z) {
        super(level);

        this.modelName = "irongolem"; // mapped in ModelManager
        this.textureName = "/mob/iron_golem.png"; // 128×128 texture atlas

        this.health = this.lastHealth = 100;
        this.coinDrop = 5;
        setSize(1.4f, HEIGHT);
        this.footSize = 0.9F;
        this.speed = 0.20F;
        this.heightOffset = 1.0F;
        this.hurtPitchMin = 0.3F;
        this.hurtPitchMax = 0.3F;
        this.soundHurt = "random/anvil_land";
        this.soundDeath = "random/anvil_break";
        setPos(x, y, z);
    }

    @Override
    public boolean isPersistenceRequired() {
        return builtByPlayer || super.isPersistenceRequired();
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putBoolean("BuiltByPlayer", builtByPlayer);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        builtByPlayer = tag.getBoolean("BuiltByPlayer");
    }

    @Override
    public void aiStep() {
        if (level == null) return;
        if (this.health <= 0) return;
        Entity target = null;
        if (builtByPlayer) {
            target = findNearestHostile(32.0f);
        } else {
            Entity previous =
                    ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic
                            ? basic.attackTarget
                            : null;
            target =
                    net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearest(
                            this, 32, previous);
        }
        if (ai instanceof BasicAI basic) basic.attackTarget = target;
        super.aiStep();
        if (ai instanceof BasicAI basic
                && basic.attackTarget instanceof Mob victim
                && !victim.removed
                && victim.health > 0) attackNearby(victim);
    }

    // --- helper: move toward & attack any entity ---
    private void attackNearby(Entity e) {
        float dx = e.x - this.x;
        float dz = e.z - this.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < 2.0f
                && this.attackTime == 0
                && e.bb.y1 > bb.y0 - .2f
                && e.bb.y0 < bb.y1 + .2f
                && HostileTargets.hasLineOfSight(this, e)) {
            this.attackTime = 40; // 2s cooldown
            e.hurt(this, 10);

            if (dist > 0.0f) {
                dx /= dist;
                dz /= dist;
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
            if (!(o instanceof Mob m) || m instanceof Player || m.corruptedToDefendPlayer) continue;
            if (!(m instanceof Zombie
                    || m instanceof Skeleton
                    || m instanceof Creeper
                    || m instanceof Spider
                    || m instanceof Enderman
                    || m instanceof BabyZombie
                    || m instanceof IronGolem golem && !golem.builtByPlayer)) {
                continue; // only hostiles
            }

            if (m.removed || m.health <= 0) continue;

            float dx = m.x - this.x;
            float dy = m.y - this.y;
            float dz = m.z - this.z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDist2 && HostileTargets.hasLineOfSight(this, m)) {
                bestDist2 = d2;
                best = m;
            }
        }
        return best;
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
    }
}
