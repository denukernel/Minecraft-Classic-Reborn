package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public class ReputationCommand implements Command {
    @Override
    public String getName() {
        return "reputation";
    }

    @Override
    public String getUsage() {
        return "/reputation <set|add|remove> <amount>";
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
            case "set" -> sender.villagerReputation = clamp(amt);
            case "add" -> sender.villagerReputation = clamp(sender.villagerReputation + amt);
            case "remove" -> sender.villagerReputation = clamp(sender.villagerReputation - amt);
            default -> {
                if (mc != null && mc.hud != null) mc.hud.addChat("&cUsage: " + getUsage());
                return;
            }
        }

        if (mc != null && mc.hud != null) {
            mc.hud.addChat("&eVillager reputation: &a" + sender.villagerReputation);
        }
    }

    private int clamp(int v) {
        if (v < -100) return -100;
        if (v > 100) return 100;
        return v;
    }
}
