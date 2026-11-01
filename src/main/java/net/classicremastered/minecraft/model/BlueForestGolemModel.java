package net.classicremastered.minecraft.model;

/** Generated from the blue forest golem editable prototype. */
public final class BlueForestGolemModel extends Model {
    public final ModelPart head;
    public final ModelPart leftBranch;
    public final ModelPart leftLeaves;
    public final ModelPart rightBranch;
    public final ModelPart rightLeaves;
    public final ModelPart body;
    public final ModelPart bodyMoss;
    public final ModelPart rightArm;
    public final ModelPart leftArm;
    public final ModelPart rightLeg;
    public final ModelPart leftLeg;

    public BlueForestGolemModel() {
        groundOffset = 24f;
        int oldW = TexturedQuad.ATLAS_W, oldH = TexturedQuad.ATLAS_H;
        try {
            TexturedQuad.ATLAS_W = 128; TexturedQuad.ATLAS_H = 64;
            head = new ModelPart(0, 0);
            head.setBounds(-4.0f, -6.0f, -3.0f, 8, 6, 6, 0f);
            head.setPosition(0.0f, 9.0f, 0.0f);
            leftBranch = new ModelPart(64, 16);
            leftBranch.setBounds(-1.0f, -2.0f, -1.0f, 2, 2, 2, 0f);
            leftBranch.setPosition(-2.0f, -6.0f, 0.0f);
            leftLeaves = new ModelPart(64, 24);
            leftLeaves.setBounds(-2.0f, -0.5f, -2.0f, 4, 1, 4, 0f);
            leftLeaves.setPosition(0.0f, -2.0f, 0.0f);
            rightBranch = new ModelPart(88, 16);
            rightBranch.setBounds(-0.5f, -2.0f, -0.5f, 1, 2, 1, 0f);
            rightBranch.setPosition(2.3f, -6.0f, 0.5f);
            rightLeaves = new ModelPart(88, 24);
            rightLeaves.setBounds(-1.5f, -0.5f, -1.5f, 3, 1, 3, 0f);
            rightLeaves.setPosition(0.0f, -2.0f, 0.0f);
            body = new ModelPart(32, 0);
            body.setBounds(-4.0f, -4.0f, -2.5f, 8, 8, 5, 0f);
            body.setPosition(0.0f, 13.0f, 0.0f);
            bodyMoss = new ModelPart(32, 16);
            bodyMoss.setBounds(-4.0f, -0.3f, -2.5f, 8, 1, 5, 0f);
            bodyMoss.setPosition(0.0f, -4.0f, 0.0f);
            rightArm = new ModelPart(64, 0);
            rightArm.setBounds(-1.5f, 0.0f, -2.0f, 3, 8, 4, 0f);
            rightArm.setPosition(-5.5f, 10.0f, 0.0f);
            leftArm = new ModelPart(80, 0);
            leftArm.setBounds(-1.5f, 0.0f, -2.0f, 3, 8, 4, 0f);
            leftArm.setPosition(5.5f, 10.0f, 0.0f);
            rightLeg = new ModelPart(0, 16);
            rightLeg.setBounds(-1.5f, -1.0f, -2.0f, 3, 7, 4, 0f);
            rightLeg.setPosition(-2.2f, 18.0f, 0.0f);
            leftLeg = new ModelPart(16, 16);
            leftLeg.setBounds(-1.5f, -1.0f, -2.0f, 3, 7, 4, 0f);
            leftLeg.setPosition(2.2f, 18.0f, 0.0f);
            head.addChild(leftBranch);
            head.addChild(rightBranch);
            leftBranch.addChild(leftLeaves);
            rightBranch.addChild(rightLeaves);
            body.addChild(bodyMoss);
        } finally { TexturedQuad.ATLAS_W = oldW; TexturedQuad.ATLAS_H = oldH; }
    }

    @Override public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        head.yaw = Math.max(-0.65f, Math.min(0.65f, netHeadYaw / 57.295776f));
        head.pitch = Math.max(-0.4f, Math.min(0.4f, headPitch / 57.295776f)) + (float)Math.sin(ageInTicks * 0.06f) * 0.025f;
        head.roll = (float)Math.sin(ageInTicks * 0.035f) * 0.025f;
        float stride = (float)Math.cos(limbSwing * 0.6662f) * 0.65f * Math.min(1f, limbSwingAmount);
        rightLeg.pitch = stride;
        leftLeg.pitch = -stride;
        rightArm.pitch = -stride * 0.8f;
        leftArm.pitch = stride * 0.8f;
        rightArm.roll = 0.06f + (float)Math.sin(ageInTicks * 0.07f) * 0.035f;
        leftArm.roll = -rightArm.roll;
        body.y = 13f + (float)Math.sin(ageInTicks * 0.06f) * 0.1f;
        leftLeaves.roll = (float)Math.sin(ageInTicks * 0.09f) * 0.06f;
        rightLeaves.roll = (float)Math.cos(ageInTicks * 0.08f) * 0.06f;
    }

    @Override public void render(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        head.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
    }
}
