package net.classicremastered.minecraft.chat.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.player.Player;

public class MobsCommand implements Command, AliasedCommand {

    @Override
    public String getName() {
        return "mobs";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "mob" };
    }

    @Override
    public String getUsage() {
        return "/mobs [page]";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.hud == null || sender == null) return;

        try {
            MobRegistry.bootstrapDefaults();
        } catch (Throwable ignored) {}

        int page = 1;
        if (args != null && args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {}
        }

        String[] names = MobRegistry.allNames();
        List<String> sortedNames = new ArrayList<>(Arrays.asList(names));
        sortedNames.sort((n1, n2) -> {
            int id1 = MobRegistry.idOfName(n1);
            int id2 = MobRegistry.idOfName(n2);
            return Integer.compare(id1, id2);
        });

        List<String> lines = new ArrayList<>();
        for (String name : sortedNames) {
            int id = MobRegistry.idOfName(name);
            lines.add("&7" + id + ": &f" + name);
        }

        List<String> wrapped = ChatLists.wrapLines(mc.fontRenderer, lines, ChatLists.DEFAULT_MAX_PIXELS);
        ChatLists.showPaged(mc, sender, "&eMobs", wrapped, page, ChatLists.DEFAULT_PAGE_SIZE, "/more");
    }
}
