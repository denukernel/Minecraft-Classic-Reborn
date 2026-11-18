package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.mob.SignMob;
import net.classicremastered.minecraft.render.TextureManager;

final class SignMobRenderer extends MobRenderer<SignMob> {
    SignMobRenderer() {
        super(null, null);
    }

    @Override
    public void render(SignMob mob, TextureManager tm, float partial) {
        SignEntity.renderStandingSign(
                tm,
                mob.level == null || mob.level.minecraft == null
                        ? null
                        : mob.level.minecraft.fontRenderer,
                mob.xo + (mob.x - mob.xo) * partial,
                mob.renderFeetY(partial),
                mob.zo + (mob.z - mob.zo) * partial,
                mob.boardYaw(partial),
                mob.currentText());
    }

    @Override
    public void renderWorld(SignMob mob, TextureManager tm, float partial) {
        render(mob, tm, partial);
    }
}
