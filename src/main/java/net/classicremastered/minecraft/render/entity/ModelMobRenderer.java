package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

class ModelMobRenderer<T extends Mob> extends MobRenderer<T> {
    ModelMobRenderer(Model model) {
        super(model, null);
    }

    @Override
    public final void render(T mob, TextureManager tm, float partial) {
        renderBase(
                mob,
                tm,
                partial,
                mob.renderHeadYaw(partial),
                mob.renderHeadPitch(partial),
                mob.getRenderBrightness(partial));
    }

    @Override
    protected void renderBase(
            T mob, TextureManager tm, float partial, float yaw, float pitch, float brightness) {
        bindSkin(mob, tm);
        renderPose(mob, tm, partial, yaw, pitch);
    }
}
