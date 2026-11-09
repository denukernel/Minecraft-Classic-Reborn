package net.classicremastered.minecraft.mob.ai.goal.enderman;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

public final class EndermanTeleportation {

    private final EndermanAI ai;
    public EndermanTeleportation(EndermanAI ai) {
        this.ai = ai;
    }

    void performTeleport(Enderman mob, float cx, float cy, float cz, int cooldown) {
        if (mob == null)
            return;
        mob.setPos(cx, cy, cz);
        mob.xd = mob.yd = mob.zd = 0;

        // unify cooldown + FX
        ai.teleportCooldown = cooldown;
        spawnTeleportParticles(mob);
        // Optional: play a sound here if you expose one (e.g., "enderman.teleport")
    }

    boolean teleportToShade(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        float ox = mob.x, oy = mob.y, oz = mob.z;

        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);
            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F, cy = ty, cz = tz + 0.5F;
            AABB candidate = buildCandidateBB(mob, cx, cy, cz);
            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            // Probe: place, test burn; if safe, commit with proper FX
            mob.setPos(cx, cy, cz);
            if (!lvl.shouldUndeadBurnAt(mob)) {
                performTeleport(mob, cx, cy, cz, 40);
                return true;
            }
        }

        // restore if nothing worked
        mob.setPos(ox, oy, oz);
        return false;
    }

    boolean teleportOutOfWater(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 0);
            return true;
        }
        return false;
    }

    static boolean candidateIntersectsLiquid(Level lvl, AABB candidate) {
        if (lvl == null || candidate == null)
            return true;
        return lvl.containsAnyLiquid(candidate);
    }

    static AABB buildCandidateBB(Enderman mob, float cx, float cy, float cz) {
        if (mob.bb == null) {
            float half = 0.6f;
            return new AABB(cx - half, cy, cz - half, cx + half, cy + 2.9f, cz + half);
        }
        float halfX = (mob.bb.x1 - mob.bb.x0) * 0.5f;
        float halfZ = (mob.bb.z1 - mob.bb.z0) * 0.5f;
        float height = mob.bb.y1 - mob.bb.y0;
        float bottomY = cy;
        final float EPS = 0.01f;
        return new AABB(cx - halfX + EPS, bottomY + EPS, cz - halfZ + EPS, cx + halfX - EPS, bottomY + height - EPS,
                cz + halfZ - EPS);
    }

    boolean teleportNearPlayer(Enderman mob, Player player) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        for (int tries = 0; tries < 12; tries++) {
            int tx = (int) (player.x + (lvl.random.nextFloat() - 0.5F) * 16);
            int tz = (int) (player.z + (lvl.random.nextFloat() - 0.5F) * 16);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float dx = cx - player.x;
            float dz = cz - player.z;
            if (dx * dx + dz * dz < 9.0F) {
                continue;
            }
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 40);
            return true;
        }
        return false;
    }

    public boolean teleportRandom(Enderman mob) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        if (mob.isInWater() || (mob.bb != null && lvl.containsAnyLiquid(mob.bb)))
            return false;

        for (int tries = 0; tries < 16; tries++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 32F);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 32F);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 40);
            return true;
        }
        return false;
    }

    void spawnTeleportParticles(Enderman mob) {
        if (mob.level == null || mob.level.particleEngine == null)
            return;

        float bbWidth = 0.6f;
        float bbHeight = 2.9f;
        try {
            bbWidth = mob.bb != null ? (mob.bb.x1 - mob.bb.x0) * 0.5f : bbWidth;
            bbHeight = mob.bb != null ? (mob.bb.y1 - mob.bb.y0) : bbHeight;
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < 20; i++) {
            float px = mob.x + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            float py = mob.y + mob.level.random.nextFloat() * bbHeight;
            float pz = mob.z + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            mob.level.particleEngine.spawnParticle(new SmokeParticle(mob.level, px, py, pz));
        }
    }
}
