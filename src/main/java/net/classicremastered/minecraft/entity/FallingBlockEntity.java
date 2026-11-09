package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.SandBlock;
import net.classicremastered.minecraft.render.BlockItemRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;
import org.lwjgl.opengl.GL11;

public final class FallingBlockEntity extends Entity {
    public static final long serialVersionUID = 0L;
    public static final int MAX_AGE = 600;

    public final int blockId;
    public int age;

    public FallingBlockEntity(Level level, float x, float y, float z, int blockId) {
        super(level);
        this.blockId = blockId;
        setSize(.98f, .98f);
        heightOffset = bbHeight * .5f;
        makeStepSound = false;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
    }

    @Override
    public void tick() {
        if (removed || level.isNetworkMode()) return;
        super.tick();
        xo = x;
        yo = y;
        zo = z;
        age++;
        yd -= .04f;
        move(xd, yd, zd);
        xd *= .98f;
        yd *= .98f;
        zd *= .98f;
        int bx = MathHelper.floor(x), by = MathHelper.floor(y), bz = MathHelper.floor(z);
        if (onGround && !SandBlock.canFallThrough(level, bx, by - 1, bz)) {
            boolean placed =
                    SandBlock.canFallThrough(level, bx, by, bz)
                            && level.setTile(bx, by, bz, blockId);
            finish(placed);
        } else if (y < -1 || age >= MAX_AGE) {
            finish(false);
        }
    }

    private void finish(boolean placed) {
        remove();
        if (!placed
                && !level.creativeMode
                && level.isInBounds(
                        MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z))) {
            level.addEntity(new DroppedBlock(level, x, y, z, blockId));
        }
    }

    @Override
    public void render(TextureManager textures, float partial) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    xo + (x - xo) * partial - .5f,
                    yo + (y - yo) * partial - .5f,
                    zo + (z - zo) * partial - .5f);
            BlockItemRenderer.render(Block.blocks[blockId], textures, getRenderBrightness(partial));
        } finally {
            GL11.glPopMatrix();
        }
    }
}
