package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.util.MathHelper;

import java.util.Random;

public class FlowerBlock extends Block {

    protected FlowerBlock(int var1, int var2) {
        this(var1, var2, "Flower");
    }

    protected FlowerBlock(int var1, int var2, String name) {
        super(var1, name);
        this.textureId = var2;
        this.setPhysics(true);
        float var3 = 0.2F;
        this.setBounds(0.5F - var3, 0.0F, 0.5F - var3, var3 + 0.5F, var3 * 3.0F, var3 + 0.5F);
    }

    public static boolean isFlower(int id) {
        return id == DANDELION.id || id == ROSE.id;
    }

    public final boolean canBeWashedAway() {
        return isFlower(id)
                || this instanceof MushroomBlock
                || this instanceof SaplingBlock
                || this instanceof ShortGrassBlock;
    }

    protected boolean supports(int blockId) {
        return blockId == GRASS.id;
    }

    public final boolean dropsWhenPlacedOnWater(Level level, int x, int y, int z) {
        if (!isFlower(id) || level.isNetworkMode()) return false;
        return level.getLiquid(x, y, z) == LiquidType.WATER
                || level.getTile(x, y, z) == 0 && level.getLiquid(x, y - 1, z) == LiquidType.WATER;
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return !canBeWashedAway()
                || level.getTile(x, y, z) == 0 && supports(level.getTile(x, y - 1, z))
                || dropsWhenPlacedOnWater(level, x, y, z);
    }

    public final void dropFromWater(Level level, int x, int y, int z) {
        if (getDropCount() > 0) {
            level.addEntity(new DroppedBlock(level, x + 0.5F, y + 0.5F, z + 0.5F, id));
        }
    }

    @Override
    public void onRemoved(Level level, int x, int y, int z) {
        if (canBeWashedAway()
                && !level.isNetworkMode()
                && level.getLiquid(x, y, z) == LiquidType.WATER) {
            dropFromWater(level, x, y, z);
        }
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (canBeWashedAway()) checkSupport(level, x, y, z);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random rand) {
        checkSupport(level, x, y, z);
    }

    private void checkSupport(Level level, int x, int y, int z) {
        if (!level.isNetworkMode()
                && level.getTile(x, y, z) == id
                && !supports(level.getTile(x, y - 1, z))
                && level.setTile(x, y, z, 0)
                && level.getLiquid(x, y, z) != LiquidType.WATER) {
            dropItems(level, x, y, z, 1.0F);
        }
    }

    private void render(ShapeRenderer var1, float var2, float var3, float var4) {
        int var15;
        int var5 = (var15 = this.getTextureId(15)) % 16 << 4;
        int var6 = var15 / 16 << 4;
        float var16 = (float) var5 / 256.0F;
        float var17 = ((float) var5 + 15.99F) / 256.0F;
        float var7 = (float) var6 / 256.0F;
        float var18 = ((float) var6 + 15.99F) / 256.0F;

        for (int var8 = 0; var8 < 2; ++var8) {
            float var9 =
                    (float)
                            ((double) MathHelper.sin((float) var8 * 3.1415927F / 2.0F + 0.7853982F)
                                    * 0.5D);
            float var10 =
                    (float)
                            ((double) MathHelper.cos((float) var8 * 3.1415927F / 2.0F + 0.7853982F)
                                    * 0.5D);
            float var11 = var2 + 0.5F - var9;
            var9 += var2 + 0.5F;
            float var13 = var3 + 1.0F;
            float var14 = var4 + 0.5F - var10;
            var10 += var4 + 0.5F;
            var1.vertexUV(var11, var13, var14, var17, var7);
            var1.vertexUV(var9, var13, var10, var16, var7);
            var1.vertexUV(var9, var3, var10, var16, var18);
            var1.vertexUV(var11, var3, var14, var17, var18);
            var1.vertexUV(var9, var13, var10, var17, var7);
            var1.vertexUV(var11, var13, var14, var16, var7);
            var1.vertexUV(var11, var3, var14, var16, var18);
            var1.vertexUV(var9, var3, var10, var17, var18);
        }
    }

    public final boolean isOpaque() {
        return false;
    }

    public final boolean isSolid() {
        return false;
    }

    public final void renderPreview(ShapeRenderer var1) {
        var1.normal(0.0F, 1.0F, 0.0F);
        var1.begin();
        this.render(var1, 0.0F, 0.4F, -0.3F);
        var1.end();
    }

    public final boolean isCube() {
        return false;
    }

    public final boolean render(Level var1, int var2, int var3, int var4, ShapeRenderer var5) {
        float var6 = var1.getRenderBrightness(var2, var3, var4);
        var5.color(var6, var6, var6);
        this.render(var5, (float) var2, (float) var3, (float) var4);
        return true;
    }

    public final void renderFullbright(ShapeRenderer shapeRenderer) {
        shapeRenderer.color(1.0F, 1.0F, 1.0F);
        this.render(shapeRenderer, (float) -2, 0.0F, 0.0F);
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return null;
    }
}
