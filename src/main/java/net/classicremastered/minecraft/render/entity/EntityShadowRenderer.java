package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.Frustrum;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Projects soft contact shadows onto nearby terrain beneath living entities. */
public final class EntityShadowRenderer {
    private static final float MAX_DISTANCE_SQUARED = 16 * 16;
    private static final float MAX_DROP = 1.5f;

    private EntityShadowRenderer() {}

    public static void render(
            Entity[] entities,
            Player localPlayer,
            Vec3D camera,
            Frustrum frustum,
            TextureManager textures,
            GameSettings settings,
            float partial) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_POLYGON_BIT
                        | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/misc/entity_shadow.png"));
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);

            GL11.glBegin(GL11.GL_QUADS);
            try {
                // The local player can cast a shadow even when its model is hidden in first person.
                if (settings.playerShadows && localPlayer != null && !localPlayer.isSpectator()) {
                    renderShadow(localPlayer, camera, frustum, partial);
                }
                for (Entity entity : entities) {
                    if (entity == localPlayer || !(entity instanceof Mob mob)) continue;
                    if (mob instanceof Player player
                            ? settings.playerShadows && !player.isSpectator()
                            : settings.mobShadows) {
                        renderShadow(mob, camera, frustum, partial);
                    }
                }
            } finally {
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void renderShadow(Mob mob, Vec3D camera, Frustrum frustum, float partial) {
        if (mob.removed || mob.bb == null || mob.level == null) return;

        float x = mob.xo + (mob.x - mob.xo) * partial;
        float z = mob.zo + (mob.z - mob.zo) * partial;
        float feet = mob.renderFeetY(partial);
        float dx = x - camera.x;
        float dy = feet - camera.y;
        float dz = z - camera.z;
        float strength = 1 - (dx * dx + dy * dy + dz * dz) / MAX_DISTANCE_SQUARED;
        if (strength <= 0) return;

        float radius = Math.max(.25f, Math.min(1.5f, mob.bbWidth * .8f));
        float left = x - radius;
        float right = x + radius;
        float near = z - radius;
        float far = z + radius;
        if (!frustum.isBoxInFrustrum(left, feet - MAX_DROP, near, right, feet + .04f, far)) return;

        Level level = mob.level;
        int minX = MathHelper.floor(left);
        int maxX = MathHelper.floor(right);
        int minZ = MathHelper.floor(near);
        int maxZ = MathHelper.floor(far);
        int minY = Math.max(0, MathHelper.floor(feet - MAX_DROP) - 1);
        int maxY = Math.min(level.depth - 1, MathHelper.floor(feet + .04f));
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!isColumnLoaded(level, bx, bz)) continue;
                for (int by = maxY; by >= minY; by--) {
                    Block block = Block.blocks[level.getTile(bx, by, bz)];
                    if (block == null) continue;
                    AABB surface = block.getCollisionBox(bx, by, bz);
                    if (surface == null || surface.y1 > feet + .04f) continue;

                    float drop = Math.max(0, feet - surface.y1);
                    float alpha = .55f * strength * (1 - drop / MAX_DROP);
                    if (alpha > 0) {
                        float light = level.getBrightness(bx, MathHelper.floor(surface.y1), bz);
                        drawSurface(surface, left, right, near, far, alpha * light);
                    }
                }
            }
        }
    }

    private static boolean isColumnLoaded(Level level, int x, int z) {
        if (level instanceof LevelInfiniteTerrain terrain) {
            return terrain.chunks().isChunkLoaded(x >> 4, z >> 4);
        }
        if (level instanceof LevelInfiniteFlat flat) {
            return flat.chunks().isChunkLoaded(x >> 4, z >> 4);
        }
        return true;
    }

    private static void drawSurface(
            AABB surface, float left, float right, float near, float far, float alpha) {
        float x0 = Math.max(left, surface.x0);
        float x1 = Math.min(right, surface.x1);
        float z0 = Math.max(near, surface.z0);
        float z1 = Math.min(far, surface.z1);
        if (x0 >= x1 || z0 >= z1) return;

        float u0 = (x0 - left) / (right - left);
        float u1 = (x1 - left) / (right - left);
        float v0 = (z0 - near) / (far - near);
        float v1 = (z1 - near) / (far - near);
        GL11.glColor4f(0, 0, 0, alpha);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(x0, surface.y1, z0);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(x0, surface.y1, z1);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(x1, surface.y1, z1);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(x1, surface.y1, z0);
    }
}
