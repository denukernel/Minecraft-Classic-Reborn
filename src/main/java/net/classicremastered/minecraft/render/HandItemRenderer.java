package net.classicremastered.minecraft.render;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.ModelPart;

/** Items pivot around their grip at the palm, rather than around the icon center. */
public final class HandItemRenderer {
    public static void applyPalm(ModelPart arm,float scale) {
        GL11.glTranslatef(arm.x*scale,arm.y*scale,arm.z*scale);
        GL11.glRotatef(arm.roll*57.295776f,0,0,1);GL11.glRotatef(arm.yaw*57.295776f,0,1,0);
        GL11.glRotatef(arm.pitch*57.295776f,1,0,0);
        float center=0;
        if(arm.vertices!=null)for(var vertex:arm.vertices)center+=vertex.vector.x/arm.vertices.length;
        GL11.glTranslatef(center*scale,9.5f*scale,-.5f*scale);
    }
    public static void renderBow(HumanoidModel model,TextureManager tm,String texture,float scale,float brightness,float draw,boolean aiming) {
        GL11.glPushMatrix();
        try {
            applyPalm(model.rightArm,scale);
            if(aiming) {
                GL11.glRotatef(90,1,0,0);GL11.glRotatef(-90,0,1,0);GL11.glRotatef(45,0,0,1);
            } else {
                GL11.glRotatef(-45,0,0,1);
            }
            GL11.glScalef(15*scale,15*scale,15*scale);
            GL11.glTranslatef(.16f*(1-draw),-.06f*(1-draw),0);
            ItemMeshRenderer.render(tm,texture,brightness);
        } finally {GL11.glPopMatrix();}
    }
    public static void renderItem(ModelPart arm,TextureManager tm,Item item,float brightness) {
        GL11.glPushMatrix();
        try {
            applyPalm(arm,.0625f);
            GL11.glRotatef(-35,1,0,0);GL11.glScalef(.8f,.8f,.8f);
            GL11.glTranslatef(-.28f,.28f,0);
            ItemMeshRenderer.renderItem(tm,item,brightness);
        } finally {GL11.glPopMatrix();}
    }
}
