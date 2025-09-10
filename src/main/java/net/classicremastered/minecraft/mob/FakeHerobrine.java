package net.classicremastered.minecraft.mob;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;

public class FakeHerobrine extends Mob {
    private int lifeTicks = 20 * 30; // 30 seconds
    private int snapTimer = 0;
    private boolean snapActive = false;
    private final Random random = new Random();

    // Valid safe textures (must exist in your resources)
    private static final String[] SNAP_TEXTURES = {
        "/terrain.png",
        "/particles.png",
        "/char.png",          // fallback to normal
        "/mob/creeper.png"    // only if present in your resources
    };
    @Override
    public void hurt(net.classicremastered.minecraft.Entity attacker, int damage) {
        // Ignore — unkillable / untouchable
    }
    public FakeHerobrine(Level level) {
        super(level);
        this.modelName = "humanoid";
        this.heightOffset = 1.62F;
        this.bbHeight = 1.8F;
        this.bbWidth = 0.6F;
        this.textureName = "/char.png"; // normal Steve skin
        this.health = 9999;
        this.ai = null;
    }

    @Override
    public void aiStep() {
        // Always face the player
        if (this.level != null && this.level.player != null) {
            this.yRot = this.yRotO =
                (float)(Math.toDegrees(Math.atan2(
                    this.level.player.z - this.z,
                    this.level.player.x - this.x
                )) - 90F);
        }

        // Texture corruption at 7M+
        if (this.level != null && this.level.player != null) {
            double dist = Math.max(Math.abs(this.level.player.x), Math.abs(this.level.player.z));
            if (dist >= 7_000_000) {
                if (++snapTimer >= 100) { // every 5s
                    snapTimer = 0;
                    snapActive = true;

                    // Pick random safe texture
                    String chosen = SNAP_TEXTURES[random.nextInt(SNAP_TEXTURES.length)];
                    this.textureName = chosen != null ? chosen : "/char.png";
                }
            } else if (snapActive) {
                // Restore when out of range
                this.textureName = "/char.png";
                snapActive = false;
                snapTimer = 0;
            }
        }

        // Lifetime
        if (--lifeTicks <= 0) {
            this.remove();
        }
    }

    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isShootable() { return false; }
}
