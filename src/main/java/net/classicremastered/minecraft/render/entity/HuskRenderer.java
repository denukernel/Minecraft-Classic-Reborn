package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Husk;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class HuskRenderer extends MobRenderer<Husk> {
    public HuskRenderer(Model model) { super(model, "/mob/husk.png"); }

    @Override
    public void render(Husk mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
