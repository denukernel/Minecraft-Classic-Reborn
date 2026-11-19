package net.classicremastered.minecraft.mob;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.StairBlock;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

public final class Builder extends Worker {
    private static final long serialVersionUID = 1L;
    public static final short REGISTRY_ID = 93;
    transient BuilderAI.Project project;
    private transient int heldBlock, heldSince, heldUntil;
    private transient List<Site> finished = new ArrayList<>();

    record Site(int x, int y, int z, int width, int height) {
        boolean contains(Pos pos) {
            return pos.x() >= x && pos.x() < x + width && pos.y() >= y && pos.y() < y + height && pos.z() >= z
                    && pos.z() < z + width;
        }

        Pos entrance() {
            return new Pos(x + width / 2, y, z);
        }

        boolean isStairTower(Level level) {
            if (width != 6 || height != 6) return false;
            for (int step = 0; step < 4; step++) {
                if (level.getTile(x, y + step, z + step) != StairBlock.id(2, 3, false))
                    return false;
            }
            return level.getTile(x + 1, y + 3, z + 3) == Block.COBBLESTONE.id;
        }
    }

    public Builder(Level level, float x, float y, float z) {
        super(level, x, y, z, "Builder");
        ai = new BuilderAI();
    }

    public int displayedBlock() {
        return tickCount <= heldUntil ? heldBlock : 0;
    }

    boolean prepareBlock(int block) {
        if (displayedBlock() != block) {
            heldBlock = block;
            heldSince = tickCount;
        }
        heldUntil = tickCount + 7;
        return tickCount > heldSince;
    }

    void rememberSite(BuilderAI.Project site) {
        finished.add(new Site(site.x, site.y, site.z, site.width(), site.design.height));
    }

    static List<Site> sitesNear(Mob mob, float range) {
        List<Site> sites = new ArrayList<>();
        for (Object entity : mob.level.blockMap.all) {
            if (!(entity instanceof Builder builder)) continue;
            for (Site site : builder.finished) {
                if (mob.distanceTo(site.x + site.width * .5f, site.y, site.z + site.width * .5f)
                                <= range
                        && !sites.contains(site)) sites.add(site);
            }
        }
        return sites;
    }

    boolean lastBuiltHouse() {
        return !finished.isEmpty() && finished.get(finished.size() - 1).width() == 5;
    }

    boolean protects(Pos pos) {
        if (project != null
                && new Site(project.x, project.y, project.z, project.width(), project.design.height)
                        .contains(pos))
            return true;
        for (Site site : finished)
            if (site.contains(pos))
                return true;
        return false;
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        if (project != null)
            tag.put(project.save());
        ListTag sites = new ListTag("FinishedSites", (byte) 10);
        for (Site site : finished) {
            CompoundTag record = new CompoundTag(null);
            record.putInt("X", site.x());
            record.putInt("Y", site.y());
            record.putInt("Z", site.z());
            record.putInt("Width", site.width());
            record.putInt("Height", site.height());
            sites.add(record);
        }
        tag.put(sites);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        project =
                tag.hasKey("BuildProject")
                        ? BuilderAI.Project.load(tag.getCompound("BuildProject"))
                        : null;
        finished = new ArrayList<>();
        for (var entry : tag.getList("FinishedSites").list) {
            if (!(entry instanceof CompoundTag site))
                continue;
            int width = site.getInt("Width"), height = site.getInt("Height");
            if (width > 0 && width <= 6 && height > 0 && height <= 6) {
                finished.add(
                        new Site(
                                site.getInt("X"),
                                site.getInt("Y"),
                                site.getInt("Z"),
                                width,
                                height));
            }
        }
    }
}
