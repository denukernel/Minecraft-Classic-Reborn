package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.player.Player;

public final class SpawnpointCommand implements Command {
    @Override
    public String getName() {
        return "spawnpoint";
    }

    @Override
    public String getUsage() {
        return "/spawnpoint [x y z]";
    }

    private static int coordinate(String value, float origin) {
        double result =
                value.startsWith("~")
                        ? origin
                                + (value.length() == 1 ? 0 : Double.parseDouble(value.substring(1)))
                        : Double.parseDouble(value);
        if (!Double.isFinite(result) || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
            throw new NumberFormatException();
        return (int) Math.floor(result);
    }

    @Override
    public void execute(Minecraft mc, Player player, String[] args) {
        if (mc == null || mc.level == null || player == null) return;
        if (mc.isOnline()) {
            mc.hud.addChat("&cSpawnpoint changes are available in local worlds.");
            return;
        }
        if (args.length != 0 && args.length != 3) {
            mc.hud.addChat("&cUsage: " + getUsage());
            return;
        }
        try {
            int x = coordinate(args.length == 0 ? "~" : args[0], player.x);
            int y = coordinate(args.length == 0 ? "~" : args[1], player.bb.y0);
            int z = coordinate(args.length == 0 ? "~" : args[2], player.z);
            Level level = mc.level;
            boolean infinite =
                    level instanceof LevelInfiniteFlat || level instanceof LevelInfiniteTerrain;
            if (y < 0
                    || y >= level.depth
                    || !infinite && (x < 0 || x >= level.width || z < 0 || z >= level.height)) {
                mc.hud.addChat("&cSpawnpoint is outside world bounds.");
                return;
            }
            level.xSpawn = x;
            level.ySpawn = y;
            level.zSpawn = z;
            level.rotSpawn = player.yRot;
            mc.hud.addChat("&aSpawnpoint set to &f" + x + " " + y + " " + z);
        } catch (NumberFormatException exception) {
            mc.hud.addChat("&cInvalid coordinates. Usage: " + getUsage());
        }
    }
}
