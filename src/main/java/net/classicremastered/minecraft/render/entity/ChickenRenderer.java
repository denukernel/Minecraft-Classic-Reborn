package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.model.ChickenModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class ChickenRenderer extends MobRenderer<Chicken> {
    public ChickenRenderer(ChickenModel model) { super(model, "/mob/chicken.png"); }

    @Override
    public void render(Chicken mob, TextureManager tm, float partial) {
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try {
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        float light=mob.getRenderBrightness(partial);
        org.lwjgl.opengl.GL11.glColor4f(light,light,light,1);
        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);
        ((ChickenModel) model).syncFromEntity(mob);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        } finally {org.lwjgl.opengl.GL11.glPopAttrib();}
    }
}
