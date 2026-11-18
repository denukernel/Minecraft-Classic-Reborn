package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Cow;
import net.classicremastered.minecraft.model.CowModel;
import net.classicremastered.minecraft.render.TextureManager;

final class CowRenderer extends ModelMobRenderer<Cow> {
    CowRenderer(CowModel model) {
        super(model);
    }

    @Override
    protected void renderBase(
            Cow cow,
            TextureManager textures,
            float partial,
            float yaw,
            float pitch,
            float brightness) {
        super.renderBase(cow, textures, partial, yaw, pitch, brightness);
        if (cow.isSaddled()) ((CowModel) model).renderSaddle(textures, MODEL_SCALE);
    }
}
