package net.classicremastered.minecraft.render.entity;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.model.EndermanModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class EndermanRenderer extends MobRenderer<Enderman> {
    public EndermanRenderer(EndermanModel model) { super(model, "/mob/enderman.png"); }

    @Override
    public void render(Enderman mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);

        // body
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // eyes overlay
        int eyes = tm.load("/mob/enderman_eyes.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, eyes);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE); // additive
        ((EndermanModel) model).renderEyes(tm, MODEL_SCALE);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
