package com.mcraft.api.registry;

import java.util.*;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;

public final class EntityRegistryContext {
    @FunctionalInterface
    public interface Factory {
        Entity create(Level l, float x, float y, float z);
    }

    private final Map<String, Factory> factories = new LinkedHashMap<>();

    public void add(String key, Factory f) {
        factories.put(key, f);
    }

    public Entity spawn(String key, Level l, float x, float y, float z) {
        Factory f = factories.get(key);
        if (f == null)
            return null;
        Entity e = f.create(l, x, y, z);
        l.addEntity(e);
        return e;
    }
}