package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

import java.util.Random;

public final class PistonBlock extends Block {
    private final int topTex    = 176;
    private final int bottomTex = 177;
    private static final int sideTex = 178;

    public PistonBlock(int id) {
        super(id, sideTex, "Piston");
        this.setData(Tile$SoundType.wood, 1.0F, 1.0F, 2.0F);
    }

    @Override
    protected int getTextureId(int side) {
        if (side == 0) return bottomTex;
        if (side == 1) return topTex;
        return sideTex;
    }


    private void pushBlock(Level level, int x, int y, int z, int dir) {
        int tx = x, ty = y, tz = z;
        if (dir == 0) ty--;
        if (dir == 1) ty++;
        if (dir == 2) tz--;
        if (dir == 3) tz++;
        if (dir == 4) tx--;
        if (dir == 5) tx++;

        int id = level.getTile(tx, ty, tz);
        if (id != 0) {
            int fx = tx, fy = ty, fz = tz;
            if (dir == 0) fy--;
            if (dir == 1) fy++;
            if (dir == 2) fz--;
            if (dir == 3) fz++;
            if (dir == 4) fx--;
            if (dir == 5) fx++;

            if (level.getTile(fx, fy, fz) == 0) {
                level.setTileNoUpdate(fx, fy, fz, id);
                level.setTileNoUpdate(tx, ty, tz, 0);
            }
        }
    }
}
