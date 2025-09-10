package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public final class SpiderModel extends Model {

    private ModelPart head = new ModelPart(32, 4);
    private ModelPart neck;
    private ModelPart body;
    private ModelPart leg1, leg2, leg3, leg4, leg5, leg6, leg7, leg8;

    public SpiderModel() {
        // Lower ground lift than humanoid so the spider doesn't hover
        this.groundOffset = 7.0F; // tune 11–13 if needed

        head.setBounds(-4.0F, -4.0F, -8.0F, 8, 8, 8, 0.0F);
        head.setPosition(0.0F, 0.0F, -3.0F);

        neck = new ModelPart(0, 0);
        neck.setBounds(-3.0F, -3.0F, -3.0F, 6, 6, 6, 0.0F);

        body = new ModelPart(0, 12);
        body.setBounds(-5.0F, -4.0F, -6.0F, 10, 8, 12, 0.0F);
        body.setPosition(0.0F, 0.0F, 9.0F);

        leg1 = new ModelPart(18, 0); leg1.setBounds(-15.0F, -1.0F, -1.0F, 16, 2, 2, 0.0F); leg1.setPosition(-4.0F, 0.0F,  2.0F);
        leg2 = new ModelPart(18, 0); leg2.setBounds(-1.0F,  -1.0F, -1.0F, 16, 2, 2, 0.0F); leg2.setPosition( 4.0F, 0.0F,  2.0F);
        leg3 = new ModelPart(18, 0); leg3.setBounds(-15.0F, -1.0F, -1.0F, 16, 2, 2, 0.0F); leg3.setPosition(-4.0F, 0.0F,  1.0F);
        leg4 = new ModelPart(18, 0); leg4.setBounds(-1.0F,  -1.0F, -1.0F, 16, 2, 2, 0.0F); leg4.setPosition( 4.0F, 0.0F,  1.0F);
        leg5 = new ModelPart(18, 0); leg5.setBounds(-15.0F, -1.0F, -1.0F, 16, 2, 2, 0.0F); leg5.setPosition(-4.0F, 0.0F,  0.0F);
        leg6 = new ModelPart(18, 0); leg6.setBounds(-1.0F,  -1.0F, -1.0F, 16, 2, 2, 0.0F); leg6.setPosition( 4.0F, 0.0F,  0.0F);
        leg7 = new ModelPart(18, 0); leg7.setBounds(-15.0F, -1.0F, -1.0F, 16, 2, 2, 0.0F); leg7.setPosition(-4.0F, 0.0F, -1.0F);
        leg8 = new ModelPart(18, 0); leg8.setBounds(-1.0F,  -1.0F, -1.0F, 16, 2, 2, 0.0F); leg8.setPosition( 4.0F, 0.0F, -1.0F);
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        head.render(scale);
        neck.render(scale);
        body.render(scale);
        leg1.render(scale); leg2.render(scale); leg3.render(scale); leg4.render(scale);
        leg5.render(scale); leg6.render(scale); leg7.render(scale); leg8.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        head.yaw   = netHeadYaw / 57.295776F;
        head.pitch = headPitch  / 57.295776F;

        float base = 0.7853982F; // 45°
        leg1.roll = -base;  leg2.roll =  base;
        leg3.roll = -base * 0.74F; leg4.roll = base * 0.74F;
        leg5.roll = -base * 0.74F; leg6.roll = base * 0.74F;
        leg7.roll = -base;  leg8.roll =  base;

        float yoff = 0.3926991F; // 22.5°
        leg1.yaw =  yoff * 2.0F; leg2.yaw = -yoff * 2.0F;
        leg3.yaw =  yoff;        leg4.yaw = -yoff;
        leg5.yaw = -yoff;        leg6.yaw =  yoff;
        leg7.yaw = -yoff * 2.0F; leg8.yaw =  yoff * 2.0F;

        float a1 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F)              * 0.4F) * limbSwingAmount;
        float a2 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + 3.1415927F) * 0.4F) * limbSwingAmount;
        float a3 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + 1.5707964F) * 0.4F) * limbSwingAmount;
        float a4 = -(MathHelper.cos(limbSwing * 0.6662F * 2.0F + 4.712389F)  * 0.4F) * limbSwingAmount;

        float r1 = Math.abs(MathHelper.sin(limbSwing * 0.6662F)              * 0.4F) * limbSwingAmount;
        float r2 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + 3.1415927F) * 0.4F) * limbSwingAmount;
        float r3 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + 1.5707964F) * 0.4F) * limbSwingAmount;
        float r4 = Math.abs(MathHelper.sin(limbSwing * 0.6662F + 4.712389F)  * 0.4F) * limbSwingAmount;

        leg1.yaw += a1; leg2.yaw -= a1;
        leg3.yaw += a2; leg4.yaw -= a2;
        leg5.yaw += a3; leg6.yaw -= a3;
        leg7.yaw += a4; leg8.yaw -= a4;

        leg1.roll += r1; leg2.roll -= r1;
        leg3.roll += r2; leg4.roll -= r2;
        leg5.roll += r3; leg6.roll -= r3;
        leg7.roll += r4; leg8.roll -= r4;
    }
}
