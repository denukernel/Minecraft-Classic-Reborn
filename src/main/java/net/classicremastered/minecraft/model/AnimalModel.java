package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public class AnimalModel extends Model {

    public ModelPart head = new ModelPart(0, 0);
    public ModelPart body, leg1, leg2, leg3, leg4;

    public AnimalModel(int legHeight, float inflate) {
        head.setBounds(-4.0F, -4.0F, -8.0F, 8, 8, 8, 0.0F);
        head.setPosition(0.0F, (float)(18 - legHeight), -6.0F);

        body = new ModelPart(28, 8);
        body.setBounds(-5.0F, -10.0F, -7.0F, 10, 16, 8, 0.0F);
        body.setPosition(0.0F, (float)(17 - legHeight), 2.0F);

        leg1 = new ModelPart(0, 16);
        leg1.setBounds(-2.0F, 0.0F, -2.0F, 4, legHeight, 4, 0.0F);
        leg1.setPosition(-3.0F, (float)(24 - legHeight), 7.0F);

        leg2 = new ModelPart(0, 16);
        leg2.setBounds(-2.0F, 0.0F, -2.0F, 4, legHeight, 4, 0.0F);
        leg2.setPosition(3.0F, (float)(24 - legHeight), 7.0F);

        leg3 = new ModelPart(0, 16);
        leg3.setBounds(-2.0F, 0.0F, -2.0F, 4, legHeight, 4, 0.0F);
        leg3.setPosition(-3.0F, (float)(24 - legHeight), -5.0F);

        leg4 = new ModelPart(0, 16);
        leg4.setBounds(-2.0F, 0.0F, -2.0F, 4, legHeight, 4, 0.0F);
        leg4.setPosition(3.0F, (float)(24 - legHeight), -5.0F);
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                             float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        head.render(scale);
        body.render(scale);
        leg1.render(scale);
        leg2.render(scale);
        leg3.render(scale);
        leg4.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;

        body.pitch = 1.5707964F;

        leg1.pitch = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
        leg2.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F)  * 1.4F * limbSwingAmount;
        leg3.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F)  * 1.4F * limbSwingAmount;
        leg4.pitch = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
    }
}
