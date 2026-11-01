package net.classicremastered.minecraft.render;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.model.*;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public final class HeldItemRenderer {
    public static void renderWorld(Player p,float partial) {
        int selected=p.inventory.getSelected();
        if(selected>=256 && GunModel.isGun(selected-256)){GunRenderer.renderWorld(p,partial);return;}
        if(p.minecraft!=null && p.minecraft.player==p && (p.minecraft.cameraMode==0 || !p.minecraft.renderer.shouldRenderLocalPlayer()))return;
        if(selected<=0 || selected>=256 && (selected-256>=Item.items.length || Item.items[selected-256]==null))return;
        if(selected<256 && Block.blocks[selected]==null)return;
        HumanoidModel model=p.getModel();
        if(model==null || p.level==null || p.level.minecraft==null)return;
        TextureManager textures=p.level.minecraft.textureManager;
        float yaw=p.yBodyRotO+(p.yBodyRot-p.yBodyRotO)*partial;
        float limb=p.animStepO+(p.animStep-p.animStepO)*partial,run=p.oRun+(p.run-p.oRun)*partial;
        model.setRotationAngles(limb,run,p.tickCount+partial,p.yRot-yaw,p.xRot,.0625f);
        BowItem bow=selected==262?(BowItem)Item.BOW:null;
        if(bow!=null)BowPose.apply(model,(p.yRot-yaw)/57.295776f,p.xRot/57.295776f,bow.getDrawProgress(p,partial),bow.isDrawing(p));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(p.xo+(p.x-p.xo)*partial,p.yo+(p.y-p.yo)*partial-p.heightOffset,p.zo+(p.z-p.zo)*partial);
            GL11.glTranslatef(0,(model.groundOffset+Math.abs((float)Math.cos(limb*.6662f))*5*run)*.0625f,0);
            GL11.glScalef(1,-1,1);GL11.glRotatef(180-yaw+p.rotOffs,0,1,0);GL11.glScalef(-1,1,1);
            float brightness=p.getBrightness(partial);
            if(bow!=null) {
                HandItemRenderer.renderBow(model,textures,bow.getTexture(p),.0625f,brightness,bow.getDrawProgress(p,partial),bow.isDrawing(p));
            } else if(selected<256) {
                HandItemRenderer.applyPalm(model.rightArm,.0625f);
                GL11.glRotatef(180,1,0,0);GL11.glScalef(.3f,.3f,.3f);GL11.glTranslatef(-.5f,-.5f,-.5f);
                BlockItemRenderer.render(Block.blocks[selected],textures,brightness);
            } else HandItemRenderer.renderItem(model.rightArm,textures,Item.items[selected-256],brightness);
        } finally {GL11.glPopMatrix();}
    }

    public static void render(Renderer renderer,float partial) {
        if(renderer.minecraft==null || renderer.minecraft.player==null || renderer.minecraft.cameraMode!=0)return;
        Player p=renderer.minecraft.player;HeldBlock held=renderer.heldBlock;
        int selected=held.getRenderedId(p.inventory.getSelected());
        float brightness=p.getBrightness(partial),equip=held.lastPos+(held.pos-held.lastPos)*partial;
        if(selected>=256 && GunModel.isGun(selected-256)) {GunRenderer.renderFirstPerson(p,selected-256,partial);return;}
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0,-(1-equip)*(selected>0 && selected<256?.6f:1.2f),0);
            float t=held.moving?Math.max(0,Math.min(1,(held.offset+partial)/7f)):0;
            float swing=(float)Math.sin(Math.sqrt(t)*Math.PI);
            if(selected>0 && selected<256 && Block.blocks[selected]!=null) {
                // Preserve the original block pose, without a visible hand.
                GL11.glTranslatef(-swing*.4f,(float)Math.sin(Math.sqrt(t)*Math.PI*2)*.2f,-(float)Math.sin(t*Math.PI)*.2f);
                GL11.glTranslatef(.56f,-.52f,-.72f);GL11.glRotatef(45,0,1,0);
                GL11.glRotatef(swing*80,0,1,0);GL11.glRotatef(-(float)Math.sin(t*t*Math.PI)*20,1,0,0);
                GL11.glScalef(.4f,.4f,.4f);GL11.glTranslatef(-.5f,-.5f,-.5f);
                BlockItemRenderer.render(Block.blocks[selected],renderer.minecraft.textureManager,brightness);
                return;
            }
            GL11.glTranslatef(-swing*.22f,(float)Math.sin(Math.sqrt(t)*Math.PI*2)*.12f,-(float)Math.sin(t*Math.PI)*.12f);
            GL11.glRotatef(-swing*25,0,1,0);
            if(selected>=256 && selected-256<Item.items.length && Item.items[selected-256]!=null) {
                Item item=Item.items[selected-256];
                if(item instanceof BowItem bow) {
                    float draw=bow.getDrawProgress(p,partial);
                    boolean aiming=bow.isDrawing(p);
                    GL11.glTranslatef(aiming?.32f:.48f,aiming?-.12f:-.30f,-.86f);
                    GL11.glRotatef(aiming?-50:-20,0,1,0);GL11.glRotatef(aiming?45:-30,0,0,1);
                    float size=aiming?.78f:.65f;GL11.glScalef(size,size,size);
                    GL11.glTranslatef(.16f*(1-draw),-.06f*(1-draw),0);
                    ItemMeshRenderer.render(renderer.minecraft.textureManager,bow.getTexture(p),brightness);
                } else {
                    boolean tool=item instanceof SwordItem || item instanceof MiningAxeItem || item instanceof ShovelItem;
                    GL11.glTranslatef(tool?.48f:.45f,tool?-.40f:-.30f,-.88f);
                    GL11.glRotatef(-25,0,1,0);GL11.glRotatef(tool?-45:-10,0,0,1);
                    GL11.glScalef(tool?.65f:.60f,tool?.65f:.60f,tool?.65f:.60f);
                    if(tool)GL11.glTranslatef(-.28f,.28f,0);
                    ItemMeshRenderer.renderItem(renderer.minecraft.textureManager,item,brightness);
                }
            } else renderArm(renderer,brightness);
        } finally {GL11.glPopMatrix();}
    }

    private static void renderArm(Renderer renderer,float brightness) {
        Player p=renderer.minecraft.player;if(p.getModel()==null)return;
        GL11.glPushMatrix();GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT);
        try {
            p.bindTexture(renderer.minecraft.textureManager);GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glColor3f(brightness,brightness,brightness);
            // Match the reference wrist silhouette at the standard 70-degree field of view.
            GL11.glTranslatef(.61159f,-.52911f,-.85f);
            GL11.glRotatef(-116.829f,0,0,1);GL11.glRotatef(39.173f,0,1,0);GL11.glRotatef(-91.318f,1,0,0);
            GL11.glScalef(.8f,-.8f,-.8f);
            // Put the wrist at the visible end, with the sleeve below the viewport.
            GL11.glTranslatef(0,.5f,0);GL11.glRotatef(180,1,0,0);
            ModelPart arm=p.getModel().rightArm;if(!arm.hasList)arm.generateList(.0625f);GL11.glCallList(arm.list);
        } finally {GL11.glPopAttrib();GL11.glPopMatrix();}
    }
}
