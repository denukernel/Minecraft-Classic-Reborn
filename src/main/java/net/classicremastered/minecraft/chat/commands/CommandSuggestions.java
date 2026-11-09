package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.level.structure.StructureRegistry;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.player.Player;

import java.util.*;

/** Registry-backed, caret-aware completion without changing command execution. */
public final class CommandSuggestions {
    private CommandSuggestions() {}

    public record Suggestion(int start, int end, String text, String usage) {}

    public static List<Suggestion> complete(Minecraft mc, Player player, String input, int caret) {
        if (input == null || !input.startsWith("/") || caret < 1 || caret > input.length())
            return List.of();
        int start = caret, end = caret;
        while (start > 1 && !Character.isWhitespace(input.charAt(start - 1))) start--;
        while (end < input.length() && !Character.isWhitespace(input.charAt(end))) end++;
        String prefix = input.substring(start, caret).toLowerCase(Locale.ROOT);
        List<String> choices;
        String usage = "";
        if (start == 1) choices = new ArrayList<>(CommandManager.getRegisteredNames());
        else {
            String[] before = input.substring(1, start).trim().split("\\s+");
            Command cmd = CommandManager.get(before[0]);
            if (cmd == null) return List.of();
            usage = cmd.getUsage();
            String[] args = Arrays.copyOfRange(before, 1, before.length + 1);
            args[args.length - 1] = prefix;
            choices = cmd.suggest(mc, player, args, args.length - 1);
        }
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(choices);
        List<Suggestion> result = new ArrayList<>();
        for (String choice : sorted)
            if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String hint = usage;
                if (start == 1) {
                    Command c = CommandManager.get(choice);
                    hint = c == null ? "" : c.getUsage();
                }
                result.add(new Suggestion(start, end, choice, hint));
            }
        return result;
    }

    public static String usage(String input) {
        if (input == null || !input.startsWith("/")) return "";
        String name = input.substring(1).split("\\s+", 2)[0];
        Command c = CommandManager.get(name);
        return c == null ? "" : c.getUsage();
    }

    public static List<String> arguments(Command command, String[] args, int index) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (name.equals("spawnmob") && (index == 0 || index == 5)) {
            MobRegistry.bootstrapDefaults();
            for (String mob : MobRegistry.displayNames())
                if (!Set.of(
                                "bot",
                                "playerclone",
                                "player",
                                "mob",
                                "entity",
                                "human",
                                "humanoid",
                                "humanoidmob",
                                "quadrupedmob")
                        .contains(mob.toLowerCase(Locale.ROOT))) out.add(mob);
        }
        if (index == 0)
            switch (name) {
                case "help":
                    out.addAll(CommandManager.getRegisteredNames());
                    break;
                case "difficulty":
                    out.addAll(List.of("peaceful", "easy", "normal", "hard"));
                    break;
                case "gamemode":
                    out.addAll(List.of("creative", "survival"));
                    break;
                case "time":
                    out.addAll(List.of("set", "day", "noon", "night", "midnight"));
                    break;
                case "coins":
                case "reputation":
                    out.addAll(List.of("set", "add", "remove"));
                    break;
                case "bot":
                    out.add("spawn");
                    break;
                case "aispeed":
                    out.addAll(
                            List.of(
                                    "show", "reset", "scale=", "walk=", "air=", "swim=", "lava=",
                                    "runmul="));
                    break;
                case "locatestructure":
                    for (var entry : StructureRegistry.INSTANCE.getEntries())
                        out.add(entry.structure.id());
                    break;
                case "give":
                    out.add("help");
                    ItemRegistry.bootstrap();
                    for (Block block : Block.blocks)
                        if (block != null
                                && block != Block.WATER
                                && block != Block.LAVA
                                && block != Block.FIRE
                                && block.id > 0
                                && block.name != null)
                            out.add(block.name.toLowerCase(Locale.ROOT).replace(' ', '_'));
                    for (Item item : Item.items)
                        if (item != null && item != Item.EMPTY && item.name != null)
                            out.add(item.name.toLowerCase(Locale.ROOT).replace(' ', '_'));
                    break;
                default:
                    break;
            }
        if (name.equals("give") && index == 1 && args[0].equalsIgnoreCase("help"))
            out.addAll(List.of("blocks", "items", "all"));
        if (name.equals("time") && index == 1 && args[0].equalsIgnoreCase("set"))
            out.addAll(List.of("day", "noon", "night", "midnight"));
        if (name.equals("bot") && index == 2 && args[0].equalsIgnoreCase("spawn"))
            out.addAll(List.of("follow", "pvp", "expertpvp"));
        return out;
    }
}
