package net.classicremastered.minecraft.particle;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.GunRenderer;
import net.classicremastered.minecraft.render.ShapeRenderer;

import java.util.concurrent.ThreadLocalRandom;

public final class FoodParticle extends Particle {
    private final String texture;
    private final float u;
    private final float v;

    private FoodParticle(Level level, Vec3D position, Vec3D velocity, String texture) {
        super(level, position.x, position.y, position.z, 0, 0, 0);
        this.texture = texture;
        u = .375f + uo * (.125f / 3);
        v = .375f + vo * (.125f / 3);
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
        size *= .24f;
        gravity = .3f;
        lifetime = 10;
    }

    public static void spawn(Player player, String texture, int count) {
        if (player.level.particleEngine == null) return;
        var random = ThreadLocalRandom.current();
        Vec3D eye = GunRenderer.cameraToWorld(player, new Vec3D(0, 0, 0), 1);
        for (int i = 0; i < count; i++) {
            Vec3D position =
                    GunRenderer.cameraToWorld(
                            player,
                            new Vec3D(
                                    random.nextFloat() * .36f - .24f,
                                    random.nextFloat() * .18f - .34f,
                                    random.nextFloat() * .14f - .62f),
                            1);
            Vec3D velocity =
                    GunRenderer.cameraToWorld(
                                    player,
                                    new Vec3D(
                                            random.nextFloat() * .10f - .05f,
                                            random.nextFloat() * .06f + .01f,
                                            random.nextFloat() * .04f - .05f),
                                    1)
                            .subtract(eye);
            player.level.particleEngine.spawnParticle(
                    new FoodParticle(player.level, position, velocity, texture));
        }
    }

    public static void spawnFragments(Level level, Vec3D position, String texture, int count) {
        if (level.particleEngine == null) return;
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vec3D velocity =
                    new Vec3D(
                            (random.nextFloat() - .5f) * .08f,
                            (random.nextFloat() - .5f) * .08f,
                            (random.nextFloat() - .5f) * .08f);
            level.particleEngine.spawnParticle(
                    new FoodParticle(level, position, velocity, texture));
        }
    }

    public String texture() {
        return texture;
    }

    @Override
    public int getParticleTexture() {
        return ParticleManager.FOOD_PASS;
    }

    @Override
    public void render(ShapeRenderer shape, float partial, Vec3D right, Vec3D up) {
        renderQuad(shape, partial, right, up, u, v, .125f);
    }
}
