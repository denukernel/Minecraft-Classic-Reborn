package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class TeenHusk extends Husk {
    public static final short REGISTRY_ID = 84;

    public TeenHusk(Level level, float x, float y, float z) {
        super(level, x, y, z);
        TeenZombie.configureTeen(this, "husk_teen");
    }

    @Override
    protected float modelScale() {
        return TeenZombie.SCALE;
    }
}
