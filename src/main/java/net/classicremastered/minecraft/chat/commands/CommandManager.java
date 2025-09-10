package net.classicremastered.minecraft.chat.commands;

import java.util.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

/**
 * Central registry and dispatcher for all commands.
 */
public class CommandManager {
    private static final Map<String, Command> commands = new LinkedHashMap<>();
    private static volatile boolean bootstrapped = false;

    public static synchronized void ensureBootstrapped(Minecraft mc) {
        if (bootstrapped) return;
        try {
            // This calls your registry bootstrapper (add built-ins here too if needed)
            net.classicremastered.minecraft.chat.CommandRegistry.bootstrap(mc);
        } finally {
            bootstrapped = true;
        }
    }

    public static void register(Command cmd) {
        if (cmd == null) return;
        String key = cmd.getName().toLowerCase(Locale.ROOT);
        commands.put(key, cmd);

        if (cmd instanceof AliasedCommand aliased) {
            for (String a : aliased.getAliases()) {
                if (a != null && !a.isEmpty()) {
                    commands.put(a.toLowerCase(Locale.ROOT), cmd);
                }
            }
        }
    }

    public static Set<String> getRegisteredNames() {
        return commands.keySet();
    }

    public static Command get(String name) {
        return (name == null) ? null : commands.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean handleCommand(Minecraft mc, Player sender, String message) {
        ensureBootstrapped(mc);

        if (message == null || message.isEmpty() || !message.startsWith("/")) return false;

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0) return false;

        String name = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command cmd = commands.get(name);
        if (cmd != null) {
            try {
                cmd.execute(mc, sender, args);
            } catch (Throwable t) {
                if (mc != null && mc.hud != null)
                    mc.hud.addChat("&cCommand error: &7" + t.getClass().getSimpleName());
            }
        } else {
            if (mc != null && mc.hud != null)
                mc.hud.addChat("&cUnknown command. Try &e/help");
        }
        return true;
    }

    /** Expose all distinct command instances (aliases folded). */
    public static Collection<Command> getAllCommands() {
        return new LinkedHashSet<>(commands.values());
    }
}
