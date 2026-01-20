package net.classicremastered.minecraft.render.entity;

import java.util.HashMap;
import java.util.Map;
import net.classicremastered.minecraft.mob.Mob;

public final class RenderManager {
    private static final Map<Class<? extends Mob>, MobRenderer<? extends Mob>> R = new HashMap<>();

    private RenderManager() {}

    public static <T extends Mob> void register(Class<T> mobClass, MobRenderer<T> renderer) {
        R.put(mobClass, renderer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mob> MobRenderer<T> getRenderer(T mob) {
        return (MobRenderer<T>) R.get(mob.getClass());
    }
}
