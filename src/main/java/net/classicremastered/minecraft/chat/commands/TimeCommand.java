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
            mc.hud.addChat("&eUsage: /time [set] <day|night|number>");
            return;
        }

        String sub = args[0];
        if (sub.equalsIgnoreCase("set") && args.length > 1) {
            sub = args[1];
        }

        if (sub.equalsIgnoreCase("day") || sub.equalsIgnoreCase("noon") || sub.equals("0")) {
            lvl.setTime(0);
            mc.hud.addChat("&aTime set to day.");
        } else if (sub.equalsIgnoreCase("night") || sub.equalsIgnoreCase("midnight") || sub.equals("12000") || sub.equals("14000")) {
            lvl.setTime(12000);
            mc.hud.addChat("&aTime set to night.");
        } else {
            try {
                int t = Integer.parseInt(sub);
                lvl.setTime(t);
                mc.hud.addChat("&aTime set to " + t + ".");
            } catch (NumberFormatException e) {
                mc.hud.addChat("&eUsage: /time [set] <day|night|number>");
            }
        }
    }
}
