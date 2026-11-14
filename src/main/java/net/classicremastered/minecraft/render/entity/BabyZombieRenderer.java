package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.BabyZombie;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class BabyZombieRenderer extends MobRenderer<BabyZombie> {
    public BabyZombieRenderer(Model babyZombieModel) {
        super(babyZombieModel, "/mob/zombie.png");
    }

    @Override
    public void render(BabyZombie mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        renderPose(mob, tm, a, a.yaw, a.pitch);
    }

    public void render(BabyZombie mob, TextureManager tm, float partial, float yaw, float pitch) {
        renderPose(mob, tm, animInputs(mob, partial), yaw, pitch);
    }

    private void renderPose(
            BabyZombie mob, TextureManager tm, AnimInputs a, float yaw, float pitch) {
        bind(tm, texture);
        model.preAnimate(mob);
        model.render(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);
    }
}
