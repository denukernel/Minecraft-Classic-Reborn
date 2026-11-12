package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Save-local references are resolved after the mount and all passengers exist. */
final class RidingLinks {
    private record Seat(int key, int index) {}

    private final Map<Integer, Entity> entities = new LinkedHashMap<>();
    private final Map<Entity, Seat> riders = new LinkedHashMap<>();

    static void save(List<?> all, Mob mob, CompoundTag tag) {
        if (mob.rider != null)
            tag.putFloat("MountFeetY", mob.bb.y0);
        if (mob.rider instanceof Mob && !(mob.rider instanceof Player)) {
            tag.putInt("MountKey", all.indexOf(mob));
        }
        savePassenger(all, mob, tag);
    }

    static void savePassenger(List<?> all, Entity rider, CompoundTag tag) {
        if (rider == null) return;
        Entity mount = rider.riding;
        if (!(mount instanceof BoatEntity)
                && (!(mount instanceof Mob) || mount instanceof Player || rider instanceof Player))
            return;
        int key = all.indexOf(mount);
        if (key < 0 || mount.removed) return;
        tag.putInt("RidingKey", key);
        if (mount instanceof BoatEntity boat && boat.secondPassenger() == rider)
            tag.putInt("RidingSeat", 1);
    }

    void read(Mob mob, CompoundTag tag) {
        if (tag == null)
            return;
        if (tag.hasKey("MountFeetY")) {
            float feet = tag.getFloat("MountFeetY");
            if (Float.isFinite(feet)) {
                mob.setPos(mob.x, feet + mob.bbHeight * .5f, mob.z);
                mob.resetInterpolation();
                if (mob.blockMap != null)
                    mob.blockMap.moved(mob);
            }
        }
        readMount(mob, tag);
        readPassenger(mob, tag);
    }

    void readMount(Entity mount, CompoundTag tag) {
        if (tag.hasKey("MountKey") && tag.getInt("MountKey") >= 0)
            entities.putIfAbsent(tag.getInt("MountKey"), mount);
    }

    void readPassenger(Entity rider, CompoundTag tag) {
        if (rider != null && tag.hasKey("RidingKey"))
            riders.put(rider, new Seat(tag.getInt("RidingKey"), tag.getInt("RidingSeat")));
    }

    void restore() {
        for (int index = 0; index <= 1; index++) {
            for (var entry : riders.entrySet()) {
                Seat seat = entry.getValue();
                if (seat.index() != index) continue;
                Entity target = entities.get(seat.key());
                if (target != null) entry.getKey().mount(target);
            }
        }
    }
}
