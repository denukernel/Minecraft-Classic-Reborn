package net.classicremastered.minecraft.level;

import java.util.*;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.nbt.*;

/** One authoritative record per assigned doorway; never owns pre-existing/player blocks. */
public final class VillagerDoorManager {
    public record Approach(float x, float y, float z) {
    }

    private static final class Door {
        final int x, y, z;
        final float homeX, homeY, homeZ, radius;
        final Approach approach;
        final Set<Villager> residents = new LinkedHashSet<>();
        int owned, clearTicks;
        boolean wasNight, sawZombie, released, sabotaged;
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
        return Math.abs(v.x - d.homeX) < d.radius && Math.abs(v.z - d.homeZ) < d.radius && Math.abs(v.bb.y0 - d.y) < 1;
    }

    private boolean closeEnough(Door d, Villager v) {
        float dx = v.x - (d.x + .5f), dz = v.z - (d.z + .5f);
        return dx * dx + dz * dz <= 4 && Math.abs(v.bb.y0 - d.y) < 1;
    }

    public Approach approach(Villager villager) {
        if (!villager.hasHomeDoor)
            return null;
        Door d = doors.get(Level.getCoordKey(villager.homeDoorX, villager.homeDoorY, villager.homeDoorZ));
        return d != null && d.builder == villager ? d.approach : null;
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
        for (Entity entity : level.blockMap.getEntities(null,
                new AABB(d.homeX - 24, d.homeY - 24, d.homeZ - 24, d.homeX + 24, d.homeY + 24, d.homeZ + 24))) {
            if (!(entity instanceof Zombie) || entity instanceof Skeleton || entity.removed
                    || ((Mob) entity).health <= 0)
                continue;
            float dx = entity.x - d.homeX, dy = entity.y - d.homeY, dz = entity.z - d.homeZ;
            if (dx * dx + dy * dy + dz * dz <= 24 * 24)
                return true;
        }
        return false;
    }

    /** Called from the shared world clock, not once per resident or rendered frame. */
    public void tick() {
        if (level.isNetworkMode() || ++clock % 20 != 0)
            return;
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
            if (!night) {
                d.wasNight = false;
                d.sawZombie = false;
                d.released = false;
                d.clearTicks = 0;
                d.sabotaged = false;
                removeOwned(d);
                continue;
            }
            if (!d.wasNight) {
                d.wasNight = true;
                d.sawZombie = false;
                d.released = false;
                d.clearTicks = 0;
                d.sabotaged = false;
            }
            if (zombiesNear(d)) {
                d.sawZombie = true;
                d.clearTicks = 0;
                if (d.released) {
                    d.released = false;
                    d.sabotaged = false;
                }
            } else if (d.sawZombie) {
                d.clearTicks = Math.min(100, d.clearTicks + 20);
                if (d.clearTicks >= 100)
                    d.released = true;
            }
            boolean hostile = d.residents.stream().anyMatch(Villager::isHostile);
            if (d.released || hostile) {
                removeOwned(d);
                continue;
            }
            if (d.owned != 0 || d.sabotaged)
                continue;
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
        float x = h.getFloat("X"), y = h.getFloat("Y"), z = h.getFloat("Z"), radius = h.getFloat("Radius");
        if (!validHome(x, y, z, radius))
            return;
        v.setHome(x, y, z, radius);
        if (h.getBoolean("HasDoor") && v.level.isInBounds(h.getInt("DoorX"), h.getInt("DoorY"), h.getInt("DoorZ"))
                && v.level.isInBounds(h.getInt("DoorX"), h.getInt("DoorY") + 1, h.getInt("DoorZ")))
            v.setHomeDoor(h.getInt("DoorX"), h.getInt("DoorY"), h.getInt("DoorZ"));
    }
}
