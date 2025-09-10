// File: src/net/classicremastered/minecraft/level/tile/BlockPortal.java
package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.phys.AABB;

public class BlockPortal extends Block {

    public BlockPortal(int id) {
        super(id, 185, "Portal"); // beacon.png placeholder
        this.explodes = false;
        setBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

        // add sound + hardness data so stepsound is not null
        this.setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.0F);
    }


    @Override
    public boolean isSolid() {
        return false; // player can walk through
    }

    @Override
    public boolean isOpaque() {
        return false; // doesn’t block light
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return null; // no collision, so player can step into it
    }

    @Override
    public void renderInside(ShapeRenderer sr, int x, int y, int z, int side) {
        // Simple purple tint effect for placeholder
        sr.color(0.6F, 0.0F, 0.8F);
        super.renderInside(sr, x, y, z, side);
    }
}
