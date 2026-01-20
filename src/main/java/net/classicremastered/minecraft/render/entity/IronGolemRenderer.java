package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.IronGolem;
import net.classicremastered.minecraft.model.IronGolemModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class IronGolemRenderer extends MobRenderer<IronGolem> {
    public IronGolemRenderer(IronGolemModel model) { super(model, "/mob/iron_golem.png"); }

    @Override
    public void render(IronGolem mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
