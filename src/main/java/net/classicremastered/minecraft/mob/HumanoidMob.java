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

    @Override
    public void renderModel(TextureManager textures, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        // --- Render base mob (uses its modelName: zombie / zombie_baby etc.) ---
        super.renderModel(textures, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        Model model = modelCache.getModel(this.modelName);
        GL11.glEnable(GL11.GL_ALPHA_TEST);

        if (this.allowAlpha) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        // ---- Hair layer (Humanoid only) ----
        if (this.hasHair && model instanceof HumanoidModel) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            HumanoidModel h = (HumanoidModel) model;
            h.headwear.yaw = h.head.yaw;
            h.headwear.pitch = h.head.pitch;
            h.headwear.render(scale);
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        // ---- Armor layer ----
        if (this.armor || this.helmet) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/armor/plate.png"));
            GL11.glDisable(GL11.GL_CULL_FACE);

            // Decide armor model: adult vs baby
            boolean isBaby = (this instanceof BabyZombie); // extend check if you add other babies
            HumanoidModel armorModel = (HumanoidModel) modelCache
                    .getModel(isBaby ? "humanoid.armor.baby" : "humanoid.armor");

            // Which parts are visible
            armorModel.head.render = this.helmet;
            armorModel.body.render = this.armor;
            armorModel.rightArm.render = this.armor;
            armorModel.leftArm.render = this.armor;
            armorModel.rightLeg.render = false;
            armorModel.leftLeg.render = false;

            // Sync rotations if the base model is humanoid-like
            if (model instanceof HumanoidModel) {
                HumanoidModel base = (HumanoidModel) model;
                armorModel.head.yaw = base.head.yaw;
                armorModel.head.pitch = base.head.pitch;

                armorModel.rightArm.pitch = base.rightArm.pitch;
                armorModel.rightArm.roll = base.rightArm.roll;
                armorModel.leftArm.pitch = base.leftArm.pitch;
                armorModel.leftArm.roll = base.leftArm.roll;

                armorModel.rightLeg.pitch = base.rightLeg.pitch;
                armorModel.leftLeg.pitch = base.leftLeg.pitch;
            }

            // === Render armor ===
            GL11.glPushMatrix();
            if (isBaby) {
                // same scale/lift as BabyZombieModel
                float childScale = BabyZombieModel.CHILD_SCALE;
                float lift = (1.8f * (1.0f - childScale));
                GL11.glTranslatef(0.0f, lift, 0.0f);
                GL11.glScalef(childScale, childScale, childScale);
            }

            armorModel.head.render(scale);
            armorModel.body.render(scale);
            armorModel.rightArm.render(scale);
            armorModel.leftArm.render(scale);
            armorModel.rightLeg.render(scale);
            armorModel.leftLeg.render(scale);

            GL11.glPopMatrix();

            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

}
