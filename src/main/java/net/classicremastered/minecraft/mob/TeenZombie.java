package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public final class TeenZombie extends Zombie {
    public static final short REGISTRY_ID = 83;
    static final float SCALE = 0.85F;

    public TeenZombie(Level level, float x, float y, float z) {
        super(level, x, y, z);
        configureTeen(this, "zombie_teen");
    }

    static void configureTeen(Zombie mob, String model) {
        mob.modelName = model;
        mob.textureName = "/mob/" + model + ".png";
        mob.setSize(mob.bbWidth * SCALE, mob.bbHeight * SCALE);
        mob.heightOffset *= SCALE;
        mob.setPos(mob.x, mob.y, mob.z);
        mob.setVoicePitch(1.25F);
    }

    @Override
    protected float modelScale() {
        return SCALE;
    }
}
