package net.classicremastered.minecraft.chat.commands;

import java.io.File;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.FileOpener;

public class ScreenshotsCommand implements Command {
    public String getName() { return "screenshots"; }
    public String getUsage() { return "/screenshots  - Opens the screenshots folder"; }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        File folder = new File(Minecraft.mcDir, "screenshots");
        if (!folder.exists()) folder.mkdirs();

        if (FileOpener.openDirectory(folder)) {
            mc.hud.addChat("&aOpened screenshots folder.");
        } else {
            mc.hud.addChat("&cFailed to open screenshots folder.");
        }
    }
}
