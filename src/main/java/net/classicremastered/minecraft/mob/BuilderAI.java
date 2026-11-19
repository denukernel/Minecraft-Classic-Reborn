package net.classicremastered.minecraft.mob;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.BuilderDoorBlock;
import net.classicremastered.minecraft.level.tile.StairBlock;
import net.classicremastered.minecraft.mob.BuilderPlacement.ScaffoldColumn;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

final class BuilderAI extends WorkerAI {
    private static final long serialVersionUID = 1L;
    private static final int STAIRS_TOWER_LAYOUT = 2;
    private transient int workDelay, siteDelay;
    private transient Vec3D buildStand;
    private transient Vec3D cleanupStand;
    private transient Pos buildTarget;
    private transient ScaffoldColumn workColumn;

    enum Design {
        TOWER("Cobblestone tower", 6, 6, false), COBBLE_HOUSE("Cobblestone house", 5, 4, false), WOOD_HOUSE(
                "Wooden house", 5, 4, true), SANDSTONE_HOUSE("Sandstone house", 5, 4, false);

        final String label;
        final int width, height;
        final boolean wooden;

        Design(String label, int width, int height, boolean wooden) {
            this.label = label;
            this.width = width;
            this.height = height;
            this.wooden = wooden;
        }

        int required() {
            return orders(STAIRS_TOWER_LAYOUT).size();
        }

        int material() {
            return switch (this) {
                case TOWER, COBBLE_HOUSE -> Block.COBBLESTONE.id;
                case WOOD_HOUSE -> Block.WOOD.id;
                case SANDSTONE_HOUSE -> Block.SANDSTONE.id;
            };
        }

        private List<Pos> orders(int layout) {
            if (this == TOWER && layout == STAIRS_TOWER_LAYOUT)
                return stairTower();
            List<Pos> blocks = new ArrayList<>();
            int width = this == TOWER ? 4 : this.width;
            int center = width / 2;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < width; z++) {
                    for (int x = 0; x < width; x++) {
                        boolean edge = x == 0 || z == 0 || x == width - 1 || z == width - 1;
                        if (z == 0 && x == center && y < 2)
                            continue;
                        if (this == TOWER) {
                            if (y == height - 1) {
                                if ((x == 0 || x == width - 1) && z == 0)
                                    blocks.add(new Pos(x, y, z));
                            } else if (edge || layout == 0 && y < height - 2
                                    && x == (y == 1 || y == 2 ? 2 : 1) && z == (y >= 2 ? 2 : 1)) {
                                blocks.add(new Pos(x, y, z));
                            }
                        } else if (y == height - 1
                                || edge && !(y == 1 && (x == 0 || x == width - 1) && z == center)) {
                            blocks.add(new Pos(x, y, z));
                        }
                    }
                }
                if (this == TOWER && layout != 0 && y < height - 2) {
                    // Attach each stair to a wall already placed at this height.
                    blocks.add(new Pos(y >= 2 ? 2 : 1, y, y == 1 || y == 2 ? 2 : 1));
                }
            }
            return blocks;
        }

        private List<Pos> stairTower() {
            List<Pos> blocks = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < width; z++) {
                    for (int x = 1; x < width; x++) {
                        boolean edge = x == 1 || x == width - 1 || z == 0 || z == width - 1;
                        boolean entrance = z == 0 && x == width / 2 && y < 2;
                        boolean stairLanding = x == 1 && z == 3;
                        if (y == 3 || y < 3 && edge && !entrance
                                || y == 4 && edge && !stairLanding
                                || y == 5 && edge && !stairLanding && (x + z) % 2 == 0)
                            blocks.add(new Pos(x, y, z));
                    }
                }
                // The west staircase rises southward to an opening in the deck parapet.
                for (int z = y; z < 4; z++)
                    blocks.add(new Pos(0, y, z));
            }
            return blocks;
        }
    }

    static final class Project {
        final Design design;
        final int x, y, z;
        List<Pos> orders;
        final List<Pos> scaffolds = new ArrayList<>();
        int layout;
        int next;
        int recoveredCobble;
        boolean entranceFinished;

        Project(Design design, int x, int y, int z) {
            this(design, x, y, z, STAIRS_TOWER_LAYOUT);
        }

        private Project(Design design, int x, int y, int z, int layout) {
            this.design = design;
            this.x = x;
            this.y = y;
            this.z = z;
            this.layout = layout;
            orders = design.orders(layout);
        }

        int width() {
            return design == Design.TOWER && layout < STAIRS_TOWER_LAYOUT ? 4 : design.width;
        }

        int nextMaterial() {
            Pos offset = orders.get(next);
            if (design == Design.TOWER && layout == STAIRS_TOWER_LAYOUT
                    && offset.x() == 0 && offset.y() == offset.z())
                return StairBlock.id(2, 3, false);
            return design.material();
        }

        Pos nextBlock() {
            Pos offset = orders.get(next);
            return new Pos(x + offset.x(), y + offset.y(), z + offset.z());
        }

        Pos entrance() {
            return new Pos(x + width() / 2, y, z);
        }

        boolean inside(float px, float pz) {
            return px >= x && px < x + width() && pz >= z && pz < z + width();
        }

        void updateLegacyTower(Level level) {
            if (design != Design.TOWER || layout != 0)
                return;
            List<Pos> updated = design.orders(1);
            for (int i = 0; i < next; i++) {
                Pos offset = orders.get(i);
                if (updated.contains(offset))
                    continue;
                Pos oldStep = new Pos(x + offset.x(), y + offset.y(), z + offset.z());
                if (level.getTile(oldStep.x(), oldStep.y(), oldStep.z()) == Block.COBBLESTONE.id
                        && level.setTile(oldStep.x(), oldStep.y(), oldStep.z(), 0))
                    recoveredCobble++;
            }
            orders = updated;
            next = 0;
            layout = 1;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag("BuildProject");
            tag.putString("Design", design.name());
            tag.putInt("X", x);
            tag.putInt("Y", y);
            tag.putInt("Z", z);
            tag.putInt("Next", next);
            tag.putInt("RecoveredCobble", recoveredCobble);
            tag.putInt("TowerLayout", layout);
            tag.putBoolean("EntranceFinished", entranceFinished);
            ListTag temporary = new ListTag("Scaffolds", (byte) 10);
            for (Pos pos : scaffolds) {
                CompoundTag block = new CompoundTag(null);
                block.putInt("X", pos.x());
                block.putInt("Y", pos.y());
                block.putInt("Z", pos.z());
                temporary.add(block);
            }
            tag.put(temporary);
            return tag;
        }

        static Project load(CompoundTag tag) {
            try {
                int layout = tag.getInt("TowerLayout");
                if (layout < 0 || layout > STAIRS_TOWER_LAYOUT)
                    return null;
                Project project = new Project(Design.valueOf(tag.getString("Design")), tag.getInt("X"),
                        tag.getInt("Y"), tag.getInt("Z"), layout);
                project.next = tag.getInt("Next");
                if (project.next < 0 || project.next > project.orders.size())
                    return null;
                project.entranceFinished = tag.getBoolean("EntranceFinished");
                project.recoveredCobble = Math.max(0, Math.min(2, tag.getInt("RecoveredCobble")));
                for (var entry : tag.getList("Scaffolds").list) {
                    if (!(entry instanceof CompoundTag block))
                        continue;
                    Pos pos = new Pos(block.getInt("X"), block.getInt("Y"), block.getInt("Z"));
                    if (pos.x() >= project.x - 1 && pos.x() <= project.x + project.width()
                            && pos.z() >= project.z - 1 && pos.z() <= project.z + project.width()
                            && pos.y() >= project.y - 1 && pos.y() <= project.y + project.design.height
                            && !project.scaffolds.contains(pos))
                        project.scaffolds.add(pos);
                }
                return project;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    @Override
    void work(Worker worker) {
        Builder builder = (Builder) worker;
        if (Block.BUILDER_SCAFFOLD == null || Block.BUILDER_DOOR == null) {
            builder.setActivity("Restart to enable new build blocks");
            return;
        }
        if (workDelay > 0)
            workDelay--;
        List<Builder> crew = crew(builder);
        if (crew.isEmpty())
            return;
        Builder leader = null;
        for (Builder member : crew) {
            if (member.project != null) {
                leader = member;
                break;
            }
            if (leader == null && member.onGround && !member.isInKnockback() && !member.isInWater()
                    && !member.isInLava())
                leader = member;
        }
        if (leader == null)
            return;
        if (leader.project == null) {
            if (leader != builder) {
                builder.setActivity(leader.activity());
                return;
            }
            if (siteDelay > 0) {
                siteDelay--;
                return;
            }
            siteDelay = 24;
            Design design = chooseDesign(crew, leader);
            if (design == null) {
                builder.setActivity(
                        crew.size() == 1
                                ? "Needs " + Design.TOWER.required() + " cobblestone for a tower"
                                : "Waiting for materials");
                return;
            }
            leader.project = findSite(builder, design);
            if (leader.project == null) {
                builder.setActivity("Looking for a clear site");
                return;
            }
        }
        build(builder, leader, crew);
    }

    private List<Builder> crew(Builder builder) {
        List<Builder> builders = new ArrayList<>();
        // World order gives nearby builders the same leader on each tick.
        for (Object entity : level.blockMap.all) {
            if (entity instanceof Builder other && builder.worksWith(other)
                    && builder.distanceToSqr(other) <= 15 * 15)
                builders.add(other);
        }
        return builders;
    }

    private static Design chooseDesign(List<Builder> crew, Builder leader) {
        int wood = 0, cobble = 0, sandstone = 0;
        for (Builder builder : crew) {
            Inventory pack = builder.pack();
            for (int slot = 0; slot < pack.totalSize(); slot++) {
                int id = pack.getId(slot), count = pack.getCount(slot);
                if (id == Block.LOG.id)
                    wood += 4 * count;
                else if (id == Block.WOOD.id)
                    wood += count;
                else if (id == Block.COBBLESTONE.id)
                    cobble += count;
                else if (id == Block.SANDSTONE.id)
                    sandstone += count;
            }
        }
        boolean hasTowerStock = cobble >= Design.TOWER.required();
        if (hasTowerStock && leader.lastBuiltHouse())
            return Design.TOWER;
        if (crew.size() > 1 && wood >= Design.WOOD_HOUSE.required())
            return Design.WOOD_HOUSE;
        if (crew.size() > 1 && sandstone >= Design.SANDSTONE_HOUSE.required())
            return Design.SANDSTONE_HOUSE;
        if (crew.size() > 1 && cobble >= Design.COBBLE_HOUSE.required())
            return Design.COBBLE_HOUSE;
        return hasTowerStock ? Design.TOWER : null;
    }

    private Project findSite(Builder builder, Design design) {
        int x = (int) Math.floor(builder.x), y = Math.round(builder.bb.y0), z = (int) Math.floor(builder.z);
        int center = design.width / 2;
        for (int distance = center + 3; distance <= 13; distance += 2) {
            for (int direction = 0; direction < 16; direction++) {
                double angle = direction * Math.PI / 8;
                int ox = x + (int) Math.round(Math.cos(angle) * distance) - center;
                int oz = z + (int) Math.round(Math.sin(angle) * distance) - center;
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    Project project = new Project(design, ox, y + dy, oz);
                    if (clear(project) && access(project, project.nextBlock()) != null)
                        return project;
                }
            }
        }
        return null;
    }

    private boolean clear(Project project) {
        for (int x = project.x; x < project.x + project.width(); x++) {
            for (int z = project.z; z < project.z + project.width(); z++) {
                if (!level.isInBounds(x, project.y + project.design.height - 1, z)
                        || !level.isSolidTile(x, project.y - 1, z))
                    return false;
                for (int y = project.y; y < project.y + project.design.height; y++) {
                    if (level.getTile(x, y, z) != 0)
                        return false;
                }
            }
        }
        return true;
    }

    private void build(Builder builder, Builder leader, List<Builder> crew) {
        Project project = leader.project;
        project.updateLegacyTower(level);
        if (project.next == project.orders.size()) {
            finish(builder, leader, project);
            return;
        }
        Pos pos = project.nextBlock();
        int raw = project.design.material();
        int placement = project.nextMaterial();
        int existing = level.getTile(pos.x(), pos.y(), pos.z());
        if (existing == placement) {
            project.next++;
            return;
        }
        if (existing != 0) {
            builder.setActivity("Construction site blocked");
            return;
        }
        Builder stock = supply(builder, crew, raw);
        if (stock == null && project.recoveredCobble == 0) {
            builder.setActivity(
                    "Needs " + (project.design.wooden ? "planks or logs" : Block.blocks[raw].name));
            return;
        }
        Vec3D contact = BuilderPlacement.face(level, eyes(), pos, false);
        if (contact == null || project.inside(builder.x, builder.z)
                || BuilderPlacement.inWay(builder, new Vec3D(builder.x, builder.bb.y0, builder.z), pos)) {
            reachPlacement(builder, project, pos);
            return;
        }
        builder.setActivity(project.design.label + " " + project.next + "/" + project.orders.size());
        aim(builder, contact);
        if (!builder.prepareBlock(placement) || workDelay > 0 || BuilderPlacement.occupied(level, pos))
            return;
        if (level.setTile(pos.x(), pos.y(), pos.z(), placement)) {
            if (project.recoveredCobble > 0)
                project.recoveredCobble--;
            else
                consume(stock, raw);
            builder.swingHand();
            level.playSound(Block.blocks[raw].stepsound.pool, builder, .55f, 1);
            workDelay = 7;
            project.next++;
        }
    }

    private void finish(Builder builder, Builder leader, Project project) {
        if (!project.entranceFinished) {
            Pos pos = project.entrance();
            if (BuilderDoorBlock.isInstalled(level, pos)) {
                project.entranceFinished = true;
            } else {
                Vec3D hit = BuilderPlacement.face(level, eyes(), pos, false);
                Vec3D feet = new Vec3D(builder.x, builder.bb.y0, builder.z);
                if (hit == null || project.inside(builder.x, builder.z)
                        || BuilderPlacement.inWay(builder, feet, pos)
                        || BuilderPlacement.inWay(builder, feet, pos.step(1))) {
                    reachPlacement(builder, project, pos);
                    return;
                }
                builder.setActivity("Fitting the wooden door");
                aim(builder, hit);
                if (!builder.prepareBlock(Block.BUILDER_DOOR.id) || workDelay > 0
                        || BuilderPlacement.occupied(level, pos)
                        || BuilderPlacement.occupied(level, pos.step(1)))
                    return;
                for (Object entity : level.findEntities(builder,
                        new AABB(project.x, project.y, project.z, project.x + project.width(),
                                project.y + project.design.height, project.z + project.width()))) {
                    if (entity instanceof Builder other && builder.worksWith(other)
                            && project.inside(other.x, other.z))
                        return;
                }
                if (!BuilderDoorBlock.install(level, pos)) {
                    builder.setActivity("Doorway blocked");
                    return;
                }
                builder.swingHand();
                workDelay = 7;
                project.entranceFinished = true;
            }
        }
        if (cleanupScaffolds(builder, project))
            return;
        leader.rememberSite(project);
        leader.project = null;
        leader.setActivity(project.design.label + " finished");
        ((BuilderAI) leader.ai).siteDelay = 100;
    }

    private void reachPlacement(Builder builder, Project project, Pos pos) {
        if (!pos.equals(buildTarget)) {
            buildTarget = pos;
            buildStand = null;
            workColumn = null;
        }
        if (workColumn == null) {
            if (buildStand == null)
                buildStand = access(project, pos);
            if (buildStand != null && approachStand(buildStand)) {
                builder.setActivity("Walking to the build site");
                return;
            }
            buildStand = null;
            workColumn = scaffoldSite(project, pos);
        }
        ScaffoldColumn column = workColumn;
        if (column == null) {
            if (descendScaffold(builder))
                builder.setActivity("Climbing down to another work position");
            else if (project.inside(builder.x, builder.z))
                exitFootprint(builder, project);
            else
                builder.setActivity("Looking for site access");
            return;
        }
        builder.setActivity("Climbing work scaffold");
        int y = column.baseY();
        while (y < column.goalY() && BuilderPlacement.scaffoldAt(level, new Pos(column.x(), y, column.z())))
            y++;
        if (y >= column.goalY()) {
            if (!approachStand(column.standAt(column.goalY())))
                workColumn = null;
            return;
        }
        Pos next = new Pos(column.x(), y, column.z());
        Vec3D hit = BuilderPlacement.face(level, eyes(), next, true);
        if (hit == null) {
            if (!approachStand(column.standAt(y)))
                workColumn = null;
            return;
        }
        aim(builder, hit);
        int scaffold = Block.BUILDER_SCAFFOLD.id;
        if (!builder.prepareBlock(scaffold) || workDelay > 0
                || level.getTile(next.x(), next.y(), next.z()) != 0)
            return;
        if (level.setTile(next.x(), next.y(), next.z(), scaffold)) {
            project.scaffolds.add(next);
            builder.swingHand();
            workDelay = 5;
        }
    }

    private void exitFootprint(Builder builder, Project project) {
        builder.setActivity("Stepping outside the build");
        Pos door = project.entrance();
        Vec3D exit = reachableStand(door.x(), project.y, door.z() - 1);
        if (exit != null && approachStand(exit))
            return;
        // A partial build may predate the outside-only placement rule. Reuse any
        // opening around its edge before trying the center of the planned entrance.
        for (int x = project.x - 1; x <= project.x + project.width(); x++) {
            for (int z = project.z - 1; z <= project.z + project.width(); z++) {
                if (project.inside(x + .5f, z + .5f))
                    continue;
                exit = reachableStand(x, project.y, z);
                if (exit != null && approachStand(exit))
                    return;
            }
        }
        builder.setActivity("Build exit blocked");
    }

    private void aim(Builder builder, Vec3D hit) {
        face(hit);
        builder.xd = builder.zd = 0;
        if (BuilderPlacement.onScaffold(builder))
            builder.yd = 0;
    }

    private ScaffoldColumn scaffoldSite(Project project, Pos target) {
        ScaffoldColumn best = null;
        float bestScore = Float.POSITIVE_INFINITY;
        for (int x = project.x - 1; x <= project.x + project.width(); x++) {
            for (int z = project.z - 1; z <= project.z + project.width(); z++) {
                if (project.inside(x + .5f, z + .5f))
                    continue;
                for (int floor = project.y - 1; floor <= project.y + 1; floor++) {
                    if (!level.isSolidTile(x, floor - 1, z))
                        continue;
                    int builtTo = floor;
                    while (project.scaffolds.contains(new Pos(x, builtTo, z))
                            && BuilderPlacement.scaffoldAt(level, new Pos(x, builtTo, z)))
                        builtTo++;
                    for (int feet = floor + 1; feet <= target.y() + 1; feet++) {
                        double dx = mob.x - x - .5, dz = mob.z - z - .5;
                        float score = (float) (dx * dx + dz * dz) * .6f + Math.max(0, feet - builtTo) * 5
                                + (feet - floor) * .2f;
                        if (score >= bestScore || !clearColumn(project, x, floor, z, feet))
                            continue;
                        Vec3D eye = new Vec3D(x + .5f, feet + mob.getEyeHeight(1), z + .5f);
                        if (BuilderPlacement.face(level, eye, target, false) == null
                                || reachableStand(x, floor, z) == null)
                            continue;
                        best = new ScaffoldColumn(x, floor, z, feet);
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private boolean clearColumn(Project project, int x, int base, int z, int feet) {
        if (!level.isInBounds(x, feet + 1, z))
            return false;
        for (int y = base; y <= feet + 1; y++) {
            int id = level.getTile(x, y, z);
            if (id != 0 && (id != Block.BUILDER_SCAFFOLD.id || !project.scaffolds.contains(new Pos(x, y, z))))
                return false;
        }
        return true;
    }

    private boolean cleanupScaffolds(Builder builder, Project project) {
        if (project.scaffolds.isEmpty())
            return false;
        builder.setActivity("Dismantling work scaffold");
        if (descendScaffold(builder))
            return true;
        project.scaffolds.removeIf(pos -> !BuilderPlacement.scaffoldAt(level, pos));
        List<Pos> exposed = new ArrayList<>();
        for (int index = project.scaffolds.size() - 1; index >= 0; index--) {
            Pos top = project.scaffolds.get(index);
            if (BuilderPlacement.scaffoldAt(level, top.step(1)) || scaffoldOccupied(top))
                continue;
            if (BuilderPlacement.withinBreakReach(level, eyes(), top)) {
                cleanupStand = null;
                aim(builder, BuilderPlacement.center(top));
                if (workDelay == 0 && level.setTile(top.x(), top.y(), top.z(), 0)) {
                    builder.swingHand();
                    if (level.particleEngine != null)
                        Block.BUILDER_SCAFFOLD.spawnBreakParticles(level, top.x(), top.y(), top.z(),
                                level.particleEngine);
                    project.scaffolds.remove(index);
                    workDelay = 5;
                }
                return true;
            }
            exposed.add(top);
        }
        if (cleanupStand != null && approachStand(cleanupStand))
            return true;
        cleanupStand = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (Pos top : exposed) {
            for (int x = top.x() - 2; x <= top.x() + 2; x++) {
                for (int z = top.z() - 2; z <= top.z() + 2; z++) {
                    for (int y = project.y - 1; y <= project.y + 1; y++) {
                        Vec3D stand = new Vec3D(x + .5f, y, z + .5f);
                        Vec3D eye = stand.add(0, builder.getEyeHeight(1), 0);
                        float distance = eyes().distanceSquared(eye);
                        if (distance >= nearest || !BuilderPlacement.withinBreakReach(level, eye, top)
                                || reachableStand(x, y, z) == null)
                            continue;
                        nearest = distance;
                        cleanupStand = stand;
                    }
                }
            }
        }
        if (cleanupStand != null)
            approachStand(cleanupStand);
        return !project.scaffolds.isEmpty();
    }

    private boolean scaffoldOccupied(Pos top) {
        AABB above = new AABB(top.x(), top.y(), top.z(), top.x() + 1, level.depth, top.z() + 1);
        for (Object entity : level.findEntities(null, above)) {
            if (entity instanceof Mob living && !living.removed && living.health > 0
                    && living.bb.y0 > top.y() + .1f)
                return true;
        }
        return false;
    }

    private boolean descendScaffold(Builder builder) {
        if (!BuilderPlacement.onScaffold(builder))
            return false;
        int x = (int) Math.floor(builder.x), z = (int) Math.floor(builder.z);
        int base = (int) Math.floor(builder.bb.y0);
        while (BuilderPlacement.scaffoldAt(level, new Pos(x, base - 1, z)))
            base--;
        return builder.bb.y0 > base + .15f && approachStand(new Vec3D(x + .5f, base, z + .5f));
    }

    private Vec3D access(Project project, Pos pos) {
        Vec3D best = null, from = eyes();
        float nearest = Float.POSITIVE_INFINITY;
        int maxX = project.x + project.width();
        int maxZ = project.z + project.width();
        for (int x = project.x - 1; x <= maxX; x++) {
            for (int z = project.z - 1; z <= maxZ; z++) {
                if (project.inside(x + .5f, z + .5f))
                    continue;
                float dx = x - pos.x(), dz = z - pos.z();
                for (int y = project.y - 1; y <= project.y + 1; y++) {
                    float dy = y + mob.getEyeHeight(1) - pos.y() - .5f;
                    if (dx * dx + dy * dy + dz * dz > 25)
                        continue;
                    Vec3D stand = new Vec3D(x + .5f, y, z + .5f);
                    Vec3D eye = stand.add(0, mob.getEyeHeight(1), 0);
                    float distance = from.distanceSquared(eye);
                    if (distance >= nearest || BuilderPlacement.inWay((Builder) mob, stand, pos)
                            || pos.equals(project.entrance())
                                    && BuilderPlacement.inWay((Builder) mob, stand, pos.step(1))
                            || BuilderPlacement.face(level, eye, pos, false) == null
                            || reachableStand(x, y, z) == null)
                        continue;
                    nearest = distance;
                    best = stand;
                }
            }
        }
        return best;
    }

    private static Builder supply(Builder builder, List<Builder> crew, int raw) {
        if (hasMaterial(builder, raw))
            return builder;
        for (Builder member : crew)
            if (hasMaterial(member, raw))
                return member;
        return null;
    }

    private static boolean hasMaterial(Builder builder, int raw) {
        return slot(builder.pack(), raw) >= 0 || raw == Block.WOOD.id && usableLog(builder) >= 0;
    }

    private static int slot(Inventory pack, int raw) {
        for (int i = 0; i < pack.totalSize(); i++) {
            if (pack.getId(i) == raw && pack.getCount(i) > 0)
                return i;
        }
        return -1;
    }

    private static int usableLog(Builder builder) {
        int log = slot(builder.pack(), Block.LOG.id);
        return log >= 0 && (builder.pack().getCount(log) == 1 || builder.roomFor(Block.WOOD.id) >= 3)
                ? log
                : -1;
    }

    private static void consume(Builder builder, int raw) {
        Inventory pack = builder.pack();
        int stock = slot(pack, raw);
        if (stock >= 0) {
            pack.setIdCount(stock, raw, pack.getCount(stock) - 1);
        } else {
            int log = usableLog(builder);
            pack.setIdCount(log, Block.LOG.id, pack.getCount(log) - 1);
            for (int i = 0; i < 3; i++)
                pack.addResource(Block.WOOD.id);
        }
    }
}
