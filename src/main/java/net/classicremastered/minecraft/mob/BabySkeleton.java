package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;

/** Permanent small skeleton archer. */
public final class BabySkeleton extends Skeleton {

    public static final long serialVersionUID = 1L;
    public static final short REGISTRY_ID = 43;

    public BabySkeleton(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "skeleton_baby";
        textureName = "/mob/skeleton.png";
        setSize(.44f, 1.26f);
        heightOffset = .63f;
        health = 10;
        bobStrength = .2f;
        setPos(x, y, z);
        if (ai instanceof BasicAttackAI attack)
            attack.speedScale *= 1.35f;
    }

    @Override
    public boolean isBaby() {
        return true;
    }
}
