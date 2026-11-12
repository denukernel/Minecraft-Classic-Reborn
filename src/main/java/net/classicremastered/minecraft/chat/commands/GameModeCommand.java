package net.classicremastered.minecraft.chat.commands;

import java.util.Locale;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.GameMode;
import net.classicremastered.minecraft.gamemode.SpectatorGameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.player.Player;

public class GameModeCommand implements Command {
    @Override
    public String getName() {
        return "gamemode";
    }

    @Override
    public String getUsage() {
        return "/gamemode <creative|survival|spectator>";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (args.length != 1) {
            mc.hud.addChat("&cUsage: " + getUsage());
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        GameMode newMode = switch (mode) {
        case "creative", "c", "1" -> new CreativeGameMode(mc);
        case "survival", "s", "0" -> new SurvivalGameMode(mc);
        case "spectator", "sp", "3" -> new SpectatorGameMode(mc);
        default -> null;
        };
        if (newMode == null) {
            mc.hud.addChat("&cUnknown mode. " + getUsage());
            return;
        }
        mc.changeGameMode(newMode);
    }
}
