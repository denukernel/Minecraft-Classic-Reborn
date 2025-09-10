// File: src/com/mojang/minecraft/mob/MobRegistry.java
package net.classicremastered.minecraft.mob;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.classicremastered.minecraft.level.Level;

/**
 * ID ↔ factory registry for concrete mobs.
 * Never register abstract classes (Mob, HumanoidMob, QuadrupedMob).
 *
 * Usage:
 *   MobRegistry.bootstrapDefaults();               // once at startup
 *   short id = MobRegistry.idOf(mobInstance);      // for saving
 *   Mob m = MobRegistry.create(id, lvl, x,y,z);    // for loading
 */
public final class MobRegistry {

    @FunctionalInterface
    public interface MobFactory {
        Mob create(Level level, float x, float y, float z) throws Exception;
    }

    private static final class Entry {
        final short id;
        final String name;
        final Class<? extends Mob> type;
        final MobFactory factory;
        Entry(short id, String name, Class<? extends Mob> type, MobFactory factory) {
            this.id = id; this.name = name; this.type = type; this.factory = factory;
        }
    }

    private static final Map<Short, Entry> byId = new HashMap<>();
    private static final Map<Class<?>, Short> idOfClass = new HashMap<>();
    private static final Map<String, Short> idOfName = new HashMap<>(); // NEW

    private static volatile boolean bootstrapped = false;

    private MobRegistry() {}

    /** Register a concrete mob class with a fixed id. */
    public static synchronized void register(short id, String name,
                                             Class<? extends Mob> cls,
                                             MobFactory factory) {
        if (cls == null) throw new IllegalArgumentException("cls is null");
        if (Modifier.isAbstract(cls.getModifiers()))
            throw new IllegalArgumentException("Refusing to register abstract class: " + cls.getName());
        if (byId.containsKey(id))
            throw new IllegalStateException("Duplicate mob id " + id + " (" + byId.get(id).name + " vs " + name + ")");
        byId.put(id, new Entry(id, name, cls, factory));
        idOfClass.put(cls, id);
        idOfName.put(name.toLowerCase(Locale.ROOT), id); // NEW
    }

    /** Safe helper for optional classes (present only in some builds). */
    private static void tryRegister(short id, String name,
                                    Class<? extends Mob> cls, MobFactory factory) {
        if (cls == null) return;
        try { register(id, name, cls, factory); } catch (Throwable ignored) {}
    }

    /** Create a mob by ID; returns null if unknown or construction fails. */
    public static Mob create(short id, Level level, float x, float y, float z) {
        Entry e = byId.get(id);
        if (e == null) return null;
        try {
            return e.factory.create(level, x, y, z);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /** Get the registry ID for a mob instance, or -1 if unregistered. */
    public static short idOf(Mob mob) {
        if (mob == null) return (short) -1;
        Short id = idOfClass.get(mob.getClass());
        return id == null ? (short) -1 : id;
    }

    // ===== NEW =====

    /** Get the registry ID for a mob name, or -1 if not registered. */
    public static short idOfName(String name) {
        if (name == null) return -1;
        Short id = idOfName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? (short) -1 : id;
    }

    /** Get all registered mob names. */
    public static String[] allNames() {
        return idOfName.keySet().toArray(new String[0]);
    }

    /** Register the built-in mobs exactly once. Keep IDs stable forever. */
 // MobRegistry.java — replace the entire bootstrapDefaults() with this
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) return;

        // --- Hostiles / undead / bosses ---
        tryRegister((short) 1,  "Zombie",            Zombie.class,            (l,x,y,z)->new Zombie(l,x,y,z));
        tryRegister((short) 2,  "Skeleton",          Skeleton.class,          (l,x,y,z)->new Skeleton(l,x,y,z));
        tryRegister((short) 3,  "Creeper",           Creeper.class,           (l,x,y,z)->new Creeper(l,x,y,z));
        tryRegister((short) 4,  "TNTThrower",        TNTThrower.class,        (l,x,y,z)->new TNTThrower(l,x,y,z));
        tryRegister((short) 5,  "ZombieBuilder",     ZombieBuilder.class,     (l,x,y,z)->new ZombieBuilder(l,x,y,z));
        tryRegister((short) 6,  "ScaryMindZombie",   ScaryMindZombie.class,   (l,x,y,z)->new ScaryMindZombie(l,x,y,z));
        tryRegister((short) 7,  "Enderman",          Enderman.class,          (l,x,y,z)->new Enderman(l,x,y,z));
        tryRegister((short) 8,  "Husk",              Husk.class,              (l,x,y,z)->new Husk(l,x,y,z)); // NEW
        tryRegister((short) 26, "Bee", Bee.class, (l,x,y,z)->new Bee(l,x,y,z));
        tryRegister((short) 27, "IronGolem", IronGolem.class, (l,x,y,z) -> new IronGolem(l, x, y, z));
        tryRegister((short) 28, "SignMob", SignMob.class, (l,x,y,z)->new SignMob(l,x,y,z));

        // --- Neutrals / animals / villagers ---
        tryRegister((short) 20, "Villager",          Villager.class,          (l,x,y,z)->new Villager(l,x,y,z));
        tryRegister((short) 21, "Pig",               Pig.class,               (l,x,y,z)->new Pig(l,x,y,z));
        tryRegister((short) 22, "Sheep",             Sheep.class,             (l,x,y,z)->new Sheep(l,x,y,z));
        tryRegister((short) 23, "Spider",            Spider.class,            (l,x,y,z)->new Spider(l,x,y,z));
        tryRegister((short) 24, "Rana",              Rana.class,              (l,x,y,z)->new Rana(l,x,y,z));
        tryRegister((short) 25, "Chicken", Chicken.class,
                (l,x,y,z)->new Chicken(l,x,y,z));
        // --- Variants / babies (optional on some builds) ---
        tryRegister((short) 40, "BabyZombie",        BabyZombie.class,        (l,x,y,z)->new BabyZombie(l,x,y,z));
        tryRegister((short) 41, "BabyVillager",      BabyVillager.class,      (l,x,y,z)->new BabyVillager(l,x,y,z));

        // Reserve a range for future mobs (e.g., 60–99)

        bootstrapped = true;
    }
}
