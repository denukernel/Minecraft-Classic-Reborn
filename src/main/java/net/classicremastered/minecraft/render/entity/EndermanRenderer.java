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

        GL11.glPushMatrix();
        if (mob.isAttacking) {
            float var10 = 0.02F;
            java.util.Random rnd = mob.level.random;
            GL11.glTranslatef((float) rnd.nextGaussian() * var10, 0.0F, (float) rnd.nextGaussian() * var10);
        }

        // body
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // Reuse the body pose and shake transform for the hurt pass.
        float hurt = mob.hurtDuration > 0 ? (mob.hurtTime - partial) / mob.hurtDuration : 0;
        if (hurt > 0) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                    | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                GL11.glDepthMask(false);
                GL11.glColor4f(1f, .12f, .1f, Math.min(.65f, .65f * hurt * hurt));
                model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
            } finally {
                GL11.glPopAttrib();
            }
        }

        // eyes overlay
        int eyes = tm.load("/mob/enderman_eyes.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, eyes);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE); // additive
        ((EndermanModel) model).renderEyes(tm, MODEL_SCALE);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glPopMatrix();
    }
}
