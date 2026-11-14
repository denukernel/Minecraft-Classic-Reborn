package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.bot.Bot.Type;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class BotCommand implements Command, AliasedCommand {
    private static final String USAGE = "/bot spawn [type] [count] | /bot list";
    private static final int MAX_COUNT = 20;
    private static final int[] SPAWN_HEIGHT_OFFSETS = {0, 1, -1, 2, -2};
    private static final List<String> COUNT_SUGGESTIONS = List.of("1", "2", "3", "5", "10", "20");

    @Override
    public String getName() {
        return "bot";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"bots"};
    }

    @Override
    public String getUsage() {
        return USAGE;
    }

    @Override
    public List<String> suggest(Minecraft mc, Player sender, String[] args, int argument) {
        if (argument == 0) return List.of("spawn", "list");
        if (!"spawn".equalsIgnoreCase(args[0])) return List.of();
        if (argument == 1) return Type.ids();
        if (argument == 2) {
            if (Type.fromId(args[1]) != null) return COUNT_SUGGESTIONS;
            if (isCount(args[1])) return Type.ids();
        }
        return List.of();
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (args.length == 0 || args.length == 1 && "list".equalsIgnoreCase(args[0])) {
            showList(mc);
            return;
        }
        if (!"spawn".equalsIgnoreCase(args[0]) || args.length > 3) {
            hud(mc, "&cusage " + USAGE);
            return;
        }
        if (mc == null || mc.level == null || mc.level.blockMap == null || sender == null) {
            hud(mc, "&cno level or player loaded.");
            return;
        }

        int count = 1;
        Type type = Type.FOLLOW;
        if (args.length >= 2) {
            boolean countFirst = isCount(args[1]);
            int typeIndex = countFirst ? 2 : 1;
            int countIndex = countFirst ? 1 : 2;
            if (args.length > typeIndex) {
                type = Type.fromId(args[typeIndex]);
                if (type == null) {
                    hud(
                            mc,
                            "&cunknown bot type. &eavailable bots &f"
                                    + String.join(", ", Type.ids()));
                    return;
                }
            }
            if (args.length > countIndex) {
                try {
                    count = Math.max(1, Math.min(MAX_COUNT, Integer.parseInt(args[countIndex])));
                } catch (NumberFormatException ex) {
                    hud(mc, "&cbot count must be a whole number.");
                    return;
                }
            }
        }

        int spawned = spawnNearby(mc.level, sender, type, count);
        hud(mc, "&aspawned &f" + spawned + " of " + count + " " + type.id() + "&a bot(s).");
        if (spawned < count) hud(mc, "&ethere was limited room to spawn bots nearby.");
    }

    private static boolean isCount(String token) {
        return token.matches("[+-]?\\d+");
    }

    private static int spawnNearby(Level level, Player sender, Type type, int count) {
        int centerX = (int) Math.floor(sender.x);
        int centerY = (int) Math.floor(sender.bb.y0 + .00001f);
        int centerZ = (int) Math.floor(sender.z);
        int spawned = 0;
        for (int radius = 1; radius <= 4 && spawned < count; radius++) {
            for (int dx = -radius; dx <= radius && spawned < count; dx++) {
                for (int dz = -radius; dz <= radius && spawned < count; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    if (spawnInColumn(level, sender, type, centerX + dx, centerY, centerZ + dz))
                        spawned++;
                }
            }
        }
        return spawned;
    }

    private static boolean spawnInColumn(
            Level level, Player sender, Type type, int x, int centerY, int z) {
        for (int offset : SPAWN_HEIGHT_OFFSETS) {
            int y = centerY + offset;
            if (!level.isInBounds(x, y, z) || y + 2 > level.depth) continue;
            AABB bounds = new AABB(x + .2f, y, z + .2f, x + .8f, y + 1.8f, z + .8f);
            if (!level.isSolidTile(x, y - 1, z)
                    || !level.getCubes(bounds).isEmpty()
                    || level.containsAnyLiquid(bounds)
                    || !level.isFree(bounds)
                    || bounds.intersects(sender.bb)) continue;

            Bot bot = new Bot(level, x + .5f, y + .9f, z + .5f, type);
            bot.bb.move(0, y - bot.bb.y0, 0);
            level.addEntity(bot);
            return true;
        }
        return false;
    }

    private static void showList(Minecraft mc) {
        hud(mc, "&eavailable bots &f" + String.join(", ", Type.ids()));
        if (mc == null || mc.level == null || mc.level.blockMap == null) return;
        EnumMap<Type, Integer> counts = new EnumMap<>(Type.class);
        for (Entity entity : mc.level.blockMap.all) {
            if (entity instanceof Bot bot && !bot.removed && bot.health > 0)
                counts.merge(bot.type(), 1, Integer::sum);
        }
        List<String> active = new ArrayList<>();
        counts.forEach((type, count) -> active.add(type.id() + " (" + count + ")"));
        hud(mc, "&eactive bots &f" + (active.isEmpty() ? "none" : String.join(", ", active)));
        hud(mc, "&7usage " + USAGE);
    }

    private static void hud(Minecraft mc, String message) {
        if (mc != null && mc.hud != null) mc.hud.addChat(message);
    }
}
