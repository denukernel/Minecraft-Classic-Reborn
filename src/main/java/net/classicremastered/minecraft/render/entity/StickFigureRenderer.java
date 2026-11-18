package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.model.StickFigureModel;
import net.classicremastered.minecraft.render.RageAuraRenderer;
import net.classicremastered.minecraft.render.TextureManager;

final class StickFigureRenderer extends ModelMobRenderer<StickFigure> {
    StickFigureRenderer(StickFigureModel model) {
        super(model);
    }

    @Override
    protected void renderBase(
            StickFigure boss,
            TextureManager tm,
            float partial,
            float yaw,
            float pitch,
            float brightness) {
        super.renderBase(boss, tm, partial, yaw, pitch, brightness);
        ((StickFigureModel) model).renderHeldBlock(tm, MODEL_SCALE, brightness);
    }

    @Override
    public void renderWorld(StickFigure boss, TextureManager tm, float partial) {
        super.renderWorld(boss, tm, partial);
        if (boss.ai instanceof StickFigureAI special)
            special.powers.renderPhysicsGun(boss, partial);
        if (boss.isEnraged() && !boss.removed) RageAuraRenderer.render(boss, partial);
    }
}
