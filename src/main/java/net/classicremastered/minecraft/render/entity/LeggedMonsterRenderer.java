package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.LeggedMonster;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class LeggedMonsterRenderer extends MobRenderer<LeggedMonster> {
    public LeggedMonsterRenderer(Model model) {
        super(model, "/mob/legged_monster.png");
    }

    @Override
    public void render(LeggedMonster mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
    }
}
