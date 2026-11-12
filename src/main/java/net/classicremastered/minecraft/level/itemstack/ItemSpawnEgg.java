// File: src/com/mojang/minecraft/level/itemstack/ItemSpawnEgg.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
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
    public void use(Player player, Level level) {
        if (player == null || level == null || level.isNetworkMode() || player.minecraft == null)
            return;
        var hit = player.minecraft.selected;
        if (hit == null || hit.entityPos != 0) return;
        int x = hit.x, y = hit.y, z = hit.z;
        switch (hit.face) {
            case 0 -> y--;
            case 1 -> y++;
            case 2 -> z--;
            case 3 -> z++;
            case 4 -> x--;
            case 5 -> x++;
            default -> {
                return;
            }
        }
        Mob mob = MobRegistry.create(mobId, level, x + .5f, y, z + .5f);
        if (mob == null) return;
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
                level.addEntity(mob);
                return;
            }
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
