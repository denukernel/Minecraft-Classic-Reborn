// com/mojang/minecraft/command/Command.java
package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public interface Command {
    String getName();

    String getUsage();

    void execute(Minecraft mc, Player sender, String[] args);

    default java.util.List<String> suggest(
            Minecraft mc, Player sender, String[] args, int argument) {
        return CommandSuggestions.arguments(this, args, argument);
    }
}
