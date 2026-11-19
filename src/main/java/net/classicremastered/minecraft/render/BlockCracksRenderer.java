package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.CircuitBlock;
import net.classicremastered.minecraft.level.tile.LadderBlock;
import net.classicremastered.minecraft.mob.Miner;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import org.lwjgl.opengl.GL11;

/** The same staged terrain overlay for player and worker mining. */
public final class BlockCracksRenderer {
    private BlockCracksRenderer() {
    }

    public static int texture(float progress) {
        return 240 + Math.min(9, Math.max(0, (int) (progress * 10)));
    }

    public static void renderMiners(Level level, TextureManager textures, Vec3D camera) {
        if (level.isNetworkMode() || level.blockMap == null)
            return;
        AABB visible = new AABB(camera.x, camera.y, camera.z, camera.x, camera.y, camera.z).grow(32, 32, 32);
        for (Object entity : level.findEntities(null, visible)) {
            if (!(entity instanceof Miner miner) || miner.removed || miner.health <= 0)
                continue;
            Miner.MiningCrack crack = miner.miningCrack();
            if (crack == null)
                continue;
            var pos = crack.block();
            if (level.getTile(pos.x(), pos.y(), pos.z()) == crack.blockId()) {
                render(level, textures, pos.x(), pos.y(), pos.z(), texture(crack.progress()));
            }
        }
    }

    public static void render(Level level, TextureManager textures, int x, int y, int z, int texture) {
        int id = level.getTile(x, y, z);
        Block block = id > 0 ? Block.blocks[id] : null;
        if (block == null)
            return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load(BlockTextureAtlas.RESOURCE));
            GL11.glColor4f(1, 1, 1, .5f);
            GL11.glDepthMask(false);
            ShapeRenderer shape = ShapeRenderer.instance;
            if (block instanceof LadderBlock || block instanceof CircuitBlock) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1, -1);
                shape.begin();
                shape.noColor();
                boolean drawn = true;
                if (block instanceof LadderBlock ladder)
                    ladder.renderCracks(shape, x, y, z, texture);
                else
                    drawn = ((CircuitBlock) block).renderTorchCracks(level, x, y, z, shape, texture);
                shape.end();
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                if (drawn)
                    return;
            }
            float cx = x + (block.x1 + block.x2) * .5f;
            float cy = y + (block.y1 + block.y2) * .5f;
            float cz = z + (block.z1 + block.z2) * .5f;
            GL11.glTranslatef(cx, cy, cz);
            GL11.glScalef(1.01f, 1.01f, 1.01f);
            GL11.glTranslatef(-cx, -cy, -cz);
            shape.begin();
            shape.noColor();
            for (int face = 0; face < 6; face++)
                block.renderSide(shape, x, y, z, face, texture);
            shape.end();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
