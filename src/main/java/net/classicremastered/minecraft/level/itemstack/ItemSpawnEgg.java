// File: src/com/mojang/minecraft/level/itemstack/ItemSpawnEgg.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.mob.Fish;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

public final class ItemSpawnEgg extends Item {

    private final short mobId;
    private final String mobName;

    public ItemSpawnEgg(int id, short mobId, String mobName) {
        super(id, MobRegistry.displayName(mobId) + " Spawn Egg", "/items/egg_colored.png", 64);
        this.mobId = mobId;
        this.mobName = mobName;
    }

    @Override
    public boolean use(Player player, Level level) {
        if (player == null
                || level == null
                || player.level != level
                || player.minecraft == null
                || player.minecraft.gamemode == null
                || player.minecraft.isOnline()
                || level.isNetworkMode()
                || player.isSpectator()
                || player.removed
                || player.health <= 0
                || player.inventory.getSelected() != id + 256
                || player.inventory.getCount(player.inventory.selected) <= 0) return false;
        var hit = player.minecraft.selected;
        if (hit != null && hit.entityPos != 0) return false;
        float reach = player.minecraft.gamemode.getReachDistance();
        Vec3D origin = new Vec3D(player.x, player.y, player.z);
        Vec3D direction = player.aimDirection(1);
        Vec3D end = origin.add(direction.x * reach, direction.y * reach, direction.z * reach);
        var water = level.clip(new Vec3D(origin.x, origin.y, origin.z), end, false, true);
        if (water != null && level.isWater(water.x, water.y, water.z)) {
            return spawn(player, level, water.x, water.y, water.z, true);
        }
        if (hit == null) return false;
        int x = hit.x, y = hit.y, z = hit.z;
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
        return spawn(player, level, x, y, z, level.isWater(x, y, z));
    }

    private boolean spawn(Player player, Level level, int x, int y, int z, boolean inWater) {
        if (!level.isInBounds(x, y, z)) return false;
        Mob mob = MobRegistry.create(mobId, level, x + .5f, y, z + .5f);
        if (mob == null || mob.getRegistryId() != mobId) return false;
        if (inWater) {
            float surface = LiquidBlock.waterSurfaceAt(level, x, y, z, .5f, .5f);
            float waterDepth = surface - y;
            if (waterDepth <= .02f) return false;
            float feet = y + Math.max(.01f, (waterDepth - mob.bbHeight) * .5f);
            mob.setPos(x + .5f, feet + mob.bbHeight * .5f, z + .5f);
            if (mob.bb.y1 >= level.depth
                    || !level.getCubes(mob.bb).isEmpty()
                    || !level.findEntities(mob, mob.bb).isEmpty()
                    || level.containsLiquid(mob.bb, LiquidType.LAVA)) return false;
            return release(player, level, mob);
        }
        for (int attempt = 0; attempt < 9; attempt++)
            for (int lift = 0; lift <= 4; lift++) {
                float px = x + .5f + (attempt == 0 ? 0 : (level.random.nextFloat() - .5f));
                float pz = z + .5f + (attempt == 0 ? 0 : (level.random.nextFloat() - .5f));
                float feet = y + lift;
                if (feet < 1 || feet + mob.bbHeight >= level.depth) continue;
                mob.setPos(px, feet + mob.bbHeight * .5f, pz);
                if (!level.getCubes(mob.bb).isEmpty() || !level.findEntities(mob, mob.bb).isEmpty())
                    continue;
                boolean liquid = false;
                for (int yy = (int) Math.floor(mob.bb.y0); yy <= Math.floor(mob.bb.y1); yy++)
                    if (level.getLiquid((int) Math.floor(px), yy, (int) Math.floor(pz))
                            != LiquidType.NOT_LIQUID) {
                        liquid = true;
                        break;
                    }
                if (liquid) continue;
                return release(player, level, mob);
            }
        return false;
    }

    private boolean release(Player player, Level level, Mob mob) {
        if (mob instanceof Fish) mob.persistent = true;
        level.addEntity(mob);
        consumeSelected(player, level);
        return true;
    }

    @Override
    public void renderIcon(TextureManager tm, ShapeRenderer sr) {
        GL11.glColor4f(1, 1, 1, 1);
        super.renderIcon(tm, sr);
    }

    @Override
    public String getTexture() {
        return MobRegistry.idOfName(mobName) < 0 || MobRegistry.idOfName(mobName) != mobId
                ? MobEggColors.texture(null)
                : MobEggColors.texture(mobName);
    }

    @Override
    public int getModelTint() {
        return 0xFFFFFF;
    }
}
