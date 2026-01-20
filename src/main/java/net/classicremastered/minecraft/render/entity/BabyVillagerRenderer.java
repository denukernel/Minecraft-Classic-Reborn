package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.BabyVillager;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class BabyVillagerRenderer extends MobRenderer<BabyVillager> {
    public BabyVillagerRenderer(Model babyVillagerModel) { super(babyVillagerModel, "/mob/villager.png"); }

    @Override
    public void render(BabyVillager mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
