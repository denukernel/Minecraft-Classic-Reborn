// File: src/com/mojang/minecraft/chat/commands/BotCommand.java
package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.bot.ai.BotInputFollow;
import net.classicremastered.minecraft.bot.ai.BotPvPAI;
import net.classicremastered.minecraft.bot.ai.BotExpertPvPAI; // new AI
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class BotCommand implements Command, AliasedCommand {

    @Override
    public String getName() {
        return "bot";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "bots" };
    }

    @Override
    public String getUsage() {
        return "/bot spawn <count> <goal>";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null) {
            hud(mc, "&cNo level loaded.");
            return;
        }
        if (args.length < 1 || !"spawn".equalsIgnoreCase(args[0])) {
            hud(mc, "&cUsage: " + getUsage());
            return;
        }

        int count = 1;
        if (args.length >= 2) {
            try {
                count = Math.max(1, Math.min(20, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {}
        }

        String goal = (args.length >= 3 ? args[2].toLowerCase() : "follow");

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            float x = sender.x + (mc.renderer.random.nextFloat() - 0.5f) * 2f;
            float y = sender.y;
            float z = sender.z + (mc.renderer.random.nextFloat() - 0.5f) * 2f;

            Bot bot = new Bot(mc.level, x, y, z);

            // === choose AI based on goal ===
            switch (goal) {
                case "follow" -> bot.ai = new BotInputFollow(bot);
                case "pvp"    -> bot.ai = new BotPvPAI(bot);
                case "expertpvp" -> bot.ai = new BotExpertPvPAI(bot); // new expert AI
                default -> {
                    hud(mc, "&eUnknown goal: &f" + goal + "&e (using follow)");
                    bot.ai = new BotInputFollow(bot);
                }
            }

            mc.level.addEntity(bot);
            spawned++;
        }

        hud(mc, "&aSpawned " + spawned + " bot(s) with goal: &f" + goal);
    }

    private static void hud(Minecraft mc, String msg) {
        if (mc != null && mc.hud != null) mc.hud.addChat(msg);
    }
}
