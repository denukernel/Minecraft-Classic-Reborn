package net.classicremastered.minecraft.level.dimensions;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.InfiniteLevelNbtCodec;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.DarkCobblestoneZombie;
import net.classicremastered.minecraft.mob.Ironmock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Steve;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;

import java.io.IOException;
import java.util.Arrays;

/** The encounter and the link between its infinite level and the player's home world. */
public final class StickmansHouse {
    public static final String NAME = "Stickman's House";
    public static final String TERRAIN_TYPE = "StickmansHouse";
    public static final int DEPTH = 64;
    private static final int FLOOR = 14;
    public static final int BOSS_FLOOR_FEET = FLOOR + 1;
    private static final int MID = 48;
    private static final int LEFT = 30, RIGHT = 66, FRONT = 12, GATE = 40, BACK = 80;
    private static final int WAVE = 0, GATE_OPEN = 1, BOSS_SPAWNED = 2;
    private static final int STEVE_CHECK_INTERVAL = 200;
    private static final int DARK_ZOMBIE_SPAWN_DISTANCE = 100;
    private static final int DARK_ZOMBIE_CHECK_INTERVAL = 100;
    private static final int DARK_ZOMBIE_LOCAL_CAP = 3;
    private final Level home;
    private final Level arena;
    private Arrival returnPoint;
    private int steveCheckTicks;
    private int darkZombieSpawnCooldown;
    private int caveSoundDelay;

    private record Arrival(
            float x, float feetY, float z, float yaw, float pitch, boolean crouched) {
        void place(Player player) {
            player.restoreSavedPosition(x, feetY, z, yaw, pitch, crouched);
        }
    }

    private StickmansHouse(Level home, Level arena) {
        this.home = home;
        this.arena = arena;
        home.setStickmansHouse(this);
        arena.setStickmansHouse(this);
    }

    public static Level activeLevel(Level level) {
        StickmansHouse house = level == null ? null : level.getStickmansHouse();
        return house != null && house.returnPoint != null ? house.arena : level;
    }

    public static Level homeLevel(Level level) {
        StickmansHouse house = level == null ? null : level.getStickmansHouse();
        return house == null ? level : house.home;
    }

    public static Level travel(Level source, Player player) {
        StickmansHouse house = source.getStickmansHouse();
        if (house == null) house = new StickmansHouse(source, generate(source));
        boolean leaving = source == house.arena;
        Level destination = leaving ? house.home : house.arena;
        player.unmount();
        if (player.rider != null) player.rider.unmount();
        source.removeEntity(player);
        source.player = null;
        if (leaving) {
            house.returnPoint.place(player);
            house.returnPoint = null;
        } else {
            house.returnPoint =
                    new Arrival(
                            player.x,
                            player.bb.y0,
                            player.z,
                            player.yRot,
                            player.xRot,
                            player.sneaking);
            new Arrival(MID + .5f, FLOOR + 1, FRONT + 2.5f, 0, 0, false).place(player);
        }
        destination.creativeMode = source.creativeMode;
        player.xd = player.yd = player.zd = 0;
        player.fallDistance = 0;
        destination.player = player;
        destination.addEntity(player);
        return destination;
    }

    private static Level generate(Level home) {
        Level level = LevelInfiniteTerrain.createStickmansHouse();
        level.name = NAME;
        level.creator = home.creator == null ? "" : home.creator;
        level.createTime = System.currentTimeMillis();
        level.keepInventory = home.keepInventory;
        level.doMobSpawning = home.doMobSpawning;
        level.doMobLoot = home.doMobLoot;
        level.doDefendersFollowPlayer = home.doDefendersFollowPlayer;
        level.doDayNightCycle = home.doDayNightCycle;
        for (int offset : new int[] {-6, -4, -2, 2, 4, 6}) {
            Ironmock ironmock =
                    new Ironmock(level, MID + offset + .5f, FLOOR + 1.75f, FRONT + 5.5f);
            ironmock.persistent = true;
            level.addEntity(ironmock);
        }
        return level;
    }

    public static void setSpawn(Level level) {
        level.setSpawnPos(MID, FLOOR + 1, FRONT + 2, 0);
    }

    public void tickExploration() {
        if (caveSoundDelay > 0) caveSoundDelay--;
        if (!(arena.player instanceof Player player) || player.removed || player.health <= 0) {
            steveCheckTicks = 0;
            darkZombieSpawnCooldown = 0;
            return;
        }

        double distance = distanceFromArena(player.x, player.z);
        tickDarkZombieSpawning(player, distance);
        if (player.isSpectator()) {
            steveCheckTicks = 0;
            return;
        }
        if (distance > 200
                && caveSoundDelay == 0
                && arena.minecraft != null
                && arena.minecraft.soundPC != null
                && arena.minecraft.soundPC.playCaveAmbience()) {
            caveSoundDelay = 600 + arena.random.nextInt(601);
        }
        if (distance < 1_000) {
            steveCheckTicks = 0;
        } else if (++steveCheckTicks == STEVE_CHECK_INTERVAL) {
            steveCheckTicks = 0;
            if (arena.random.nextInt(100) < 3) spawnSteve(player);
        }
    }

    private void spawnSteve(Player player) {
        for (Entity entity :
                arena.blockMap.getEntities(player, player.bb.grow(48, arena.depth, 48))) {
            if (entity instanceof Steve steve && !steve.removed && steve.health > 0) return;
        }
        double facing = Math.toRadians(-player.yRot);
        Vec3D eyes = new Vec3D(player.x, player.bb.y0 + player.getEyeHeight(1), player.z);
        // Prefer an unobstructed spot ahead, but still try other directions near the arena or
        // walls.
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean preferVisible = attempt < 4;
            double roll = arena.random.nextDouble();
            double angle =
                    preferVisible ? facing + Math.toRadians(40 * (roll - .5)) : roll * Math.PI * 2;
            double radius = 18 + arena.random.nextDouble() * 12;
            int x = (int) Math.floor(player.x + radius * Math.sin(angle));
            int z = (int) Math.floor(player.z + radius * Math.cos(angle));
            if (distanceFromArena(x + .5f, z + .5f) < 1_000) continue;

            int y = arena.getHighestTile(x, z);
            if (!arena.isSolidTile(x, y - 1, z) || y + 1 >= arena.depth) continue;
            Steve steve = new Steve(arena, x + .5f, y + .9f, z + .5f);
            if (!arena.getCubes(steve.bb).isEmpty() || arena.containsAnyLiquid(steve.bb)) continue;
            if (preferVisible
                    && arena.clip(eyes, new Vec3D(steve.x, steve.y, steve.z), true) != null)
                continue;
            arena.addEntity(steve);
            return;
        }
    }

    private void tickDarkZombieSpawning(Player player, double distance) {
        if (distance < DARK_ZOMBIE_SPAWN_DISTANCE
                || !arena.doMobSpawning
                || arena.isNetworkMode()
                || CombatRules.difficulty(arena) == Difficulty.PEACEFUL) {
            darkZombieSpawnCooldown = 0;
            return;
        }
        if (darkZombieSpawnCooldown > 0 && --darkZombieSpawnCooldown > 0) return;
        darkZombieSpawnCooldown = DARK_ZOMBIE_CHECK_INTERVAL;

        int nearby = 0;
        for (Object entity : arena.findEntities(player, player.bb.grow(48, arena.depth, 48))) {
            if (entity instanceof DarkCobblestoneZombie zombie
                    && !zombie.removed
                    && zombie.health > 0
                    && ++nearby >= DARK_ZOMBIE_LOCAL_CAP) return;
        }
        int ground = arena.getHighestTile((int) Math.floor(player.x), (int) Math.floor(player.z));
        double facing = Math.toRadians(-player.yRot);
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle =
                    attempt < 4
                            ? facing + Math.toRadians(arena.random.nextDouble() * 80 - 40)
                            : arena.random.nextDouble() * Math.PI * 2;
            double radius = 14 + arena.random.nextDouble() * 10;
            int x = (int) Math.floor(player.x + Math.sin(angle) * radius);
            int z = (int) Math.floor(player.z + Math.cos(angle) * radius);
            if (distanceFromArena(x + .5f, z + .5f) < DARK_ZOMBIE_SPAWN_DISTANCE) continue;
            int feet = arena.getHighestTile(x, z);
            if (feet + 1 >= arena.depth
                    || Math.abs(feet - ground) > 8
                    || !arena.isSolidTile(x, feet - 1, z)) continue;
            var zombie = new DarkCobblestoneZombie(arena, x + .5f, feet + .9f, z + .5f);
            if (!arena.getCubes(zombie.bb).isEmpty()
                    || arena.containsAnyLiquid(zombie.bb)
                    || !arena.findEntities(zombie, zombie.bb).isEmpty()) continue;
            arena.addEntity(zombie);
            return;
        }
    }

    private static double distanceFromArena(float x, float z) {
        float dx = Math.max(0, Math.max(LEFT - x, x - (RIGHT + 1)));
        float dz = Math.max(0, Math.max(FRONT - z, z - (BACK + 1)));
        return Math.hypot(dx, dz);
    }

    public static void generateChunk(SimpleChunk chunk) {
        int size = SimpleChunk.SIZE;
        Arrays.fill(chunk.blocks, 0, size * size, (byte) Block.BEDROCK.id);
        Arrays.fill(
                chunk.blocks,
                size * size,
                size * size * (FLOOR + 1),
                (byte) Block.BLACK_COBBLESTONE.id);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int worldX = chunk.cx * size + x;
                int worldZ = chunk.cz * size + z;
                generateColumn(chunk, x, z, worldX, worldZ);
            }
        }
    }

    private static void generateColumn(SimpleChunk chunk, int x, int z, int worldX, int worldZ) {
        if (worldX < LEFT - 2 || worldX > RIGHT + 2 || worldZ < FRONT - 2 || worldZ > BACK + 2) {
            int ridge =
                    (int) (2 + 2 * Math.sin(worldX * .21 + worldZ * .13) * Math.cos(worldZ * .3));
            for (int y = FLOOR + 1; y <= FLOOR + ridge; y++)
                place(chunk, x, y, z, Block.BLACK_COBBLESTONE);
        }
        if (worldX < LEFT || worldX > RIGHT || worldZ < FRONT || worldZ > BACK) return;
        if (worldZ >= GATE) place(chunk, x, FLOOR, z, Block.OBSIDIAN);
        boolean wall =
                worldX == LEFT
                        || worldX == RIGHT
                        || worldZ == FRONT
                        || worldZ == GATE
                        || worldZ == BACK;
        boolean entrance = worldZ == FRONT && Math.abs(worldX - MID) <= 2;
        if (wall && !entrance) {
            Block material = worldZ >= GATE ? Block.OBSIDIAN : Block.BLACK_COBBLESTONE;
            for (int y = FLOOR + 1; y <= FLOOR + 7; y++) place(chunk, x, y, z, material);
        }
        if (worldX >= LEFT + 3
                && worldX <= RIGHT - 3
                && (worldX - LEFT - 3) % 6 == 0
                && worldZ >= GATE + 5
                && worldZ <= BACK - 3
                && (worldZ - GATE - 5) % 6 == 0) {
            place(chunk, x, FLOOR + 1, z, Block.OBSIDIAN);
        }
        if ((worldZ == FRONT + 1 || worldZ == GATE - 1 || worldZ == GATE + 1 || worldZ == BACK - 1)
                && (worldX == LEFT + 1 || worldX == RIGHT - 1 || Math.abs(worldX - MID) == 4)) {
            place(chunk, x, FLOOR, z, Block.MAGMA);
        }
    }

    private static void place(SimpleChunk chunk, int x, int y, int z, Block block) {
        chunk.blocks[SimpleChunk.idx(x, y, z, chunk.height)] = (byte) block.id;
    }

    public static void tick(Level level) {
        for (var entity : level.blockMap.all) {
            if (entity instanceof StickFigure boss) keepInRoom(boss, GATE, BACK);
            else if (entity instanceof Ironmock mock) keepInRoom(mock, FRONT, GATE);
        }
        if (level.getStickmansHouseStage() == WAVE) {
            for (var entity : level.blockMap.all) {
                if (entity instanceof Ironmock mock && mock.health > 0 && !mock.removed) return;
            }
            for (int x = MID - 2; x <= MID + 2; x++) {
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) level.setTile(x, y, GATE, 0);
            }
            level.setStickmansHouseStage(GATE_OPEN);
        }
        if (level.getStickmansHouseStage() == GATE_OPEN
                && level.player instanceof Player player
                && player.health > 0
                && player.z > GATE + 2
                && isInBossRoom(player)) {
            level.addEntity(new StickFigure(level, MID + .5f, FLOOR + 2, BACK - 10.5f));
            level.setStickmansHouseStage(BOSS_SPAWNED);
        }
    }

    public static boolean canFightBoss(Level level, Entity target) {
        return level == null
                || !level.isStickmansHouse()
                || !(target instanceof Player)
                || target.level == level && isInBossRoom(target);
    }

    private static boolean isInBossRoom(Entity entity) {
        return isBossRoomInterior(entity.x, entity.z)
                && entity.bb.y0 >= FLOOR
                && (entity.bb.y0 < FLOOR + 8
                        || CombatRules.difficulty(entity.level).compareTo(Difficulty.HARD) >= 0);
    }

    public static boolean isBossRoomInterior(float x, float z) {
        return x >= LEFT + 1 && x < RIGHT && z >= GATE + 1 && z < BACK;
    }

    private static void keepInRoom(Mob mob, int front, int back) {
        float half = mob.bbWidth * .5f;
        float x = Math.max(LEFT + 1 + half, Math.min(mob.x, RIGHT - half));
        float z = Math.max(front + 1 + half, Math.min(mob.z, back - half));
        if (mob.x == x && mob.z == z) return;
        mob.unmount();
        mob.ejectPassengers();
        mob.bb.move(x - mob.x, 0, z - mob.z);
        if (mob.x != x) mob.xd = 0;
        if (mob.z != z) mob.zd = 0;
        mob.x = x;
        mob.z = z;
        mob.resetInterpolation();
    }

    public static boolean isLooseObsidian(int x, int y, int z) {
        return y == FLOOR + 1 && x > LEFT && x < RIGHT && z > GATE && z < BACK;
    }

    public static void save(Level level, CompoundTag root) throws IOException {
        StickmansHouse house = level.getStickmansHouse();
        if (house == null || level != house.home) return;
        CompoundTag tag = InfiniteLevelNbtCodec.encode(house.arena, TERRAIN_TYPE);
        if (house.returnPoint != null) {
            Arrival point = house.returnPoint;
            tag.putBoolean("PlayerInside", true);
            tag.putFloat("HomeX", point.x);
            tag.putFloat("HomeFeetY", point.feetY);
            tag.putFloat("HomeZ", point.z);
            tag.putFloat("HomeYaw", point.yaw);
            tag.putFloat("HomePitch", point.pitch);
            tag.putBoolean("HomeCrouched", point.crouched);
        }
        root.put(tag);
    }

    public static void load(Level home, CompoundTag root) throws IOException {
        if (home.isStickmansHouse() || !root.hasKey(TERRAIN_TYPE)) return;
        CompoundTag tag = root.getCompound(TERRAIN_TYPE);
        if (!TERRAIN_TYPE.equals(tag.getString("TerrainType")))
            throw new IOException("Invalid Stickman's House level");
        Level arena = InfiniteLevelNbtCodec.load(tag, progress -> {});
        StickmansHouse house = new StickmansHouse(home, arena);
        if (tag.getBoolean("PlayerInside") && arena.player != null) {
            house.returnPoint =
                    new Arrival(
                            tag.getFloat("HomeX"),
                            tag.getFloat("HomeFeetY"),
                            tag.getFloat("HomeZ"),
                            tag.getFloat("HomeYaw"),
                            tag.getFloat("HomePitch"),
                            tag.getBoolean("HomeCrouched"));
        }
    }
}
