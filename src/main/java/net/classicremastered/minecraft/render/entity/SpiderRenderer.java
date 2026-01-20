package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Spider;
import net.classicremastered.minecraft.model.SpiderModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class SpiderRenderer extends MobRenderer<Spider> {
    public SpiderRenderer(SpiderModel model) { super(model, "/mob/spider.png"); }

    @Override
    public void render(Spider mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
