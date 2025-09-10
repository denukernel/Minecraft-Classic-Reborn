// File: src/com/mojang/minecraft/level/itemstack/ItemSpawnEgg.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import org.lwjgl.opengl.GL11;

public final class ItemSpawnEgg extends Item {

    private final short mobId;
    private final String mobName;

    public ItemSpawnEgg(int id, short mobId, String mobName) {
        super(id, mobName + " Spawn Egg", "/items/egg_colored.png", 64);
        this.mobId = mobId;
        this.mobName = mobName;
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        // changed: fallback if no block is selected
        if (player.minecraft == null || player.minecraft.selected == null) {
            return; // no target, do nothing
        }

        // changed: allow spawning on sides (entityPos != 0 just means not top face)
        int baseX = player.minecraft.selected.x;
         int baseY = player.minecraft.selected.y + 1; // default: top face
         int baseZ = player.minecraft.selected.z;

        // added: offset for side faces
        switch (player.minecraft.selected.entityPos) {
            case 1: // bottom face
                break; // keep Y+1
            case 2: baseZ--; break; // north
            case 3: baseZ++; break; // south
            case 4: baseX--; break; // west
            case 5: baseX++; break; // east
        }

        Mob m = MobRegistry.create(mobId, level, baseX + 0.5f, baseY, baseZ + 0.5f);
        if (m == null) return;

        final int MAX_LIFT_BLOCKS = 4; // changed: lift up to 4 blocks
        boolean placed = false;

        float px = baseX + 0.5f;
        float pz = baseZ + 0.5f;
        float py = baseY;

        // changed: clearance check supports mob height
        java.util.function.BiPredicate<Integer, Integer> coarseClear = (fx, fz) -> {
            int iy = (int) Math.floor(py);
            for (int h = 0; h < Math.ceil(m.bbHeight); h++) { // check mob height
                int block = level.getTile(fx, iy + h, fz);
                if (block != 0) return false;
                if (level.getLiquid(fx, iy + h, fz) != LiquidType.NOT_LIQUID) return false;
            }
            return true;
        };

        // try current column and lift up to 4 blocks
        for (int lift = 0; lift <= MAX_LIFT_BLOCKS; lift++) {
            float tryY = py + lift;
            m.setPos(px, tryY, pz);

            if (coarseClear.test((int) Math.floor(px), (int) Math.floor(tryY))
                && level.isFree(m.bb) && level.getCubes(m.bb).isEmpty()) {
                placed = true;
                break;
            }
        }

        // jitter if vertical failed
        if (!placed) {
            for (int jitter = 0; jitter < 8 && !placed; jitter++) {
                float jx = px + (level.random.nextFloat() - 0.5f);
                float jz = pz + (level.random.nextFloat() - 0.5f);
                for (int lift = 0; lift <= MAX_LIFT_BLOCKS; lift++) {
                    float tryY = py + lift;
                    m.setPos(jx, tryY, jz);

                    if (coarseClear.test((int) Math.floor(jx), (int) Math.floor(tryY))
                        && level.isFree(m.bb) && level.getCubes(m.bb).isEmpty()) {
                        px = jx; pz = jz; // changed: commit jittered pos
                        placed = true;
                        break;
                    }
                }
            }
        }

        if (!placed) return; // fixed: no safe spot found

        // fixed: clamp void spawn
        if (m.y < 1f) {
            m.y = 1f;
            m.setPos(m.x, m.y, m.z);
            if (!level.isFree(m.bb) || !level.getCubes(m.bb).isEmpty()) {
                return;
            }
        }

        level.addEntity(m);
    }


    @Override
    public void renderIcon(TextureManager tm, ShapeRenderer sr) {
        // If mob invalid, show null placeholder as-is
        if (MobRegistry.create(mobId, null, 0, 0, 0) == null) {
            int texId = tm.load("/items/egg_null.png");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glColor3f(1f, 1f, 1f);
            sr.begin();
            sr.vertexUV(0, 16, 0, 0, 1);
            sr.vertexUV(16, 16, 0, 1, 1);
            sr.vertexUV(16, 0, 0, 1, 0);
            sr.vertexUV(0, 0, 0, 0, 0);
            sr.end();
            return;
        }

        // Keep your placeholder tint routine (single image, two passes with tints).
        // Note: with a single combined image this produces a blended tint; for true two-tone,
        // you’ll need separate base/spots masks.
        MobEggColors.EggColor color = MobEggColors.get(mobName);
        int texId = tm.load("/items/egg_colored.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        // Base tint
        float r = ((color.base >> 16) & 255) / 255f;
        float g = ((color.base >> 8) & 255) / 255f;
        float b = (color.base & 255) / 255f;
        GL11.glColor3f(r, g, b);
        sr.begin();
        sr.vertexUV(0, 16, 0, 0, 1);
        sr.vertexUV(16, 16, 0, 1, 1);
        sr.vertexUV(16, 0, 0, 1, 0);
        sr.vertexUV(0, 0, 0, 0, 0);
        sr.end();

        // Spots tint (still same texture; gives a composite tint with single-mask art)
        r = ((color.spots >> 16) & 255) / 255f;
        g = ((color.spots >> 8) & 255) / 255f;
        b = (color.spots & 255) / 255f;
        GL11.glColor3f(r, g, b);
        sr.begin();
        sr.vertexUV(0, 16, 0, 0, 1);
        sr.vertexUV(16, 16, 0, 1, 1);
        sr.vertexUV(16, 0, 0, 1, 0);
        sr.vertexUV(0, 0, 0, 0, 0);
        sr.end();

        GL11.glColor3f(1f, 1f, 1f);
    }
}
