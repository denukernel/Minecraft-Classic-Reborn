package net.classicremastered.minecraft.level.itemstack;

import java.util.Locale;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.structure.DesertPyramid;
import net.classicremastered.minecraft.player.Player;

public final class StructureLocatorItem extends Item {
    public enum Filter {
        ALL(227, "", "structure_locator"),
        PYRAMID(228, "Desert Pyramid", "structure_locator_pyramid"),
        HOUSE(229, "House", "structure_locator_house"),
        TOWER(230, "Cobblestone Tower", "structure_locator_tower");

        public final int id;
        final String label, texture;

        Filter(int id, String label, String texture) {
            this.id = id;
            this.label = label;
            this.texture = texture;
        }
    }

    record Location(String label, int x, int y, int z) {}

    private final Filter filter;

    StructureLocatorItem(Filter filter) {
        super(
                filter.id,
                "Structure Locator" + (filter == Filter.ALL ? "" : " (" + filter.label + ")"),
                "/items/" + filter.texture + ".png",
                1);
        this.filter = filter;
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null
                || level == null
                || player.minecraft == null
                || player.minecraft.hud == null) return;
        if (level.isNetworkMode()) {
            player.minecraft.hud.addChat("&eStructure scanning is available in local worlds.");
            return;
        }
        Location found = scan(level, player.x, player.z);
        level.playSound("random/click", player, .6f, 1.4f);
        player.minecraft.hud.addChat(
                found == null
                        ? "&eNo matching structure in range."
                        : String.format(
                                Locale.ROOT,
                                "&a%s &fat %d %d %d &7(%.0f blocks away)",
                                found.label,
                                found.x,
                                found.y,
                                found.z,
                                Math.hypot(found.x - player.x, found.z - player.z)));
    }

    Location scan(Level level, float x, float z) {
        Location best = null;
        if (filter == Filter.ALL || filter == Filter.HOUSE) {
            for (var house : level.generatedHouses) {
                best =
                        nearer(
                                best,
                                new Location(
                                        Filter.HOUSE.label,
                                        house.x(),
                                        house.floor() + 1,
                                        house.z()),
                                x,
                                z);
            }
        }
        if (filter == Filter.ALL || filter == Filter.TOWER) {
            for (var tower : level.cobblestoneTowers) {
                best =
                        nearer(
                                best,
                                new Location(Filter.TOWER.label, tower.x(), tower.y(), tower.z()),
                                x,
                                z);
            }
        }
        if (level.biomeSeed != null && (filter == Filter.ALL || filter == Filter.PYRAMID)) {
            int cx = Math.floorDiv((int) Math.floor(x), 64),
                    cz = Math.floorDiv((int) Math.floor(z), 64);
            for (int dz = -20; dz <= 20; dz++) {
                for (int dx = -20; dx <= 20; dx++) {
                    var site =
                            DesertPyramid.site(
                                    level.biomeSeed,
                                    level.depth,
                                    cx + dx,
                                    cz + dz,
                                    level.flatBiomes);
                    if (site == null
                            || !(level instanceof LevelInfiniteTerrain)
                                    && (!level.isInBounds(site.x(), site.y(), site.z())
                                            || !level.isInBounds(
                                                    site.x() + 20, site.y(), site.z() + 20)))
                        continue;
                    best =
                            nearer(
                                    best,
                                    new Location(
                                            Filter.PYRAMID.label,
                                            site.x() + 10,
                                            site.y() + 1,
                                            site.z()),
                                    x,
                                    z);
                }
            }
        }
        return best;
    }

    private static Location nearer(Location best, Location candidate, float x, float z) {
        double distance = Math.hypot(candidate.x - x, candidate.z - z);
        return distance <= 1280 && (best == null || distance < Math.hypot(best.x - x, best.z - z))
                ? candidate
                : best;
    }
}
