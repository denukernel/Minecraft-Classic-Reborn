package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.model.ChickenModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class ChickenRenderer extends MobRenderer<Chicken> {
    public ChickenRenderer(ChickenModel model) { super(model, "/mob/chicken.png"); }

    @Override
    public void render(Chicken mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        ((ChickenModel) model).syncFromEntity(mob);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
