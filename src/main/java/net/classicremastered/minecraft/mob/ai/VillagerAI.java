package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.Player;

public class VillagerAI extends BasicAI {

    private static final long serialVersionUID = 0L;

    public float homeX, homeY, homeZ;
    public float homeRadius = 3.0f;
    public boolean enforceInsideAtNight = true; // go home & stay put at night

    public VillagerAI(float hx, float hy, float hz, float radius) {
        super();
        this.homeX = hx;
        this.homeY = hy;
        this.homeZ = hz;
        this.homeRadius = radius;
        // gentle walk speed for villagers
        this.runSpeed = 0.6F;
        this.walkSpeed = 0.10f;
    }

    @Override
    protected void update() {
        if (this.level == null || this.mob == null) {
            super.update();
            return;
        }

        boolean reportedHome = false;
        Villager villager = null;
        if (this.mob instanceof Villager) {
            villager = (Villager) this.mob;
            reportedHome = villager.hasHome;
            if (reportedHome) {
                this.homeX = villager.homeX;
                this.homeY = villager.homeY;
                this.homeZ = villager.homeZ;
                this.homeRadius = villager.homeRadius;
            }
        }

        // --- Hostile villagers: break out & chase player ---
        if (villager != null && villager.isHostile()) {
            this.runSpeed = 1.2F; // zombie speed
            this.walkSpeed = 0.3f;

            if (reportedHome) {
                // If inside home area → head for the door with per-villager separation
                if (Math.abs(villager.x - homeX) < homeRadius && Math.abs(villager.z - homeZ) < homeRadius) {
                    float sepOffset = (villager.hashCode() % 3 - 1) * 0.8f; // -0.8, 0, +0.8
                    int doorX = (int) (homeX + sepOffset);
                    int doorY = (int) homeY;
                    int doorZ = (int) (homeZ - homeRadius - 0.5f);

                    // If door is blocked, smash through
                    int block = level.getTile(doorX, doorY, doorZ);
                    if (block != 0 && net.classicremastered.minecraft.level.tile.Block.blocks[block] != null
                            && net.classicremastered.minecraft.level.tile.Block.blocks[block] != net.classicremastered.minecraft.level.tile.Block.BEDROCK) {
                        level.setTile(doorX, doorY, doorZ, 0);
                        if (level.minecraft != null && level.minecraft.hud != null) {
                            level.minecraft.hud.addChat("&4A villager smashes the door!");
                        }
                    }

                    // Move toward door
                    float dx = (doorX + 0.5f) - mob.x;
                    float dz = (doorZ + 0.5f) - mob.z;
                    float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;

                    float dyaw = targetYaw - mob.yRot;
                    while (dyaw < -180.0F)
                        dyaw += 360.0F;
                    while (dyaw >= 180.0F)
                        dyaw -= 360.0F;

                    this.yRotA = dyaw * 0.5F;
                    this.yya = this.runSpeed;
                    this.running = true;
                    this.jumping = false; // no stuck jump at door
                    return;
                }
            }

            // --- Outside home → chase player within 40 blocks ---
            Player target = (this.level != null) ? (Player) this.level.player : null;
            if (target != null) {
                float dx = target.x - mob.x;
                float dz = target.z - mob.z;
                float dist2 = dx * dx + dz * dz;

                if (dist2 < 40 * 40) {
                    float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;

                    float dyaw = targetYaw - mob.yRot;
                    while (dyaw < -180.0F)
                        dyaw += 360.0F;
                    while (dyaw >= 180.0F)
                        dyaw -= 360.0F;

                    this.yRotA = dyaw * 0.5F;
                    this.yya = this.runSpeed;
                    this.running = true;
                    return;
                }
            }

            // Default hostile wander
            super.update();
            return;
        }

        // --- Peaceful villagers: original home/night behavior ---
        if (reportedHome && enforceInsideAtNight && !this.level.isDaytime()) {
            float dx = homeX - mob.x;
            float dz = homeZ - mob.z;
            float dist2 = dx * dx + dz * dz;
            float r2 = homeRadius * homeRadius;

            if (dist2 > r2) {
                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                float dyaw = targetYaw - mob.yRot;
                while (dyaw < -180.0F)
                    dyaw += 360.0F;
                while (dyaw >= 180.0F)
                    dyaw -= 360.0F;

                this.yRotA = dyaw * 0.5F;
                this.yya = this.runSpeed;
                this.running = true;
            } else {
                this.yya = 0.0F;
                this.xxa = 0.0F;
                this.running = false;
                this.jumping = false;
            }
            return;
        }

        // --- Daytime: gentle bias to stay near home ---
        super.update();

        if (reportedHome) {
            float dx = homeX - mob.x;
            float dz = homeZ - mob.z;
            float dist2 = dx * dx + dz * dz;
            float softLimit = homeRadius * homeRadius * 1.6f;

            if (dist2 > softLimit) {
                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                float dyaw = targetYaw - mob.yRot;
                while (dyaw < -180.0F)
                    dyaw += 360.0F;
                while (dyaw >= 180.0F)
                    dyaw -= 360.0F;

                this.yRotA += dyaw * 0.05F;
                this.yya += 0.2f;
                this.noActionTime = 0;
            }
        }
    }

}
