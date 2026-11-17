package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FlowerBlock;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

public class BucketItem extends ToolItem {

    public final int bucketType; // 0 = empty, 1 = water, 2 = lava

    public BucketItem(int id, int bucketType) {
        super(id, getBucketName(bucketType), "/items/bucket.png");
        this.bucketType = bucketType;
    }

    private static String getBucketName(int type) {
        return switch (type) {
            case 1 -> "Water Bucket";
            case 2 -> "Lava Bucket";
            default -> "Bucket";
        };
    }

    @Override public float getModelTextureU0() { return bucketType/3f; }
    @Override public float getModelTextureU1() { return (bucketType+1)/3f; }

    @Override
    public void renderIcon(TextureManager tm, ShapeRenderer sr) {
        String tex = getTexture();
        if (tex == null) return;
        int texId = tm.load(tex);
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texId);
        
        float u0 = bucketType / 3.0f;
        float u1 = (bucketType + 1) / 3.0f;
        
        sr.begin();
        sr.vertexUV(0, 16, 0, u0, 1);
        sr.vertexUV(16, 16, 0, u1, 1);
        sr.vertexUV(16, 0, 0, u1, 0);
        sr.vertexUV(0, 0, 0, u0, 0);
        sr.end();
    }

    @Override
    public boolean use(Player player, Level level) {
        if (player == null
                || level == null
                || player.level != level
                || level.isNetworkMode()
                || player.removed
                || player.health <= 0
                || player.isSpectator()
                || player.minecraft == null
                || player.minecraft.gamemode == null
                || player.inventory.getSelected() != id + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return false;
        float reach = player.minecraft.gamemode.getReachDistance();
        Vec3D start = new Vec3D(player.x, player.y, player.z);
        Vec3D aim = player.aimDirection(1);
        Vec3D end = start.add(aim.x * reach, aim.y * reach, aim.z * reach);
        MovingObjectPosition hit =
                bucketType == 0 ? level.clipLiquidSources(start, end) : level.clip(start, end);
        if (hit == null) return false;
        int result;
        if (bucketType == 0) {
            result = scoopSource(level, hit.x, hit.y, hit.z);
            if (result <= 0) return false;
        } else {
            if (!pour(player, level, hit)) return false;
            result = Item.BUCKET_EMPTY.id + 256;
        }
        player.inventory.setIdCount(player.inventory.selected, result, 1);
        return true;
    }

    private boolean pour(Player player, Level level, MovingObjectPosition hit) {
        int x = hit.x, y = hit.y, z = hit.z;
        int clicked = level.getTile(x, y, z);
        if (bucketType == 1
                && (clicked == Block.SNOW_LAYER.id || clicked == Block.GLASS.id && !player.sneaking)
                && placeWater(level, x, y, z)) return true;
        switch (hit.face) {
            case 0 -> y--;
            case 1 -> y++;
            case 2 -> z--;
            case 3 -> z++;
            case 4 -> x--;
            case 5 -> x++;
            default -> {
                return false;
            }
        }
        if (bucketType == 1) return placeWater(level, x, y, z);
        if (!level.isInBounds(x, y, z)
                || level.getTile(x, y, z) != 0
                || !level.setTile(x, y, z, Block.LAVA.id)) return false;
        level.playSound("random/pop", x + .5f, y + .5f, z + .5f, .5f, 1);
        return true;
    }

    public static boolean canPlaceWaterSource(Level level, int x, int y, int z) {
        if (level.isNetworkMode() || !level.isInBounds(x, y, z)) return false;
        int tile = level.getTile(x, y, z);
        return tile == 0 || level.isWater(x, y, z) || LiquidBlock.canWashAway(tile);
    }

    public static boolean placeWater(Level level, int x, int y, int z) {
        if (level.isNetworkMode() || !level.isInBounds(x, y, z)) return false;
        int tile = level.getTile(x, y, z);
        if (tile == Block.GLASS.id) {
            if (level.isWaterloggedGlass(x, y, z)) {
                if (level.getFlowLevel(x, y, z) == 0) return false;
                level.setWaterFlowLevel(x, y, z, 0);
            } else if (!level.setTile(x, y, z, Block.WATERLOGGED_GLASS.id)) return false;
        } else if (level.isWater(x, y, z)) {
            if (level.getFlowLevel(x, y, z) != 0) {
                level.setWaterFlowLevel(x, y, z, 0);
                level.addToTickNextTick(x, y, z, tile);
            }
        } else {
            if (tile != 0 && !LiquidBlock.canWashAway(tile)) return false;
            int source =
                    LiquidBlock.usesLevelledFlow(level)
                            ? Block.STATIONARY_WATER.id
                            : Block.WATER.id;
            if (!level.setTile(x, y, z, source)) return false;
            if (Block.blocks[tile] instanceof FlowerBlock && !level.creativeMode)
                Block.blocks[tile].dropItems(level, x, y, z, 1);
        }
        playWaterSound(level, "empty", x, y, z);
        return true;
    }

    public static int scoopSource(Level level, int x, int y, int z) {
        if (level.isNetworkMode() || !level.isInBounds(x, y, z) || level.getFlowLevel(x, y, z) != 0)
            return 0;
        boolean waterlogged = level.isWaterloggedGlass(x, y, z);
        LiquidType liquid = level.getLiquid(x, y, z);
        if (!waterlogged && liquid == LiquidType.NOT_LIQUID) return 0;
        int replacement = waterlogged ? Block.GLASS.id : 0;
        if (!level.setTile(x, y, z, replacement)
                || (waterlogged ? level.isWaterloggedGlass(x, y, z) : level.getTile(x, y, z) != 0))
            return 0;
        if (waterlogged || liquid == LiquidType.WATER) {
            playWaterSound(level, "fill", x, y, z);
            return Item.BUCKET_WATER.id + 256;
        }
        level.playSound("random/pop", x + .5f, y + .5f, z + .5f, .5f, .8f);
        return Item.BUCKET_LAVA.id + 256;
    }

    private static void playWaterSound(Level level, String action, int x, int y, int z) {
        level.playSound("item/bucket/" + action, x + .5f, y + .5f, z + .5f, 1, 1);
    }
}
