package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class ZombieRenderer extends MobRenderer<Zombie> {
    public ZombieRenderer(Model model) { super(model, "/mob/zombie.png"); }

    @Override
    public void render(Zombie mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture); // skeleton texture
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // --- Armor overlay (safe, no double base) ---
        mob.renderArmorLayer(tm, a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
