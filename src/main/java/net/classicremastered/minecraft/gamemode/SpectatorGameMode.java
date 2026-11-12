package net.classicremastered.minecraft.gamemode;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class SpectatorGameMode extends GameMode {
    public SpectatorGameMode(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void apply(Level level) {
        super.apply(level);
        level.spectatorMode = true;
    }

    @Override
    public void apply(Player player) {
        player.setSpectator(true);
        player.canFly = true;
        player.isFlying = true;
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    @Override
    public boolean isSurvival() {
        return false;
    }

    @Override
    public boolean canPlace(int block) {
        return false;
    }

    @Override
    public void openInventory() {
    }
}
