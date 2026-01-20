package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Pig;
import net.classicremastered.minecraft.model.PigModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class PigRenderer extends MobRenderer<Pig> {
    public PigRenderer(PigModel model) { super(model, "/mob/pig.png"); }

    @Override
    public void render(Pig mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
