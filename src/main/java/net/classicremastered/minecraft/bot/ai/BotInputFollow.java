package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.mob.ai.BasicAI;

public class BotInputFollow extends BasicAI {
    private final Bot clone;

    public BotInputFollow(Bot mob) {
        this.clone = mob;
    }

    @Override
    protected void update() {
        if (clone.level == null || !(clone.level.player instanceof Player)) return;
        Player p = (Player) clone.level.player;

        // Mirror the player’s input into this mob’s movement
        this.jumping = p.input.jumping;
        this.xxa     = p.input.xxa;   // strafe
        this.yya     = p.input.yya;   // forward
        this.running = p.input.running;
    }
}
