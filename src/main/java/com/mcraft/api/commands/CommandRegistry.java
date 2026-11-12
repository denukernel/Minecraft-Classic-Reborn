package com.mcraft.api.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.chat.commands.Command;
import net.classicremastered.minecraft.chat.commands.CommandManager;
import net.classicremastered.minecraft.player.Player;

public final class CommandRegistry {
    @FunctionalInterface
    public interface Executor {
        void run(Minecraft mc, Player sender, String[] args);
    }

    public void register(Minecraft mc, String name, String usage, Executor exec) {
        CommandManager.register(
                new Command() {
                    public String getName() {
                        return name;
                    }

                    public String getUsage() {
                        return usage;
                    }

                    public void execute(Minecraft client, Player sender, String[] args) {
                        exec.run(client, sender, args);
                    }
                });
    }
}
