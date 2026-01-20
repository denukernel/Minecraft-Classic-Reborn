package net.classicremastered.minecraft.mob;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.BabyZombieModel;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public abstract class HumanoidMob extends Mob {

    public static final long serialVersionUID = 0L;
    public boolean helmet = Math.random() < 0.20000000298023224D;
    public boolean armor = Math.random() < 0.20000000298023224D;

    public HumanoidMob(Level var1, float var2, float var3, float var4) {
        super(var1);
        this.setPos(var2, var3, var4);
    }
 // In HumanoidMob.java
    public void renderArmorLayer(TextureManager tm, float anim, float runAmt, float age, float yaw, float pitch, float scale) {
        if (!(helmet || armor)) return;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load("/armor/plate.png"));
        GL11.glDisable(GL11.GL_CULL_FACE);

        boolean isBaby = (this instanceof BabyZombie);
        HumanoidModel armorModel = (HumanoidModel) modelCache
                .getModel(isBaby ? "humanoid.armor.baby" : "humanoid.armor");

        // Visibility flags
        armorModel.head.render = helmet;
        armorModel.body.render = armor;
        armorModel.rightArm.render = armor;
        armorModel.leftArm.render = armor;
        armorModel.rightLeg.render = false;
        armorModel.leftLeg.render = false;

        if (modelCache.getModel(this.modelName) instanceof HumanoidModel base) {
            armorModel.head.yaw = base.head.yaw;
            armorModel.head.pitch = base.head.pitch;
            armorModel.rightArm.pitch = base.rightArm.pitch;
            armorModel.rightArm.roll  = base.rightArm.roll;
            armorModel.leftArm.pitch  = base.leftArm.pitch;
            armorModel.leftArm.roll   = base.leftArm.roll;
            armorModel.rightLeg.pitch = base.rightLeg.pitch;
            armorModel.leftLeg.pitch  = base.leftLeg.pitch;
        }

        GL11.glPushMatrix();
        if (isBaby) {
            float childScale = BabyZombieModel.CHILD_SCALE;
            float lift = (1.8f * (1.0f - childScale));
            GL11.glTranslatef(0.0f, lift, 0.0f);
            GL11.glScalef(childScale, childScale, childScale);
        }

        armorModel.head.render(scale);
        armorModel.body.render(scale);
        armorModel.rightArm.render(scale);
        armorModel.leftArm.render(scale);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    public void renderModel(TextureManager textures, float limbSwing, float limbSwingAmount,
                            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        // --- Render base mob ---
        super.renderModel(textures, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        Model model = modelCache.getModel(this.modelName);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (this.allowAlpha) GL11.glEnable(GL11.GL_CULL_FACE);

        // ---- Hair layer ----
        if (this.hasHair && model instanceof HumanoidModel) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            HumanoidModel h = (HumanoidModel) model;
            h.headwear.yaw = h.head.yaw;
            h.headwear.pitch = h.head.pitch;
            h.headwear.render(scale);
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

}
