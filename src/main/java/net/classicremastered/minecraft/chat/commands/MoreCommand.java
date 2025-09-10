package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

public class MoreCommand implements Command {
    @Override public String getName()  { return "more"; }
    @Override public String getUsage() { return "/more"; }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || sender == null || mc.hud == null) return;
        boolean ok = ChatLists.more(mc, sender, "/more");
        if (!ok) mc.hud.addChat("&7No more lines.");
    }
}
