package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public final class CreeperModel extends Model {

    private ModelPart head = new ModelPart(0, 0);
    private ModelPart unused;
    private ModelPart body;
    private ModelPart leg1, leg2, leg3, leg4;

    public CreeperModel() {
        head.setBounds(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F);

        unused = new ModelPart(32, 0);
        unused.setBounds(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.5F);

        body = new ModelPart(16, 16);
        body.setBounds(-4.0F, 0.0F, -2.0F, 8, 12, 4, 0.0F);

        leg1 = new ModelPart(0, 16);
        leg1.setBounds(-2.0F, 0.0F, -2.0F, 4, 6, 4, 0.0F);
        leg1.setPosition(-2.0F, 12.0F, 4.0F);

        leg2 = new ModelPart(0, 16);
        leg2.setBounds(-2.0F, 0.0F, -2.0F, 4, 6, 4, 0.0F);
        leg2.setPosition(2.0F, 12.0F, 4.0F);

        leg3 = new ModelPart(0, 16);
        leg3.setBounds(-2.0F, 0.0F, -2.0F, 4, 6, 4, 0.0F);
        leg3.setPosition(-2.0F, 12.0F, -4.0F);

        leg4 = new ModelPart(0, 16);
        leg4.setBounds(-2.0F, 0.0F, -2.0F, 4, 6, 4, 0.0F);
        leg4.setPosition(2.0F, 12.0F, -4.0F);
    }

    @Override
    public final void render(float limbSwing, float limbSwingAmount, float ageInTicks,
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

        leg1.pitch = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
        leg2.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F)  * 1.4F * limbSwingAmount;
        leg3.pitch = MathHelper.cos(limbSwing * 0.6662F + 3.1415927F)  * 1.4F * limbSwingAmount;
        leg4.pitch = MathHelper.cos(limbSwing * 0.6662F)               * 1.4F * limbSwingAmount;
    }
}
