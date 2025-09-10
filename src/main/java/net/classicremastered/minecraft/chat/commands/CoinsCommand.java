package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public class CoinsCommand implements Command {
    @Override
    public String getName() {
        return "coins";
    }

    @Override
    public String getUsage() {
        return "/coins <set|add|remove> <amount>";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (sender == null) return;

        if (args.length != 2) {
            if (mc != null && mc.hud != null) mc.hud.addChat("&cUsage: " + getUsage());
            return;
        }

        String mode = args[0].toLowerCase();
        int amt;
        try {
            amt = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            if (mc != null && mc.hud != null) mc.hud.addChat("&cAmount must be a number.");
            return;
        }

        switch (mode) {
            case "set" -> sender.coins = Math.max(0, amt);
            case "add" -> sender.coins += Math.max(0, amt);
            case "remove" -> sender.coins = Math.max(0, sender.coins - amt);
            default -> {
                if (mc != null && mc.hud != null) mc.hud.addChat("&cUsage: " + getUsage());
                return;
            }
        }

        if (mc != null && mc.hud != null) {
            mc.hud.addChat("&eCoins: &6" + sender.coins);
        }
    }
}
