package net.classicremastered.minecraft.chat.commands;

import java.util.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

/**
 * /help                -> paged list of all commands
 * /help <page>         -> jump to page
 * /help <command>      -> detailed usage
 */
public class HelpCommand implements Command {

    @Override public String getName()  { return "help"; }
    @Override public String getUsage() { return "/help [command|page]"; }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.hud == null) return;
        CommandManager.ensureBootstrapped(mc);

        if (args != null && args.length >= 1 && !isInt(args[0])) {
            showDetailed(mc, args[0]);
            return;
        }

        int page = 1;
        if (args != null && args.length >= 1 && isInt(args[0])) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (Throwable ignored) {}
        }
        showIndex(mc, page);
    }

    private void showDetailed(Minecraft mc, String name) {
        Command c = CommandManager.get(name);
        if (c == null) {
            mc.hud.addChat("&cNo such command: &7" + name);
            return;
        }
        mc.hud.addChat("&eCommand: &7/" + c.getName().toLowerCase(Locale.ROOT));

        if (c instanceof AliasedCommand aliased) {
            String[] aliases = aliased.getAliases();
            if (aliases != null && aliases.length > 0) {
                mc.hud.addChat("&eAliases: &7" + String.join(", ", aliases));
            }
        }

        mc.hud.addChat("&eUsage: &7" + c.getUsage());
    }

    private void showIndex(Minecraft mc, int page) {
        List<Command> list = new ArrayList<>(CommandManager.getAllCommands());
        list.sort(Comparator.comparing(c -> c.getName().toLowerCase(Locale.ROOT)));

        final int PAGE_SIZE = 6;
        int pages = Math.max(1, (int)Math.ceil(list.size() / (double)PAGE_SIZE));
        int p = Math.min(Math.max(1, page), pages);

        mc.hud.addChat("&eAvailable commands &7(page " + p + "/" + pages + ")");

        if (list.isEmpty()) {
            mc.hud.addChat("&8(none)");
            return;
        }

        int start = (p - 1) * PAGE_SIZE, end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            Command c = list.get(i);
            mc.hud.addChat("/" + c.getName().toLowerCase(Locale.ROOT) + " &7- " + c.getUsage());
        }
    }

    private static boolean isInt(String s) {
        try { Integer.parseInt(s); return true; } catch (Throwable t) { return false; }
    }
}
