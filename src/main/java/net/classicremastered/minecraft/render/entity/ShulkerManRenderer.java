package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.ShulkerMan;
import net.classicremastered.minecraft.model.ModelShulkerMan;
import net.classicremastered.minecraft.render.TextureManager;

public final class ShulkerManRenderer extends MobRenderer<ShulkerMan> {

    public ShulkerManRenderer(ModelShulkerMan model) {
        super(model, "/mob/stuckerman.png"); // call parent MobRenderer constructor
    }

    @Override
    public void render(ShulkerMan mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);     // same helper as other renderers
        bind(tm, texture);                           // MobRenderer handles GL bind
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
