// com/mojang/minecraft/chat/CommandRegistry.java
package net.classicremastered.minecraft.chat;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.chat.commands.AISpeedCommand;
import net.classicremastered.minecraft.chat.commands.CommandManager;
import net.classicremastered.minecraft.chat.commands.GameModeCommand;
import net.classicremastered.minecraft.chat.commands.GiveCommand;
import net.classicremastered.minecraft.chat.commands.HelpCommand;
import net.classicremastered.minecraft.chat.commands.LocateStructureCommand;
import net.classicremastered.minecraft.chat.commands.MoreCommand;
import net.classicremastered.minecraft.chat.commands.SpawnMobCommand;
import net.classicremastered.minecraft.chat.commands.TeleportCommand;
import net.classicremastered.minecraft.chat.commands.TimeCommand;

public final class CommandRegistry {
    private CommandRegistry() {}

    public static void bootstrap(Minecraft mc) {
        CommandManager.register(new HelpCommand());
        CommandManager.register(new TimeCommand());
        CommandManager.register(new GiveCommand());
        CommandManager.register(new LocateStructureCommand());
        CommandManager.register(new GameModeCommand());
        CommandManager.register(new SpawnMobCommand());
        CommandManager.register(new MoreCommand());
        CommandManager.register(new net.classicremastered.minecraft.chat.commands.DifficultyCommand());
        CommandManager.register(new TeleportCommand());
        CommandManager.register(new net.classicremastered.minecraft.chat.commands.MobVoteCommand());
        CommandManager.register(new net.classicremastered.minecraft.chat.commands.CoinsCommand());
        CommandManager.register(new net.classicremastered.minecraft.chat.commands.ReputationCommand());

        CommandManager.register(new AISpeedCommand());
        if (mc != null && mc.developer && mc.hud != null) {
            mc.hud.addChat("&7[dev] /aispeed enabled");
        }
    }
}
