package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.model.VillagerModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class VillagerRenderer extends MobRenderer<Villager> {
    public VillagerRenderer(VillagerModel model) { super(model, "/mob/villager.png"); }

    @Override
    public void render(Villager mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
