package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.model.CreeperModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class CreeperRenderer extends MobRenderer<Creeper> {
    public CreeperRenderer(CreeperModel model) { super(model, "/mob/creeper.png"); }

    @Override
    public void render(Creeper mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
