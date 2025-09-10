// File: src/com/mojang/minecraft/model/ChickenModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;
import net.classicremastered.minecraft.mob.Chicken;

public final class ChickenModel extends AnimalModel {

    private final ModelPart head;
    private final ModelPart bill;
    private final ModelPart chin;
    private final ModelPart body;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    private float wingRotation = 0.0F; // synced from entity

    public ChickenModel() {
        super(0, 0.0F);

        int yOff = 16;
        this.groundOffset = 24.5F;

        this.head = new ModelPart(0, 0);
        this.head.setBounds(-2F, -6F, -2F, 4, 6, 3, 0F);
        this.head.setPosition(0F, -1 + yOff, -4F);

        this.bill = new ModelPart(14, 0);
        this.bill.setBounds(-2F, -4F, -4F, 4, 2, 2, 0F);
        this.bill.setPosition(0F, -1 + yOff, -4F);

        this.chin = new ModelPart(14, 4);
        this.chin.setBounds(-1F, -2F, -3F, 2, 2, 2, 0F);
        this.chin.setPosition(0F, -1 + yOff, -4F);

        this.body = new ModelPart(0, 9);
        this.body.setBounds(-3F, -4F, -3F, 6, 8, 6, 0F);
        this.body.setPosition(0F, 0 + yOff, 0F);

        this.rightLeg = new ModelPart(26, 0);
        this.rightLeg.setBounds(-1F, 0F, -3F, 3, 5, 3, 0F);
        this.rightLeg.setPosition(-2F, 3 + yOff, 1F);

        this.leftLeg = new ModelPart(26, 0);
        this.leftLeg.setBounds(-1F, 0F, -3F, 3, 5, 3, 0F);
        this.leftLeg.setPosition(1F, 3 + yOff, 1F);

        this.rightWing = new ModelPart(24, 13);
        this.rightWing.setBounds(0F, 0F, -3F, 1, 4, 6, 0F);
        this.rightWing.setPosition(-4F, -3 + yOff, 0F);

        this.leftWing = new ModelPart(24, 13);
        this.leftWing.setBounds(-1F, 0F, -3F, 1, 4, 6, 0F);
        this.leftWing.setPosition(4F, -3 + yOff, 0F);
    }

    /** Sync wing rotation from entity */
    public void syncFromEntity(Chicken chicken) {
        if (chicken != null) {
            this.wingRotation = chicken.wingRotation;
        }
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        head.render(scale);
        bill.render(scale);
        chin.render(scale);
        body.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        rightWing.render(scale);
        leftWing.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        float toRad = (float)(Math.PI / 180D);

        // head rotation
        head.pitch = -(headPitch * toRad);
        head.yaw   = netHeadYaw * toRad;
        bill.copyFrom(head);
        chin.copyFrom(head);

        // body fixed upright
        body.pitch = (float)Math.PI * 0.5F;

        // walking legs
        rightLeg.pitch = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        leftLeg.pitch  = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;

        // wings: Beta 1.8 style
        rightWing.roll = (float)Math.sin(this.wingRotation) * 0.6F;
        leftWing.roll  = -rightWing.roll;
    }
}
