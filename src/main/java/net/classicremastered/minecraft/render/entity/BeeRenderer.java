package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Bee;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class BeeRenderer extends MobRenderer<Bee> {
    public BeeRenderer(Model beeModel) { super(beeModel, "/mob/bee.png"); }

    @Override
    public void render(Bee mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
