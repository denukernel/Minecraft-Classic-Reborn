package com.mcraft.api.registry;

import java.util.*;

public final class BlockRegistryContext {

    @FunctionalInterface
    public interface Factory {
        Object create(int assignedId) throws Exception;
    }

    private final Map<String, Factory> factories = new LinkedHashMap<>();
    private int nextId = 200; // safe high range

    public void add(String key, Factory f) {
        if (factories.containsKey(key)) {
            System.err.println("[API] Block key already registered: " + key);
            return;
        }
        factories.put(key, f);
    }

    // Call once after vanilla block init
    public void applyToVanillaArrays() {
        try {
            Class<?> Block = Class.forName("net.classicremastered.minecraft.level.tile.Block");
            java.lang.reflect.Field arrF = Block.getDeclaredField("blocks");
            arrF.setAccessible(true);

            Object[] blocks = (Object[]) arrF.get(null);
            for (Map.Entry<String, Factory> e : factories.entrySet()) {
                int id = nextId++;
                Object b = e.getValue().create(id);
                if (id >= blocks.length) {
                    System.err.println("[API] Block id out of range: " + id);
                    continue;
                }
                blocks[id] = b;
                System.out.println("[API] Registered block: " + e.getKey() + " -> id=" + id);
            }
        } catch (Throwable t) {
            System.err.println("[API] BlockRegistryContext failed:");
            t.printStackTrace();
        }
    }
}
