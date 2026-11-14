package net.classicremastered.minecraft.level;

import java.util.*;
import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.nbt.*;

/** One authoritative record per assigned doorway; never owns pre-existing/player blocks. */
public final class VillagerDoorManager {
    public static final int SIEGE_RANGE = 35;
    private static final int WAVE_DELAY = 160;
    private static final int WAVE_PLAYER_RANGE = 32;
    private static final float CLOSE_RANGE = 2.5f;

    public record Approach(float x, float y, float z) {}

    public record SiegeApproach(Approach doorway, Approach slot) {}

    private static final class Door {
        final int x, y, z;
        final float homeX, homeY, homeZ, radius;
        final Approach approach;
        final Set<Villager> residents = new LinkedHashSet<>();
        int owned, clearTicks;
        boolean wasNight, sawZombie, released, sabotaged;
        boolean burning;
        Villager builder;
        Door(int x, int y, int z, float hx, float hy, float hz, float radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            homeX = hx;
            homeY = hy;
            homeZ = hz;
            this.radius = radius;
            float dx = hx - (x + .5f), dz = hz - (z + .5f);
            approach = Math.abs(dx) > Math.abs(dz)
                    ? new Approach(x + .5f + Math.signum(dx), y, z + .5f)
                    : new Approach(x + .5f, y, z + .5f + Math.signum(dz));
        }
    }
    private final Level level;
    private final Map<Long, Door> doors = new LinkedHashMap<>(), cells = new HashMap<>();
    private Boolean wasDaytime;
    private Door pendingSiege;
    private int waveDelay;
    private int clock;
    private boolean editing;
    private long editedCell;
    public VillagerDoorManager(Level level) {
        this.level = level;
    }

    public void register(Villager villager) {
        if (!villager.hasHome || !villager.hasHomeDoor || level.isNetworkMode())
            return;
        long key = Level.getCoordKey(villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ);
        Door door = doors.get(key);
        if (door == null) {
            door = new Door(villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ, villager.homeX, villager.homeY,
                    villager.homeZ, villager.homeRadius);
            doors.put(key, door);
            track(door);
        }
        door.residents.add(villager);
    }

    private void track(Door door) {
        cells.put(Level.getCoordKey(door.x, door.y, door.z), door);
        cells.put(Level.getCoordKey(door.x, door.y + 1, door.z), door);
    }

    public void unregister(Villager villager) {
        if (!villager.hasHomeDoor)
            return;
        Door door = doors.get(Level.getCoordKey(villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        if (door != null)
            door.residents.remove(villager);
    }

    public void blockChanged(int x, int y, int z) {
        long key = Level.getCoordKey(x, y, z);
        if (editing && key == editedCell)
            return;
        Door door = cells.get(key);
        if (door == null)
            return;
        int bit = y == door.y ? 1 : 2;
        if ((door.owned & bit) != 0) {
            door.owned &= ~bit;
            door.sabotaged = true;
        }
    }

    private boolean inside(Door d, Villager v) {
        return inside(d, v.x, v.bb.y0, v.z);
    }

    private boolean inside(Door d, float x, float feetY, float z) {
        return Math.abs(x - d.homeX) < d.radius
                && Math.abs(z - d.homeZ) < d.radius
                && Math.abs(feetY - d.y) < 1;
    }

    private boolean closeEnough(Door d, Villager v) {
        float dx = v.x - (d.x + .5f), dz = v.z - (d.z + .5f);
        return dx * dx + dz * dz <= CLOSE_RANGE * CLOSE_RANGE && Math.abs(v.bb.y0 - d.y) < 1;
    }

    private Approach closingApproach(Door door, Villager villager) {
        // Leave room between the villager and a zombie standing just beyond the doorway.
        float x = 2 * door.approach.x() - door.x - .5f;
        float z = 2 * door.approach.z() - door.z - .5f;
        if (inside(door, x, door.y, z)
                && Pathfinder.hasStandingRoom(level, villager, x, door.y, z)
                && !villager.overlapsFire(
                        villager.bb.cloneMove(
                                x - villager.x, door.y - villager.bb.y0, z - villager.z)))
            return new Approach(x, door.y, z);
        return door.approach;
    }

    public Approach approach(Villager villager) {
        if (!villager.hasHomeDoor) return null;
        Door d =
                doors.get(
                        Level.getCoordKey(
                                villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        return d != null && d.builder == villager ? closingApproach(d, villager) : null;
    }

    public Approach fleeShelter(Villager villager) {
        if (!villager.hasHomeDoor) return null;
        Door door =
                doors.get(
                        Level.getCoordKey(
                                villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        if (door == null || door.burning) return null;
        if (door.owned == 3 && inside(door, villager)) {
            return new Approach(villager.x, villager.bb.y0, villager.z);
        }
        return door.owned == 0
                        && !door.sabotaged
                        && !collidable(door.x, door.y, door.z)
                        && !collidable(door.x, door.y + 1, door.z)
                ? closingApproach(door, villager)
                : null;
    }

    public boolean homeBurning(Villager villager) {
        if (!villager.hasHomeDoor) return false;
        Door door =
                doors.get(
                        Level.getCoordKey(
                                villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        return door != null && door.burning;
    }

    public List<Approach> evacuationShelters(Villager villager) {
        List<Approach> shelters = new ArrayList<>();
        for (Door door : doors.values()) {
            if (door.burning
                    || door.owned != 0
                    || door.residents.isEmpty()
                    || collidable(door.x, door.y, door.z)
                    || collidable(door.x, door.y + 1, door.z)) continue;
            Approach point = door.approach;
            if (villager.distanceTo(point.x(), point.y() + villager.heightOffset, point.z()) <= 24)
                shelters.add(point);
        }
        shelters.sort(
                Comparator.comparingDouble(
                        p -> villager.distanceTo(p.x(), p.y() + villager.heightOffset, p.z())));
        return shelters;
    }

    private boolean houseOnFire(Door door) {
        int x = (int) Math.floor(door.homeX), z = (int) Math.floor(door.homeZ);
        // Flames set against the outside wall are a siege hazard, not an evacuation signal.
        for (int bx = x - 2; bx <= x + 2; bx++)
            for (int bz = z - 2; bz <= z + 2; bz++)
                for (int y = door.y; y <= door.y + 4; y++)
                    if (level.getTile(bx, y, bz) == Block.FIRE.id) return true;
        return false;
    }

    private final transient Map<Zombie, Approach> siegePositions = new WeakHashMap<>();

    public SiegeApproach siegeApproach(Zombie zombie, float range) {
        if (level.isNetworkMode()) return null;
        Approach nearest = null;
        Door selectedDoor = null;
        Door blockedDoor = null;
        float bestDistance = range * range;
        float blockedDistance = bestDistance;
        for (Door door : doors.values()) {
            if (!door.burning
                    && (door.owned != 3
                            || level.getTile(door.x, door.y, door.z) != Block.WOOD.id
                            || level.getTile(door.x, door.y + 1, door.z) != Block.WOOD.id))
                continue;
            float x = 2 * door.x + 1 - door.approach.x();
            float z = 2 * door.z + 1 - door.approach.z();
            float dy = door.y - zombie.bb.y0;
            float dx = x - zombie.x, dz = z - zombie.z;
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance > bestDistance || !hasResidentInside(door)) {
                continue;
            }
            Approach position = siegePosition(zombie, door, x, z, dy);
            if (position != null) {
                nearest = position;
                selectedDoor = door;
                bestDistance = distance;
            } else if (distance <= blockedDistance) {
                blockedDoor = door;
                blockedDistance = distance;
            }
        }
        if (nearest == null) siegePositions.remove(zombie);
        else siegePositions.put(zombie, nearest);
        Door door = selectedDoor != null ? selectedDoor : blockedDoor;
        return door == null
                ? null
                : new SiegeApproach(new Approach(door.x + .5f, door.y, door.z + .5f), nearest);
    }

    private boolean hasResidentInside(Door door) {
        return door.residents.stream()
                .anyMatch(
                        v ->
                                v.level == level
                                        && !v.removed
                                        && v.health > 0
                                        && !v.isHostile()
                                        && inside(door, v));
    }

    private Approach siegePosition(Zombie zombie, Door door, float x, float z, float dy) {
        float nx = x - door.x - .5f, nz = z - door.z - .5f;
        int firstRow = door.burning ? 2 : 0;
        for (int row = firstRow; row <= 4; row++) {
            for (int index = 0; index < 5; index++) {
                int side = (index + 1) / 2 * (index % 2 == 0 ? -1 : 1);
                float px = x + nx * row - nz * side, pz = z + nz * row + nx * side;
                Approach position = new Approach(px, door.y, pz);
                AABB bounds = zombie.bb.cloneMove(px - zombie.x, dy, pz - zombie.z);
                if (zombie.overlapsFire(bounds.grow(1f, 0, 1f))
                        || !level.getCubes(bounds).isEmpty()
                        || level.getCubes(bounds.cloneMove(0, -.1f, 0)).isEmpty()) continue;
                boolean taken =
                        siegePositions.entrySet().stream()
                                .anyMatch(
                                        entry ->
                                                entry.getKey() != zombie
                                                        && !entry.getKey().removed
                                                        && entry.getKey().health > 0
                                                        && entry.getKey()
                                                                        .distanceTo(
                                                                                px,
                                                                                door.y
                                                                                        + entry
                                                                                                .getKey()
                                                                                                .heightOffset,
                                                                                pz)
                                                                < 10
                                                        && position.equals(entry.getValue()));
                if (!taken) return position;
            }
        }
        return null;
    }

    public void openForHostile(Villager villager) {
        if (level.isNetworkMode() || !villager.hasHomeDoor)
            return;
        Door d = doors.get(Level.getCoordKey(villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        if (d != null)
            removeOwned(d);
    }

    private boolean write(Door d, int offset, int id) {
        editing = true;
        editedCell = Level.getCoordKey(d.x, d.y + offset, d.z);
        try {
            return level.setTile(d.x, d.y + offset, d.z, id);
        } finally {
            editing = false;
        }
    }

    private void removeOwned(Door d) {
        for (int i = 0; i < 2; i++)
            if ((d.owned & (1 << i)) != 0) {
                if (level.getTile(d.x, d.y + i, d.z) == Block.WOOD.id)
                    write(d, i, 0);
            }
        d.owned = 0;
    }

    private boolean collidable(int x, int y, int z) {
        int id = level.getTile(x, y, z);
        return id > 0 && Block.blocks[id] != null && Block.blocks[id].getCollisionBox(x, y, z) != null;
    }

    private boolean canClose(Door d) {
        if (!level.isInBounds(d.x, d.y, d.z) || !level.isInBounds(d.x, d.y + 1, d.z) || !collidable(d.x, d.y - 1, d.z)
                || level.getTile(d.x, d.y, d.z) != 0 || level.getTile(d.x, d.y + 1, d.z) != 0)
            return false;
        boolean alongX = d.approach.z() != d.z + .5f;
        for (int side : new int[]{-1, 1})
            for (int h = 0; h < 2; h++)
                if (!collidable(d.x + (alongX ? side : 0), d.y + h, d.z + (alongX ? 0 : side)))
                    return false;
        if (level.blockMap != null)
            for (Entity entity : level.blockMap.getEntities(null, new AABB(d.x, d.y, d.z, d.x + 1, d.y + 2, d.z + 1)))
                if (!entity.removed && entity.bb.intersects(new AABB(d.x, d.y, d.z, d.x + 1, d.y + 2, d.z + 1)))
                    return false;
        return true;
    }

    private boolean zombiesNear(Door d) {
        if (level.blockMap == null)
            return false;
        for (Entity entity :
                level.blockMap.getEntities(
                        null,
                        new AABB(
                                d.homeX - SIEGE_RANGE,
                                d.homeY - SIEGE_RANGE,
                                d.homeZ - SIEGE_RANGE,
                                d.homeX + SIEGE_RANGE,
                                d.homeY + SIEGE_RANGE,
                                d.homeZ + SIEGE_RANGE))) {
            if (!(entity instanceof Zombie zombie)
                    || entity instanceof Skeleton
                    || entity.removed
                    || zombie.health <= 0
                    || zombie.corruptedToDefendPlayer) continue;
            float dx = entity.x - d.homeX, dy = entity.y - d.homeY, dz = entity.z - d.homeZ;
            if (dx * dx + dy * dy + dz * dz <= SIEGE_RANGE * SIEGE_RANGE) return true;
        }
        return false;
    }

    /** Called from the shared world clock, not once per resident or rendered frame. */
    public void tick() {
        if (level.isNetworkMode()) return;
        tickSiegeWave();
        if (++clock % 20 != 0) return;
        boolean night = !level.isDaytime();
        Iterator<Door> iterator = doors.values().iterator();
        while (iterator.hasNext()) {
            Door d = iterator.next();
            d.builder = null;
            d.residents.removeIf(v -> v.level != level || v.removed || v.health <= 0 || !v.hasHome || !v.hasHomeDoor
                    || v.homeDoorX != d.x || v.homeDoorY != d.y || v.homeDoorZ != d.z);
            if (d.residents.isEmpty()) {
                removeOwned(d);
                cells.remove(Level.getCoordKey(d.x, d.y, d.z));
                cells.remove(Level.getCoordKey(d.x, d.y + 1, d.z));
                iterator.remove();
                continue;
            }
            d.burning = houseOnFire(d);
            if (d.burning) {
                if (exitClearOfFire(d)) removeOwned(d);
                continue;
            }
            boolean threatened =
                    night || d.owned != 0
                            ? zombiesNear(d)
                            : d.residents.stream().anyMatch(this::chasedByZombie);
            if (!night && !threatened && !d.sawZombie) {
                d.wasNight = false;
                d.sawZombie = false;
                d.released = false;
                d.clearTicks = 0;
                d.sabotaged = false;
                removeOwned(d);
                continue;
            }
            if (night && !d.wasNight) {
                d.wasNight = true;
                d.sawZombie = false;
                d.released = false;
                d.clearTicks = 0;
                d.sabotaged = false;
            }
            if (!night) d.wasNight = false;
            if (threatened) {
                d.sawZombie = true;
                d.clearTicks = 0;
                if (d.released) {
                    d.released = false;
                    d.sabotaged = false;
                }
            } else if (d.sawZombie) {
                d.clearTicks = Math.min(100, d.clearTicks + 20);
                if (!night && d.clearTicks >= 100) {
                    d.released = true;
                    d.sawZombie = false;
                }
            }
            boolean hostile = d.residents.stream().anyMatch(Villager::isHostile);
            if ((!night && d.released) || hostile) {
                removeOwned(d);
                continue;
            }
            if (d.owned != 0 || d.sabotaged) continue;
            if (d.residents.stream().anyMatch(v -> !inside(d, v)))
                continue;
            d.builder = d.residents.iterator().next();
            if (!closeEnough(d, d.builder) || !canClose(d))
                continue;
            if (write(d, 0, Block.WOOD.id)) {
                d.owned = 1;
                if (write(d, 1, Block.WOOD.id))
                    d.owned = 3;
                else
                    removeOwned(d);
            }
            d.builder = null;
        }
    }

    private void tickSiegeWave() {
        boolean daytime = level.isDaytime();
        boolean dusk = Boolean.TRUE.equals(wasDaytime) && !daytime;
        wasDaytime = daytime;
        Player player = level.player instanceof Player local ? local : null;
        int desired =
                switch (CombatRules.difficulty(level)) {
                    case PEACEFUL, EASY -> 0;
                    case NORMAL -> 4;
                    case HARD -> 5;
                    case VERY_HARD, EXTREME -> 6;
                };
        if (daytime
                || !level.doDayNightCycle
                || !level.doMobSpawning
                || desired == 0
                || player == null
                || player.level != level
                || player.removed
                || player.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(player)) {
            pendingSiege = null;
            return;
        }
        if (dusk) {
            Door nearest = null;
            double bestDistance = WAVE_PLAYER_RANGE * WAVE_PLAYER_RANGE;
            for (Door door : doors.values()) {
                double distance = homeDistanceSquared(player, door);
                if (distance <= bestDistance && hasResidentInside(door)) {
                    nearest = door;
                    bestDistance = distance;
                }
            }
            if (nearest != null && level.random.nextInt(10) == 0) {
                pendingSiege = nearest;
                waveDelay = 0;
            }
        }
        Door door = pendingSiege;
        if (door == null) return;
        if (doors.get(Level.getCoordKey(door.x, door.y, door.z)) != door
                || homeDistanceSquared(player, door) > WAVE_PLAYER_RANGE * WAVE_PLAYER_RANGE
                || !hasResidentInside(door)) {
            pendingSiege = null;
            return;
        }
        // The sun crosses the horizon before open ground is dark enough for spawning.
        if (waveDelay == 0) {
            if (level.getDayFactorSmooth() > MobSpawner.HOSTILE_SPAWN_BRIGHTNESS) return;
            waveDelay = WAVE_DELAY;
            if (level.minecraft != null && level.minecraft.hud != null)
                level.minecraft.hud.addChat("&cA zombie siege is approaching the village.");
            return;
        }
        if (--waveDelay > 0) return;
        pendingSiege = null;
        new MobSpawner(level).spawnSiegeGroup(player, door.x, door.y, door.z, desired);
    }

    private double homeDistanceSquared(Player player, Door door) {
        double dx = player.x - door.homeX,
                dy = player.bb.y0 - door.homeY,
                dz = player.z - door.homeZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean exitClearOfFire(Door door) {
        int dx = Math.round(door.approach.x() - door.x - .5f);
        int dz = Math.round(door.approach.z() - door.z - .5f);
        for (int step = -1; step <= 1; step++) {
            for (int y = door.y; y <= door.y + 1; y++) {
                if (level.getTile(door.x + step * dx, y, door.z + step * dz) == Block.FIRE.id)
                    return false;
            }
        }
        return true;
    }

    private boolean chasedByZombie(Villager villager) {
        for (Object entity : level.findEntities(villager, villager.bb.grow(12, 4, 12))) {
            if (entity instanceof Zombie zombie
                    && !(zombie instanceof Skeleton)
                    && !zombie.removed
                    && zombie.health > 0
                    && !zombie.corruptedToDefendPlayer
                    && villager.distanceToSqr(zombie) <= 12 * 12) return true;
        }
        return false;
    }

    public void save(CompoundTag root) {
        ListTag list = new ListTag("VillagerDoors", (byte) 10);
        for (Door d : doors.values()) {
            CompoundTag tag = new CompoundTag(null);
            tag.putInt("X", d.x);
            tag.putInt("Y", d.y);
            tag.putInt("Z", d.z);
            tag.putFloat("HomeX", d.homeX);
            tag.putFloat("HomeY", d.homeY);
            tag.putFloat("HomeZ", d.homeZ);
            tag.putFloat("Radius", d.radius);
            tag.putInt("Owned", d.owned);
            tag.putBoolean("WasNight", d.wasNight);
            tag.putBoolean("SawZombie", d.sawZombie);
            tag.putBoolean("Released", d.released);
            tag.putBoolean("Sabotaged", d.sabotaged);
            list.add(tag);
        }
        root.put(list);
    }

    public void load(CompoundTag root) {
        doors.clear();
        cells.clear();
        siegePositions.clear();
        wasDaytime = null;
        pendingSiege = null;
        clock = 0;
        ListTag list = root.getList("VillagerDoors");
        for (int n = 0; n < list.size(); n++) {
            CompoundTag t = (CompoundTag) list.get(n);
            int x = t.getInt("X"), y = t.getInt("Y"), z = t.getInt("Z");
            if (!level.isInBounds(x, y, z) || !level.isInBounds(x, y + 1, z))
                continue;
            float hx = t.getFloat("HomeX"), hy = t.getFloat("HomeY"), hz = t.getFloat("HomeZ"),
                    r = t.getFloat("Radius");
            if (!validHome(hx, hy, hz, r))
                continue;
            Door d = new Door(x, y, z, hx, hy, hz, r);
            d.owned = t.getInt("Owned") & 3;
            for (int i = 0; i < 2; i++)
                if (level.getTile(x, y + i, z) != Block.WOOD.id)
                    d.owned &= ~(1 << i);
            d.wasNight = t.getBoolean("WasNight");
            d.sawZombie = t.getBoolean("SawZombie");
            d.released = t.getBoolean("Released");
            d.sabotaged = t.getBoolean("Sabotaged");
            doors.put(Level.getCoordKey(x, y, z), d);
            track(d);
        }
    }

    private static boolean validHome(float x, float y, float z, float radius) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(radius) && radius > 0
                && radius <= 32;
    }

    public static void saveHome(Mob mob, CompoundTag tag) {
        if (!(mob instanceof Villager v) || !v.hasHome)
            return;
        CompoundTag h = new CompoundTag("VillagerHome");
        h.putFloat("X", v.homeX);
        h.putFloat("Y", v.homeY);
        h.putFloat("Z", v.homeZ);
        h.putFloat("Radius", v.homeRadius);
        h.putBoolean("HasDoor", v.hasHomeDoor);
        h.putInt("DoorX", v.homeDoorX);
        h.putInt("DoorY", v.homeDoorY);
        h.putInt("DoorZ", v.homeDoorZ);
        tag.put(h);
    }

    public static void loadHome(Mob mob, CompoundTag tag) {
        if (!(mob instanceof Villager v) || tag == null || !tag.hasKey("VillagerHome"))
            return;
        CompoundTag h = tag.getCompound("VillagerHome");
        float x = h.getFloat("X"),
                y = h.getFloat("Y"),
                z = h.getFloat("Z"),
                radius = h.getFloat("Radius");
        if (!validHome(x, y, z, radius))
            return;
        v.setHome(x, y, z, radius);
        if (h.getBoolean("HasDoor")
                && v.level.isInBounds(h.getInt("DoorX"), h.getInt("DoorY"), h.getInt("DoorZ"))
                && v.level.isInBounds(h.getInt("DoorX"), h.getInt("DoorY") + 1, h.getInt("DoorZ")))
            v.setHomeDoor(h.getInt("DoorX"), h.getInt("DoorY"), h.getInt("DoorZ"));
    }
}
