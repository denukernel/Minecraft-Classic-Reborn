package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class TimeCommand implements Command {
    public String getName() { return "time"; }
    public String getUsage() { return "/time <day|night>"; }

    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null) return;
        Level lvl = mc.level;

        if (args.length == 0) {
            mc.hud.addChat("&eUsage: " + getUsage());
            return;
        }

        if (args[0].equalsIgnoreCase("day")) {
            lvl.setTime(0);
            mc.hud.addChat("&aTime set to day.");
        } else if (args[0].equalsIgnoreCase("night")) {
            lvl.setTime(12000);
            mc.hud.addChat("&aTime set to night.");
        } else {
            mc.hud.addChat("&eUsage: " + getUsage());
        }
    }
}
