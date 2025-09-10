package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public class DifficultyCommand implements Command, AliasedCommand {

    @Override
    public String getName() {
        return "difficulty";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "diff" };
    }

    @Override
    public String getUsage() {
        return "/difficulty [peaceful|easy|normal|hard]";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.settings == null) return;

        if (args.length == 0) {
            mc.hud.addChat("&7Difficulty is &e" + mc.settings.difficulty.getLabel());
            return;
        }

        String arg = args[0].toUpperCase();
        Difficulty newDiff;
        if (arg.startsWith("PEACE")) newDiff = Difficulty.PEACEFUL;
        else if (arg.startsWith("EAS")) newDiff = Difficulty.EASY;
        else if (arg.startsWith("NOR")) newDiff = Difficulty.NORMAL;
        else if (arg.startsWith("HAR")) newDiff = Difficulty.HARD;
        else {
            mc.hud.addChat("&cUnknown difficulty. Use: peaceful, easy, normal, hard");
            return;
        }

        mc.settings.difficulty = newDiff;
        // persist to disk
        try {  // piggyback existing save mechanics
            java.lang.reflect.Method m = mc.settings.getClass().getDeclaredMethod("toggleSetting", int.class, int.class);
            m.setAccessible(true);
            // no-op call just to trigger save() via toggleSetting’s save; if you prefer, expose save()
            // but we’ll just call save() reflectively:
        } catch (Throwable ignored) {}

        // Call save() reflectively (since it's private)
        try {
            java.lang.reflect.Method save = mc.settings.getClass().getDeclaredMethod("save");
            save.setAccessible(true);
            save.invoke(mc.settings);
        } catch (Throwable ignored) {}

        mc.hud.addChat("&7Difficulty set to &e" + newDiff.getLabel());
    }
}
