package net.classicremastered.minecraft.chat.commands;

import java.util.Locale;
import java.util.Random;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.structure.Structure;
import net.classicremastered.minecraft.level.structure.StructureRegistry;
import net.classicremastered.minecraft.player.Player;

/**
 * /locatestructure <id>
 * Finds the nearest suitable placement for a registered structure.
 */
public class LocateStructureCommand implements Command {
    @Override public String getName() { return "locatestructure"; }
    @Override public String getUsage() { return "/locatestructure <id>"; }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null || sender == null) {
            hud(mc, "&cNo level/player loaded.");
            return;
        }
        if (args.length < 1) {
            hud(mc, "&cUsage: " + getUsage());
            return;
        }

        String id = args[0].toLowerCase(Locale.ROOT);
        Level lvl = mc.level;

        Structure target = null;
        for (var e : StructureRegistry.INSTANCE.getEntries()) {
            if (e.structure.id().toLowerCase(Locale.ROOT).equals(id)) {
                target = e.structure;
                break;
            }
        }
        if (target == null) {
            hud(mc, "&cUnknown structure id: &f" + id);
            return;
        }

        final int cell = 64;
        final int border = 8;
        Random rand = lvl.random;

        int bestX=-1, bestY=-1, bestZ=-1;
        double bestD2 = Double.MAX_VALUE;

        for (int gx = border; gx < lvl.width - border; gx += cell) {
            for (int gz = border; gz < lvl.height - border; gz += cell) {
                int x = gx + 8 + rand.nextInt(Math.max(1, cell - 16));
                int z = gz + 8 + rand.nextInt(Math.max(1, cell - 16));
                int y = lvl.getHighestTile(x, z);

                if (target.canPlace(lvl, x, y, z)) {
                    double dx = x - sender.x;
                    double dy = y - sender.y;
                    double dz = z - sender.z;
                    double d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        bestX = x; bestY = y; bestZ = z;
                    }
                }
            }
        }

        if (bestX == -1) {
            hud(mc, "&eNo suitable placement found for &f" + id);
        } else {
            hud(mc, "&aNearest " + id + " at &f" +
                bestX + " " + bestY + " " + bestZ +
                " &7(" + String.format("%.1f", Math.sqrt(bestD2)) + " blocks)");
        }
    }

    private static void hud(Minecraft mc, String msg) {
        if (mc != null && mc.hud != null) mc.hud.addChat(msg);
    }
}
