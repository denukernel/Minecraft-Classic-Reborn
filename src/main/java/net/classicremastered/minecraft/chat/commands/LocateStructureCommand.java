package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.structure.Structure;
import net.classicremastered.minecraft.level.structure.StructureRegistry;
import net.classicremastered.minecraft.player.Player;

import java.util.Locale;
import java.util.Random;

/** /locatestructure <id> Finds the nearest suitable placement for a registered structure. */
public class LocateStructureCommand implements Command {
    @Override
    public String getName() {
        return "locatestructure";
    }

    @Override
    public String getUsage() {
        return "/locatestructure <id>";
    }

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
        StructureRegistry.INSTANCE.bootstrapDefaults();
        for (var e : StructureRegistry.INSTANCE.getEntries()) {
            if (e.structure.id().toLowerCase(Locale.ROOT).equals(id)) {
                target = e.structure;
                break;
            }
        }
        if (target == null) {
            hud(
                    mc,
                    "&cUnknown structure id: &f"
                            + id
                            + "&7. Valid ids: &f"
                            + String.join(
                                    ", ",
                                    StructureRegistry.INSTANCE.getEntries().stream()
                                            .map(entry -> entry.structure.id())
                                            .toList()));
            return;
        }

        if (target instanceof net.classicremastered.minecraft.level.structure.CobblestoneTower) {
            var nearest =
                    lvl.cobblestoneTowers.stream()
                            .min(
                                    java.util.Comparator.comparingDouble(
                                            site -> site.distanceSquared(sender.x, sender.z)))
                            .orElse(null);
            hud(mc, nearest == null ? "&eNo Cobblestone Tower has generated yet"
                    : "&aCobblestone Tower at &f" + nearest.x() + " " + nearest.y() + " " + nearest.z());
            return;
        }

        if (target instanceof net.classicremastered.minecraft.level.structure.DesertPyramid
                && lvl.biomeSeed != null) {
            net.classicremastered.minecraft.level.structure.DesertPyramid.Site nearest = null;
            double best = Double.MAX_VALUE;
            int cx = Math.floorDiv((int) sender.x, 64), cz = Math.floorDiv((int) sender.z, 64);
            for (int dx = -20; dx <= 20; dx++)
                for (int dz = -20; dz <= 20; dz++) {
                    var site =
                            net.classicremastered.minecraft.level.structure.DesertPyramid.site(
                                    lvl.biomeSeed, lvl.depth, cx + dx, cz + dz, lvl.flatBiomes);
                    if (site == null) continue;
                    if (!(lvl instanceof net.classicremastered.minecraft.level.LevelInfiniteTerrain)
                            && (site.x() < 0
                                    || site.z() < 0
                                    || site.x() + 20 >= lvl.width
                                    || site.z() + 20 >= lvl.height)) continue;
                    double distance =
                            Math.pow(site.x() - sender.x, 2) + Math.pow(site.z() - sender.z, 2);
                    if (distance < best) {
                        best = distance;
                        nearest = site;
                    }
                }
            hud(
                    mc,
                    nearest == null
                            ? "&eNo desert pyramid found nearby"
                            : "&aDesert pyramid at &f"
                                    + nearest.x()
                                    + " "
                                    + (nearest.y() + 1)
                                    + " "
                                    + nearest.z());
            return;
        }

        final int cell = 64;
        final int border = 8;
        Random rand = lvl.random;

        int bestX = -1, bestY = -1, bestZ = -1;
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
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        bestX = x;
                        bestY = y;
                        bestZ = z;
                    }
                }
            }
        }

        if (bestX == -1) {
            hud(mc, "&eNo suitable placement found for &f" + id);
        } else {
            hud(
                    mc,
                    "&aNearest "
                            + id
                            + " at &f"
                            + bestX
                            + " "
                            + bestY
                            + " "
                            + bestZ
                            + " &7("
                            + String.format("%.1f", Math.sqrt(bestD2))
                            + " blocks)");
        }
    }

    private static void hud(Minecraft mc, String msg) {
        if (mc != null && mc.hud != null) mc.hud.addChat(msg);
    }
}
