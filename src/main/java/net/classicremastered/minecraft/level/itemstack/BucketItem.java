package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
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
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        // Custom raycast that checks for liquid blocks
        float yaw = player.yRot, pitch = player.xRot;
        float cy = net.classicremastered.util.MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = net.classicremastered.util.MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = net.classicremastered.util.MathHelper.cos(-pitch * 0.017453292F);
        float sp = net.classicremastered.util.MathHelper.sin(-pitch * 0.017453292F);
        float lx = sy * cp, ly = sp, lz = cy * cp;

        float reach = player.minecraft.gamemode.getReachDistance();
        Vec3D start = new Vec3D(player.x, player.y, player.z);
        Vec3D end = start.add(lx * reach, ly * reach, lz * reach);

        MovingObjectPosition sel = level.clip(start, end, false, true);
        if (sel == null || sel.entityPos != 0) return; // Must hit a block or liquid

        int x = sel.x;
        int y = sel.y;
        int z = sel.z;
        int side = sel.face;

        int slot = player.inventory.selected;

        if (bucketType == 0) {
            // Scoop up liquid
            int tile = level.getTile(x, y, z);
            if (tile == Block.WATER.id || tile == Block.STATIONARY_WATER.id) {
                level.setTile(x, y, z, 0); // Remove water
                level.playSound("random/pop", player, 0.5f, 1.0f);
                player.inventory.slots[slot] = Item.BUCKET_WATER.id + 256;
            } else if (tile == Block.LAVA.id || tile == Block.STATIONARY_LAVA.id) {
                level.setTile(x, y, z, 0); // Remove lava
                level.playSound("random/pop", player, 0.5f, 0.8f);
                player.inventory.slots[slot] = Item.BUCKET_LAVA.id + 256;
            }
        } else {
            // Place liquid
            int nx = x;
            int ny = y;
            int nz = z;
            if (side == 0) ny--;
            if (side == 1) ny++;
            if (side == 2) nz--;
            if (side == 3) nz++;
            if (side == 4) nx--;
            if (side == 5) nx++;

            if (level.isInBounds(nx, ny, nz) && level.getTile(nx, ny, nz) == 0) {
                int placeTile = (bucketType == 1) ? Block.STATIONARY_WATER.id : Block.STATIONARY_LAVA.id;
                level.setTile(nx, ny, nz, placeTile);
                
                // Add to flowLevels map as level 0 (source) so it flows properly
                level.flowLevels.put(Level.getCoordKey(nx, ny, nz), (byte) 0);
                level.addToTickNextTick(nx, ny, nz, placeTile);

                level.playSound("random/pop", player, 0.5f, 1.0f);
                player.inventory.slots[slot] = Item.BUCKET_EMPTY.id + 256;
            }
        }
    }
}
