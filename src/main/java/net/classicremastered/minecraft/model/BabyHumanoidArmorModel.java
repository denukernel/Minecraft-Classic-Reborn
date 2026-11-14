// com/mojang/minecraft/model/BabyHumanoidArmorModel.java
package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

public class BabyHumanoidArmorModel extends HumanoidModel {
    private static final float SCALE = 0.65f;

    public BabyHumanoidArmorModel() {
        super(1.0F); // use inflated version
    }

    public static HumanoidModel compactArmor(float inflate) {
        HumanoidModel model = new HumanoidModel(inflate);
        // Keep the armor atlas UVs while fitting the compact husk and frostbite meshes.
        resize(model.head, .75F, .75F, 0, 0);
        resize(model.body, .5F, 5F / 12F, 0, 0);
        resize(model.rightArm, .5F, 1F / 3F, .5F, -1F / 3F);
        resize(model.leftArm, .5F, 1F / 3F, -.5F, -1F / 3F);
        resize(model.rightLeg, .5F, 5F / 12F, 0, 0);
        resize(model.leftLeg, .5F, 5F / 12F, 0, 0);
        return model;
    }

    private static void resize(
            ModelPart part, float widthScale, float heightScale, float xOffset, float yOffset) {
        for (Vertex vertex : part.vertices) {
            Vec3D point = vertex.vector;
            point.x = point.x * widthScale + xOffset;
            point.y = point.y * heightScale + yOffset;
            point.z *= widthScale;
        }
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(SCALE, SCALE, SCALE);
        GL11.glTranslatef(0.0f, (1.8f * (1.0f - SCALE)) * 0.95f, 0.0f); // lift so feet align
        super.render(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        GL11.glPopMatrix();
    }
}
