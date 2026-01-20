package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.player.Player;

public class BotPvPAI extends BasicAI {
    private final Bot bot;

    public BotPvPAI(Bot mob) {
        this.bot = mob;
    }

    @Override
    protected void update() {
        if (bot.level == null || !(bot.level.player instanceof Player)) return;
        Player player = (Player) bot.level.player;

        // --- Simple chase movement ---
        float dx = player.x - bot.x;
        float dz = player.z - bot.z;
        float distSq = dx * dx + dz * dz;

        if (distSq > 0.0001f) {
            float dist = (float)Math.sqrt(distSq);
            // normalize to forward input
            this.yya = 1.0f; // always walk forward
            bot.yRot = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        } else {
            this.yya = 0f;
        }

        // --- Attack if close enough ---
        if (distSq < 2.5f * 2.5f) { // melee range ~2.5 blocks
            if (bot.attackTime <= 0) {
                bot.attackTime = 20; // cooldown (1 sec at 20 TPS)
                player.hurt(bot, 2); // deal 2 damage (like Zombie)
            }
        }
    }
}
