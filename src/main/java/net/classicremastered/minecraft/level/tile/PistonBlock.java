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
}
