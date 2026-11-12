package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

public final class BoatItem extends Item {
    public static final int ID = 210;

    BoatItem() {
        super(ID, "Oak Boat", "/items/oak_boat.png", 1);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null
                || level == null
                || player.level != level
                || level.blockMap == null
                || level.isNetworkMode()
                || player.removed
                || player.health <= 0
                || player.isSpectator()
                || player.riding != null
                || player.minecraft == null
                || player.minecraft.isOnline()
                || player.inventory.getSelected() != ID + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return;

        float reach = player.minecraft.gamemode.getReachDistance();
        Vec3D start = new Vec3D(player.x, player.y, player.z);
        Vec3D direction = player.aimDirection(1);
        Vec3D end = start.add(direction.x * reach, direction.y * reach, direction.z * reach);
        MovingObjectPosition hit =
                level.clip(new Vec3D(start.x, start.y, start.z), end, false, true);
        if (hit == null) return;
        LiquidType liquid = level.getLiquid(hit.x, hit.y, hit.z);
        float feet;
        if (liquid == LiquidType.WATER) {
            feet = LiquidBlock.waterSurface(level, hit.x, hit.y, hit.z) - .18f;
        } else {
            Block block = Block.blocks[level.getTile(hit.x, hit.y, hit.z)];
            if (liquid != LiquidType.NOT_LIQUID || hit.face != 1 || block == null) return;
            AABB solid = block.getCollisionBox(hit.x, hit.y, hit.z);
            if (solid == null) return;
            feet = solid.y1 + .001f;
        }

        BoatEntity boat = new BoatEntity(level, hit.vec.x, feet, hit.vec.z, player.yRot);
        if (!level.getCubes(boat.bb).isEmpty()
                || level.containsLiquid(boat.bb, LiquidType.LAVA)
                || player.bb.intersects(boat.bb)) return;
        AABB path = player.bb.expand(hit.vec.x - start.x, hit.vec.y - start.y, hit.vec.z - start.z);
        for (Entity entity : level.blockMap.getEntities(player, path.grow(.2f, .2f, .2f))) {
            if (entity.isPickable() && entity.bb.grow(.1f, .1f, .1f).clip(start, hit.vec) != null)
                return;
        }
        for (Entity entity : level.blockMap.getEntities(player, boat.bb)) {
            if (entity.isPickable()) return;
        }
        level.addEntity(boat);
        consumeSelected(player, level);
    }
}
