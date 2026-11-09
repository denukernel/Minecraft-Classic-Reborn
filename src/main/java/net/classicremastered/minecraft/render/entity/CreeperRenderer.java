package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.model.CreeperModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class CreeperRenderer extends MobRenderer<Creeper> {
    public CreeperRenderer(CreeperModel model) { super(model, "/mob/creeper.png"); }

    @Override
    public void render(Creeper mob, TextureManager tm, float partial) {
        AnimInputs a = animInputs(mob, partial);
        float fuse=mob.fuseProgress(partial);
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GL11.glPushMatrix();try{
        float pulse=1+(float)Math.sin(fuse*100)*fuse*.025f;
        org.lwjgl.opengl.GL11.glScalef((1+fuse*fuse*.25f)*pulse,1+fuse*fuse*.1f,(1+fuse*fuse*.25f)*pulse);
        bind(tm, texture);
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);
        if(fuse>0&&((int)(fuse*10)&1)==1){
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glDepthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            org.lwjgl.opengl.GL11.glColor4f(1,1,1,.25f+fuse*.5f);
            model.render(a.anim,a.runAmt,a.age,a.yaw,a.pitch,MODEL_SCALE);
        }
        }finally{org.lwjgl.opengl.GL11.glPopMatrix();org.lwjgl.opengl.GL11.glPopAttrib();}
    }
}
