package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public final class BeeModel extends Model {

    public ModelPart body;
    public ModelPart head;
    public ModelPart stinger;
    public ModelPart leftWing;
    public ModelPart rightWing;
    public ModelPart leftAntenna;
    public ModelPart rightAntenna;

    public BeeModel() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64;
        TexturedQuad.ATLAS_H = 64;

        // Body
        body = new ModelPart(0, 0);
        body.setBounds(-3.5F, -4.0F, -5.0F, 7, 7, 10, 0.0F);

        // Head
        head = new ModelPart(0, 0);
        head.setBounds(-2.0F, -2.0F, -7.0F, 4, 4, 4, 0.0F);

        // Stinger
        stinger = new ModelPart(26, 7);
        stinger.setBounds(-0.5F, 2.0F, 5.0F, 1, 1, 2, 0.0F);

        // Antennae
        leftAntenna = new ModelPart(2, 0);
        leftAntenna.setBounds(1, -4, -7, 1, 2, 3, 0.0F);

        rightAntenna = new ModelPart(2, 3);
        rightAntenna.setBounds(-2, -4, -7, 1, 2, 3, 0.0F);

        // Wings
        leftWing = new ModelPart(0, 18);
        leftWing.setBounds(0, -4, -3, 9, 1, 6, 0.0F);

        rightWing = new ModelPart(0, 18);
        rightWing.mirror = true;
        rightWing.setBounds(-9, -4, -3, 9, 1, 6, 0.0F);

        // Attach children so they move with body
        body.addChild(head);
        body.addChild(stinger);
        body.addChild(leftAntenna);
        body.addChild(rightAntenna);
        body.addChild(leftWing);
        body.addChild(rightWing);

        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, float scale) {
        // Wing flap
        float flap = MathHelper.cos(age * 20F) * (float)Math.PI * 0.15F;
        this.rightWing.roll = flap;
        this.leftWing.roll = -flap;

        // Body bobbing
        body.y = MathHelper.cos(age * 0.18F) * 0.5F;

        // Render root part (children will follow)
        body.render(scale);
    }
}
