package com.mcraft.api.registry;

import java.util.*;

public final class ItemRegistryContext {

    @FunctionalInterface
    public interface Factory {
        Object create(int assignedId) throws Exception;
    }

    private final Map<String, Factory> factories = new LinkedHashMap<>();
    private int nextId = 128; // above block-as-item indices

    public void add(String key, Factory f) {
        if (factories.containsKey(key)) {
            System.err.println("[API] Item key already registered: " + key);
            return;
        }
        factories.put(key, f);
    }

    public void applyToVanillaArrays() {
        try {
            Class<?> Item = Class.forName("net.classicremastered.minecraft.level.itemstack.Item");
            java.lang.reflect.Field arrF = Item.getDeclaredField("items");
            arrF.setAccessible(true);

            Object[] items = (Object[]) arrF.get(null);
            int id = nextId;
            for (Map.Entry<String, Factory> e : factories.entrySet()) {
                if (id >= items.length) {
                    System.err.println("[API] Item id out of range: " + id);
                    break;
                }
                Object it = e.getValue().create(id);
                items[id] = it;
                System.out.println("[API] Registered item: " + e.getKey() + " -> id=" + id);
                id++;
            }
            nextId = id;
        } catch (Throwable t) {
            System.err.println("[API] ItemRegistryContext failed:");
            t.printStackTrace();
        }
    }
}
