package net.classicremastered.minecraft.model;
public final class HouseVandalizerModel extends Model {
    public final ModelPart head, nose, body, rightArm, leftArm, rightLeg, leftLeg, plankPack, plankTop;
    private int strikeTicks;
    private boolean kicking;
    public HouseVandalizerModel() {
        groundOffset = 32;
        int oldW = TexturedQuad.ATLAS_W, oldH = TexturedQuad.ATLAS_H;
        try {
            TexturedQuad.ATLAS_W = TexturedQuad.ATLAS_H = 128;
            head = new ModelPart(0, 0);
            head.setBounds(-4f, -8f, -4f, 8, 8, 8, 0);
            head.setPosition(0f, 8f, 0f);
            nose = new ModelPart(33, 0);
            nose.setBounds(-1f, -2f, -4f, 2, 4, 4, 0);
            nose.setPosition(0f, -2f, -3f);
            body = new ModelPart(46, 0);
            body.setBounds(-4f, 0f, -2f, 8, 12, 4, 0);
            body.setPosition(0f, 8f, 0f);
            rightArm = new ModelPart(71, 0);
            rightArm.setBounds(-2f, 0f, -2f, 4, 12, 4, 0);
            rightArm.setPosition(-6f, 8f, 0f);
            leftArm = new ModelPart(88, 0);
            leftArm.setBounds(-2f, 0f, -2f, 4, 12, 4, 0);
            leftArm.setPosition(6f, 8f, 0f);
            rightLeg = new ModelPart(105, 0);
            rightLeg.setBounds(-2f, 0f, -2f, 4, 12, 4, 0);
            rightLeg.setPosition(-2f, 20f, 0f);
            leftLeg = new ModelPart(0, 17);
            leftLeg.setBounds(-2f, 0f, -2f, 4, 12, 4, 0);
            leftLeg.setPosition(2f, 20f, 0f);
            plankPack = new ModelPart(17, 17);
            plankPack.setBounds(-5f, 0f, 0f, 10, 8, 4, 0);
            plankPack.setPosition(0f, 9f, 2f);
            plankTop = new ModelPart(46, 17);
            plankTop.setBounds(-6f, -1f, 0f, 12, 3, 4, 0);
            plankTop.setPosition(0f, 8f, 2f);
            head.addChild(nose);
        } finally {
            TexturedQuad.ATLAS_W = oldW;
            TexturedQuad.ATLAS_H = oldH;
        }
    }

    @Override
    public void preAnimate(Object entity) {
        strikeTicks = entity instanceof net.classicremastered.minecraft.mob.HouseVandalizer v ? v.throwTicks : 0;
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale) {
        head.pitch = head.yaw = head.roll = 0;
        nose.pitch = nose.yaw = nose.roll = 0;
        body.pitch = body.yaw = body.roll = 0;
        rightArm.pitch = rightArm.yaw = rightArm.roll = 0;
        leftArm.pitch = leftArm.yaw = leftArm.roll = 0;
        rightLeg.pitch = rightLeg.yaw = rightLeg.roll = 0;
        leftLeg.pitch = leftLeg.yaw = leftLeg.roll = 0;
        plankPack.pitch = plankPack.yaw = plankPack.roll = 0;
        plankTop.pitch = plankTop.yaw = plankTop.roll = 0;
        float stride = (float) Math.cos(swing * .6662f) * .8f * Math.min(1, amount);
        head.yaw = Math.max(-.7f, Math.min(.7f, yaw / 57.295776f));
        head.pitch = Math.max(-.4f, Math.min(.4f, pitch / 57.295776f));
        rightLeg.pitch = stride;
        leftLeg.pitch = -stride;
        rightArm.pitch = -1.1f - stride * .2f;
        leftArm.pitch = -1.1f + stride * .2f;
        if (strikeTicks > 0) {
            rightArm.pitch = leftArm.pitch = .8f;
            plankTop.roll = (float) Math.sin(strikeTicks * .3f) * .12f;
        }
    }

    @Override
    public void render(float swing, float amount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, amount, age, yaw, pitch, scale);
        head.render(scale);
        body.render(scale);
        rightArm.render(scale);
        leftArm.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        plankPack.render(scale);
        plankTop.render(scale);
    }
}
