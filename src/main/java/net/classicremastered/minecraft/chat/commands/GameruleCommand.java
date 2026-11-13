package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

import java.util.List;

public final class GameruleCommand implements Command, AliasedCommand {
    public static final List<String> RULES =
            List.of("doDayNightCycle", "doMobSpawning", "doMobLoot", "keepInventory");
    private static final String KEEP_INVENTORY_ALIAS = "keep_inventory";

    public String getName() {
        return "gamerule";
    }

    public String[] getAliases() {
        return new String[] {"gamerules"};
    }

    public String getUsage() {
        return "/gamerule <rule> [true|false]";
    }

    public static boolean value(Level level, String rule, Boolean update) {
        switch (rule.toLowerCase(java.util.Locale.ROOT)) {
            case "dodaynightcycle":
                if (update != null) level.doDayNightCycle = update;
                return level.doDayNightCycle;
            case "domobspawning":
                if (update != null) level.doMobSpawning = update;
                return level.doMobSpawning;
            case "domobloot":
                if (update != null) level.doMobLoot = update;
                return level.doMobLoot;
            case "keepinventory":
            case KEEP_INVENTORY_ALIAS:
                if (update != null) level.keepInventory = update;
                return level.keepInventory;
            default:
                throw new IllegalArgumentException("unknown gamerule");
        }
    }

    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null || mc.hud == null) return;
        if (args.length == 0) {
            mc.hud.addChat("&7" + String.join(", ", RULES));
            return;
        }
        if (args.length > 2
                || args.length == 2
                        && !args[1].equalsIgnoreCase("true")
                        && !args[1].equalsIgnoreCase("false")) {
            mc.hud.addChat("&c" + getUsage());
            return;
        }
        try {
            boolean result =
                    value(mc.level, args[0], args.length == 2 ? Boolean.valueOf(args[1]) : null);
            mc.hud.addChat("&7" + args[0] + " = &e" + result);
        } catch (IllegalArgumentException ex) {
            mc.hud.addChat("&cunknown gamerule. " + getUsage());
        }
    }

    public List<String> suggest(Minecraft mc, Player player, String[] args, int index) {
        return index == 0 ? RULES : index == 1 ? List.of("true", "false") : List.of();
    }
}
