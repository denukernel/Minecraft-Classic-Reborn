package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.BabyZombie;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class BabyZombieRenderer extends MobRenderer<BabyZombie> {
    public BabyZombieRenderer(Model babyZombieModel) { super(babyZombieModel, "/mob/zombie.png"); }

    @Override
    public void render(BabyZombie mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);

        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // Armor overlay (armor model already has its own scale)
        mob.renderArmorLayer(tm, a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }


}
