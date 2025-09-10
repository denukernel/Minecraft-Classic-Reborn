// com/mojang/minecraft/command/Command.java
package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public interface Command {
    String getName();
    String getUsage();
    void execute(Minecraft mc, Player sender, String[] args);
}
