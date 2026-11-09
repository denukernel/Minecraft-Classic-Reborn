package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public final class DevPowersCommand implements Command {
    @Override
    public String getName() {
        return "devpowers";
    }
    @Override
    public String getUsage() {
        return "/devpowers";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (sender == null || mc.level == null || mc.hud == null)
            return;
        if (!mc.developer || mc.level.isNetworkMode()) {
            mc.hud.addChat("&c/devpowers requires developer mode in a local world.");
            return;
        }
        if (args.length != 0) {
            mc.hud.addChat(getUsage());
            return;
        }
        if (sender.developerPowers().toggle()) {
            mc.hud.addChat("&aKernel (developer) powerups enabled");
            mc.hud.addChat("&7Hold attack for power punches. G kicks. R dodges.");
            mc.hud.addChat("&7Sword: right click sweeps, shift + right click thrusts.");
            mc.hud.addChat("&7Hold B to aim a block, release B to throw.");
            mc.hud.addChat("&7Mob damage reduced 75%. Developer Options in Options.");
        } else {
            mc.hud.addChat("&7Kernel (developer) powerups disabled");
        }
    }
}
