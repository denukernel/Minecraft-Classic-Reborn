package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.entity.FlyingPlank;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ai.vandalizer.HouseVandalizerAI;
import net.classicremastered.minecraft.model.Vec3D;

/**
 * Rare undead house raider. Planks are taken from the actual house, never
 * created.
 */
public final class HouseVandalizer extends Zombie {

    public static final short REGISTRY_ID = 63;
    public int throwTicks;
    public HouseVandalizer(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "housevandalizer";
        textureName = "/mob/house-vandalizer.png";
        health = lastHealth = 30;
        helmet = armor = false;
        equipmentRolled = true;
        legacyArmor = false;
        setSize(.65f, 2f);
        heightOffset = 1f;
        bobStrength = 0;
        ai = new HouseVandalizerAI();
        setPos(x, y, z);
    }

    @Override
    public void tick() {
        if (throwTicks > 0)
            throwTicks--;
        super.tick();
    }

    public boolean tearHouseBlock(int x, int y, int z, Block block) {
        if (health <= 0 || level == null || level.isNetworkMode()
                || (block != Block.WOOD && block != Block.GLASS) || level.getTile(x, y, z) != block.id)
            return false;
        if (block == Block.GLASS) {
            var wall = level.clip(new Vec3D(this.x, bb.y0 + 1.25f, this.z),
                    new Vec3D(x + .5f, y + .5f, z + .5f));
            if (wall != null && (wall.x != x || wall.y != y || wall.z != z))
                return false;
        }
        if (!level.setTile(x, y, z, 0))
            return false;
        if (level.getTile(x, y, z) != 0) {
            level.setTile(x, y, z, block.id);
            return false;
        }
        boolean glass = block == Block.GLASS;
        float angle = glass ? (float) Math.atan2(this.x - x - .5f, z + .5f - this.z)
                : yBodyRot * (float) Math.PI / 180f;
        float bx = (float) Math.sin(angle), bz = -(float) Math.cos(angle);
        level.addEntity(new FlyingPlank(level, this, this.x + bx * .8f, this.bb.y0 + 1.25f, this.z + bz * .8f,
                bx * .65f, .3f, bz * .65f, glass));
        throwTicks = 12;
        if (!glass)
            level.playSound("step/wood", this, .8f, .8f);
        return true;
    }
}
