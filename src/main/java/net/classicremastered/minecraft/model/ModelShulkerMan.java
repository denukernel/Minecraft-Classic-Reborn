package net.classicremastered.minecraft.model;

public final class ModelShulkerMan extends Model {

    public ModelPart base;
    public ModelPart lid;
    public ModelPart head;

    public ModelShulkerMan() {
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64;
        TexturedQuad.ATLAS_H = 64;

        // --- bottom section ---
        base = new ModelPart(0, 28);
        base.setBounds(-8F, 0F, -8F, 16, 8, 16, 0F);
        base.setPosition(0F, 24F, 0F); // sits on ground

        // --- middle ring ---
        ModelPart mid = new ModelPart(0, 28);
        mid.setBounds(-8F, -8F, -8F, 16, 8, 16, 0F);
        base.addChild(mid);

        // --- upper ring ---
        ModelPart top = new ModelPart(0, 28);
        top.setBounds(-8F, -16F, -8F, 16, 8, 16, 0F);
        base.addChild(top);

        // --- lid column (neck) ---
        lid = new ModelPart(12, 0);
        lid.setBounds(-2F, -28F, -8F, 4, 12, 16, 0F);
        base.addChild(lid);

        // --- head cube ---
        head = new ModelPart(0, 52);
        head.setBounds(-3F, -34F, -3F, 6, 6, 6, 0F);
        base.addChild(head);

        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void preAnimate(Object entity) { }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        lid.pitch  = (float)Math.sin(ageInTicks * 0.05F) * 0.25F;
        head.yaw   = netHeadYaw * ((float)Math.PI / 180F);
        head.pitch = headPitch  * ((float)Math.PI / 180F);
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw,
                       float headPitch, float scale) {
        base.render(scale);
    }
}
