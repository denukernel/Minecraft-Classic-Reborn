// File: src/net/classicremastered/minecraft/bot/ai/BotExpertPvPAI.java
package net.classicremastered.minecraft.bot.ai;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.util.MathHelper;

public class BotExpertPvPAI extends BasicAI {

    private final Mob bot;
    private int attackCooldown = 0;
    private int jumpCooldown = 0;
    private int buildCooldown = 0;

    public BotExpertPvPAI(Mob bot) {
        this.bot = bot;
    }

    @Override
    protected void update() {
        if (bot.level == null || !(bot.level.player instanceof Player)) return;
        Player target = (Player) bot.level.player;

        float dx = target.x - bot.x;
        float dz = target.z - bot.z;
        float dy = target.y - bot.y;
        float dist2 = dx * dx + dz * dz;

        // Always face target
        bot.yRot = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

        // ---- Movement ----
        this.yya = 1.0F;   // always move forward toward player
        this.xxa = MathHelper.sin(bot.tickCount * 0.3F) * 0.8F; // strafe left/right wiggle
        this.running = true;

        // Bunny-hopping
        if (jumpCooldown-- <= 0) {
            this.jumping = true;
            jumpCooldown = 8; // jump every 0.4s
        } else {
            this.jumping = false;
        }

        // ---- Attacking ----
        if (attackCooldown-- <= 0 && dist2 < 4.0F) {
            target.hurt(bot, 2); // deal 2 dmg
            attackCooldown = 8; // 0.4s cooldown at 20 TPS
        }

        // ---- Pillar up if target is above ----
        if (dy > 2.5F && buildCooldown-- <= 0) {
            int bx = (int)Math.floor(bot.x);
            int by = (int)Math.floor(bot.y);
            int bz = (int)Math.floor(bot.z);

            if (bot.level.getTile(bx, by, bz) == 0) {
                bot.level.setTile(bx, by, bz, Block.COBBLESTONE.id);
                bot.y += 1.0F; // rise with block
            }
            buildCooldown = 10; // pillar every 0.5s
        }
    }
}
