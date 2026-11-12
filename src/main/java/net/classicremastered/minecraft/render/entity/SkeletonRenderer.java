package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.model.SkeletonModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class SkeletonRenderer<T extends Skeleton> extends MobRenderer<T> {
    public SkeletonRenderer(SkeletonModel model) {
        super(model, "/mob/skeleton.png");
    }

    @Override
    public void render(T mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        ((SkeletonModel) model).setRenderTextures(tm);
        ((SkeletonModel) model).setRenderPartial(partial);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // --- Armor overlay (safe, no double base) ---
        mob.renderArmorLayer(tm, a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
