// com/mojang/minecraft/chat/commands/GameModeCommand.java
package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.GameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class GameModeCommand implements Command {
    @Override public String getName()  { return "gamemode"; }
    @Override public String getUsage() { return "/gamemode <creative|survival>"; }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (args.length < 1) {
            mc.hud.addChat("&cUsage: " + getUsage());
            return;
        }

        String mode = args[0].toLowerCase();
        GameMode newMode = null;

        if (mode.equals("creative") || mode.equals("c") || mode.equals("1")) {
            if (mc.gamemode instanceof CreativeGameMode) {
                mc.hud.addChat("&eAlready in Creative.");
                return;
            }
            newMode = new CreativeGameMode(mc);
        } else if (mode.equals("survival") || mode.equals("s") || mode.equals("0")) {
            if (mc.gamemode instanceof SurvivalGameMode) {
                mc.hud.addChat("&eAlready in Survival.");
                return;
            }
            newMode = new SurvivalGameMode(mc);
        } else {
            mc.hud.addChat("&cUnknown mode. " + getUsage());
            return;
        }

        mc.gamemode = newMode;

        // Re-apply mode to level and player
        Level lvl = mc.level;
        if (lvl != null) {
            mc.gamemode.apply(lvl);
        }
        if (mc.player != null) {
            mc.gamemode.apply(mc.player);
            // Optional: reset position/inventory rules depending on your GameMode implementation
            // mc.player.resetPos();
        }

        mc.hud.addChat("&aGame mode set to &f" +
                (mc.gamemode instanceof CreativeGameMode ? "Creative" : "Survival") + "&a.");
    }
}
