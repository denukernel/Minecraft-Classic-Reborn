package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.ProgressBarDisplay;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.phys.AABB;

/** Bounded local populations and individually validated species packs. */
public final class MobSpawner {
    public Level level;
    public static final int SPAWN_RADIUS = 48, DESPAWN_RADIUS = 128, LOCAL_MOB_CAP = 32;
    public static final int ANIMAL_CAP = 12;

    public MobSpawner(Level level) {
        this.level = level;
    }

    public static boolean isAnimal(Mob mob) {
        return mob instanceof Pig
                || mob instanceof Sheep
                || mob instanceof Chicken
                || mob instanceof Cow;
    }

    public static boolean isMonster(Mob mob) {
        return mob instanceof Zombie
                || mob instanceof Creeper
                || mob instanceof Spider
                || mob instanceof Enderman
                || mob instanceof HouseVandalizer;
    }

    public int monsterCap() {
        return switch (CombatRules.difficulty(level)) {
            case PEACEFUL -> 0;
            case EASY -> 12;
            case NORMAL -> 20;
            case HARD -> 28;
            case VERY_HARD -> 32;
            case EXTREME -> 36;
        };
    }

    private int nearby(float x, float y, float z, int category, Class<?> species, float radius) {
        int count = 0;
        for (Object value :
                level.findEntities(
                        null,
                        new AABB(
                                x - radius,
                                y - radius,
                                z - radius,
                                x + radius,
                                y + radius,
                                z + radius))) {
            if (!(value instanceof Mob mob) || mob.removed || mob.health <= 0) continue;
            float dx = mob.x - x, dy = mob.y - y, dz = mob.z - z;
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            if (species != null
                    ? species.isInstance(mob)
                    : category == 0 ? isAnimal(mob) : isMonster(mob)) count++;
        }
        return count;
    }

    private int population(float x, float y, float z, int category, Entity near) {
        if (near != null) return nearby(x, y, z, category, null, DESPAWN_RADIUS);
        int count = 0;
        if (level.blockMap != null)
            for (Object value : level.blockMap.all)
                if (value instanceof Mob mob
                        && !mob.removed
                        && mob.health > 0
                        && (category == 0 ? isAnimal(mob) : isMonster(mob))) count++;
        return count;
    }

    private boolean inside(int x, int z) {
        return level instanceof LevelInfiniteTerrain
                || level instanceof LevelInfiniteFlat
                || x >= 0 && z >= 0 && x < level.width && z < level.height;
    }

    private void ensureColumn(int x, int z) {
        if (level instanceof LevelInfiniteTerrain t) t.chunks().getOrCreate(x >> 4, z >> 4);
        else if (level instanceof LevelInfiniteFlat f) f.chunks.getOrCreate(x >> 4, z >> 4);
    }

    private boolean feetOk(int x, int y, int z) {
        return y > 0
                && y < level.depth - 1
                && level.isSolidTile(x, y - 1, z)
                && !level.isSolidTile(x, y, z)
                && level.getLiquid(x, y, z) == LiquidType.NOT_LIQUID
                && level.getLiquid(x, y - 1, z) == LiquidType.NOT_LIQUID;
    }

    private int feet(int x, int y, int z) {
        for (int delta = 0; delta <= 8; delta++) {
            if (feetOk(x, y - delta, z)) return y - delta;
            if (delta > 0 && feetOk(x, y + delta, z)) return y + delta;
        }
        return -1;
    }

    private boolean range(float x, float y, float z, Entity near) {
        float dx = x - (near == null ? level.xSpawn : near.x),
                dy = y - (near == null ? level.ySpawn : near.y),
                dz = z - (near == null ? level.zSpawn : near.z);
        float d = dx * dx + dy * dy + dz * dz;
        return d >= 24 * 24 && (near == null || d <= SPAWN_RADIUS * SPAWN_RADIUS);
    }

    private boolean animalSpot(int x, int y, int z) {
        return level.getTile(x, y - 1, z) == Block.GRASS.id
                && level.isLit(x, y, z)
                && level.getDaylightBrightness() >= .6f;
    }

    private boolean dark(int x, int y, int z) {
        return !(level.isLit(x, y, z) && level.getDaylightBrightness() >= .6f)
                && level.getBrightness(x, y, z) <= .45f;
    }

    private boolean villagerNear(float x, float y, float z) {
        for (Object o :
                level.findEntities(null, new AABB(x - 32, y - 16, z - 32, x + 32, y + 16, z + 32)))
            if (o instanceof Villager v && v.hasHome && !v.removed && v.health > 0) return true;
        return false;
    }

    private short monster() {
        int r = level.random.nextInt(97);
        if (r < 4) return 7;
        if (r < 47) return 1;
        if (r < 77) return 2;
        if (r < 90) return 23;
        return 3;
    }

    public final int spawn(int batches, Entity near, ProgressBarDisplay bar) {
        return spawn(batches, near, bar, true);
    }

    public final int spawn(
            int batches, Entity near, ProgressBarDisplay bar, boolean allowHostiles) {
        if (level == null || level.isNetworkMode() || !level.doMobSpawning) return 0;
        MobRegistry.bootstrapDefaults();
        BiomeLevelGenerator biomes =
                level.biomeSeed == null ? null : new BiomeLevelGenerator(level.biomeSeed);
        allowHostiles &= near != null && monsterCap() > 0;
        float centerX = near == null ? level.xSpawn : near.x,
                centerY = near == null ? level.ySpawn : near.y,
                centerZ = near == null ? level.zSpawn : near.z;
        int spawned = 0;
        // Preserve the distant-world special encounter, with safe final placement.
        if (Math.max(Math.abs(centerX), Math.abs(centerZ)) >= 6000000) {
            if (allowHostiles && level.random.nextInt(200) == 0) {
                int x = (int) centerX + 24 + level.random.nextInt(20), z = (int) centerZ;
                ensureColumn(x, z);
                int y = feet(x, level.getHighestTile(x, z), z);
                if (y > 0) {
                    FakeHerobrine mob = new FakeHerobrine(level);
                    if (place(mob, x, y, z, near)) {
                        level.addEntity(mob);
                        spawned++;
                    }
                }
            }
            return spawned;
        }
        // Initial seeding uses a finite work budget and one bounded population budget.
        int limit = Math.min(Math.max(0, batches), near == null ? 256 : 6);
        for (int batch = 0; batch < limit; batch++) {
            if (bar != null) bar.setProgress(batch * 100 / Math.max(1, limit - 1));
            int animals = population(centerX, centerY, centerZ, 0, near),
                    monsters = population(centerX, centerY, centerZ, 1, near);
            boolean golemCandidate = near != null && level.random.nextInt(100) == 0;
            boolean animal =
                    golemCandidate
                            || animals < ANIMAL_CAP
                                    && (!allowHostiles
                                            || monsters >= monsterCap()
                                            || level.random.nextBoolean());
            if (!golemCandidate
                    && (animal
                            ? animals >= ANIMAL_CAP
                            : !allowHostiles || monsters >= monsterCap())) continue;
            boolean infinite =
                    level instanceof LevelInfiniteTerrain || level instanceof LevelInfiniteFlat;
            int x =
                    near == null && !infinite
                            ? level.random.nextInt(level.width)
                            : (int) Math.floor(centerX) + level.random.nextInt(97) - 48;
            int z =
                    near == null && !infinite
                            ? level.random.nextInt(level.height)
                            : (int) Math.floor(centerZ) + level.random.nextInt(97) - 48;
            if (!inside(x, z)) continue;
            ensureColumn(x, z);
            int guess = level.getHighestTile(x, z);
            if (!animal && level.random.nextInt(3) == 0)
                guess = 1 + level.random.nextInt(Math.max(1, Math.min(level.depth - 2, guess)));
            int y = feet(x, guess, z);
            if (y < 1) continue;
            short id = animal ? new short[] {21, 22, 25, 43}[level.random.nextInt(4)] : monster();
            boolean golem = golemCandidate && villagerNear(x, y, z);
            if (golemCandidate && !golem) continue;
            boolean vandal =
                    !animal
                            && level.getDaylightBrightness() < .6f
                            && level.isLit(x, y, z)
                            && level.random.nextInt(2000) == 0
                            && level.countInstanceOf(HouseVandalizer.class) < 2
                            && villagerNear(x, y, z);
            if (golem) id = 27;
            else if (vandal) id = HouseVandalizer.REGISTRY_ID;
            int pack =
                    golem || vandal
                            ? 1
                            : animal ? 2 + level.random.nextInt(3) : 1 + level.random.nextInt(3);
            for (int member = 0; member < pack; member++) {
                if (!golem
                        && population(centerX, centerY, centerZ, animal ? 0 : 1, near)
                                >= (animal ? ANIMAL_CAP : monsterCap())) break;
                int sx = x + level.random.nextInt(7) - 3, sz = z + level.random.nextInt(7) - 3;
                if (!inside(sx, sz)) continue;
                ensureColumn(sx, sz);
                int sy = feet(sx, y, sz);
                if (sy < 1) continue;
                if (animal ? !animalSpot(sx, sy, sz) : !dark(sx, sy, sz)) continue;
                if (golem
                        && (!villagerNear(sx, sy, sz)
                                || nearby(sx, sy, sz, 0, IronGolem.class, 32) > 0)) continue;
                if (vandal && !villagerNear(sx, sy, sz)) continue;
                short variant = id;
                if (id == 2 && level.random.nextInt(100) < 5) variant = BabySkeleton.REGISTRY_ID;
                if (id == 1) {
                    boolean baby = level.random.nextInt(100) < 5;
                    var biome = biomes == null ? null : biomes.biomeAt(sx, sz);
                    if (biome == BiomeLevelGenerator.Biome.DESERT) {
                        variant =
                                baby
                                        ? net.classicremastered.minecraft.mob.BabyHusk.REGISTRY_ID
                                        : Husk.REGISTRY_ID;
                    } else if (!level.isDaytime()
                            && (biome == BiomeLevelGenerator.Biome.SNOWY_PLAINS
                                    || biome == BiomeLevelGenerator.Biome.SNOWY_FOREST)) {
                        variant =
                                baby
                                        ? net.classicremastered.minecraft.mob.BabyFrostbite
                                                .REGISTRY_ID
                                        : net.classicremastered.minecraft.mob.Frostbite.REGISTRY_ID;
                    } else if (baby) {
                        variant = 40;
                    } else {
                        var difficulty = CombatRules.difficulty(level);
                        if (difficulty.ordinal()
                                >= net.classicremastered.minecraft.Difficulty.VERY_HARD.ordinal()) {
                            int roll = level.random.nextInt(100);
                            int igniterChance =
                                    difficulty == net.classicremastered.minecraft.Difficulty.EXTREME
                                            ? 12
                                            : 2;
                            if (roll < igniterChance) variant = ZombieIgniter.REGISTRY_ID;
                            else if (roll < igniterChance + 2)
                                variant = MobRegistry.idOfName("ZombieBuilder");
                        }
                    }
                }
                Mob mob = MobRegistry.create(variant, level, sx + .5f, sy, sz + .5f);
                if (mob == null || mob.getRegistryId() != variant || !place(mob, sx, sy, sz, near))
                    continue;
                Mob chicken = null;
                if (mob instanceof Zombie && mob.isBaby() && level.random.nextInt(20) == 0) {
                    var candidate = MobRegistry.create((short) 25, level, sx + .5f, sy, sz + .5f);
                    if (candidate instanceof net.classicremastered.minecraft.mob.Chicken
                            && place(candidate, sx, sy, sz, near)) chicken = candidate;
                }
                if (chicken != null) level.addEntity(chicken);
                level.addEntity(mob);
                if (chicken != null) mob.mount(chicken);
                if (mob instanceof Spider spider && level.random.nextInt(100) == 0) {
                    Mob rider = MobRegistry.create((short) 2, level, mob.x, mob.y, mob.z);
                    if (rider instanceof Skeleton skeleton && spider.hasRiderClearance(skeleton)) {
                        level.addEntity(rider);
                        rider.mount(mob);
                    }
                }
                spawned++;
            }
        }
        return spawned;
    }

    private boolean dry(AABB bounds) {
        for (int x = (int) Math.floor(bounds.x0); x < Math.ceil(bounds.x1); x++)
            for (int y = (int) Math.floor(bounds.y0); y < Math.ceil(bounds.y1); y++)
                for (int z = (int) Math.floor(bounds.z0); z < Math.ceil(bounds.z1); z++)
                    if (level.getLiquid(x, y, z) != LiquidType.NOT_LIQUID) return false;
        return true;
    }

    private boolean place(Mob mob, int x, int feetY, int z, Entity near) {
        if (mob instanceof Zombie
                && net.classicremastered.minecraft.level.structure.GeneratedHouse.containsHouse(
                        level, x, feetY, z)) return false;
        // Refresh bounds after constructors that assign dimensions directly.
        mob.setPos(mob.x, mob.y, mob.z);
        // Respect species with feet-based origins as well as centered entities.
        float originAboveFeet = mob.y - mob.bb.y0;
        mob.setPos(x + .5f, feetY + originAboveFeet + .01f, z + .5f);
        if (mob.bb.y0 < 1 || mob.bb.y1 >= level.depth) return false;
        if (!(level instanceof LevelInfiniteTerrain || level instanceof LevelInfiniteFlat)
                && (mob.bb.x0 < 0
                        || mob.bb.z0 < 0
                        || mob.bb.x1 > level.width
                        || mob.bb.z1 > level.height)) return false;
        return range(mob.x, mob.y, mob.z, near)
                && level.getLiquid(x, feetY, z) == LiquidType.NOT_LIQUID
                && dry(mob.bb)
                && level.getCubes(mob.bb).isEmpty()
                && level.isFree(mob.bb)
                && level.findEntities(mob, mob.bb).isEmpty();
    }
}
