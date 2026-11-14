package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class TeenFrostbite extends Frostbite {
    public static final short REGISTRY_ID = 85;

    public TeenFrostbite(Level level, float x, float y, float z) {
        super(level, x, y, z);
        TeenZombie.configureTeen(this, "frostbite_teen");
    }

    @Override
    protected float modelScale() {
        return TeenZombie.SCALE;
    }
}
