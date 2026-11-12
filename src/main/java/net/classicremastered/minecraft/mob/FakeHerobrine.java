package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

import java.util.Random;

public class FakeHerobrine extends Mob {
    private int lifeTicks = 20 * 30; // 30 seconds
    private int snapTimer = 0;
    private boolean snapActive = false;
    private final Random random = new Random();

    // Valid safe textures (must exist in your resources)
    private static final String[] SNAP_TEXTURES = {
        net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE,
        "/particles.png",
        "/char.png", // fallback to normal
        "/mob/creeper.png" // only if present in your resources
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
        Player player =
                this.level != null && this.level.player instanceof Player p && !p.isSpectator()
                ? p
                : null;
        if (player != null) {
            this.yRot = this.yRotO =
                    (float) (Math.toDegrees(Math.atan2(player.z - this.z, player.x - this.x))
                            - 90F);
        }

        // Texture corruption at 7M+
        if (player != null) {
            double dist = Math.max(Math.abs(player.x), Math.abs(player.z));
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isShootable() {
        return false;
    }
}
