package net.classicremastered.minecraft.model;

public final class ModelSlime extends Model {

    public ModelPart cube;
    public ModelPart eye0;
    public ModelPart eye1;
    public ModelPart mouth;

    public ModelSlime() {
        // --- texture atlas ---
        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = 64;
        TexturedQuad.ATLAS_H = 32;

        // --- main cube body ---
        cube = new ModelPart(0, 0);
        cube.setBounds(-4F, 16F, -4F, 8, 8, 8, 0F); // corrected Y alignment
        cube.setPosition(0F, 8F, 0F);

        // inner cube (gel layer)
        ModelPart gel = new ModelPart(0, 16);
        gel.setBounds(-3F, 17F, -3F, 6, 6, 6, 0F);
        cube.addChild(gel);

        // --- eyes ---
        eye0 = new ModelPart(32, 0);
        eye0.setBounds(-3.3F, 18F, -3.5F, 2, 2, 2, 0F);
        cube.addChild(eye0);

        eye1 = new ModelPart(32, 4);
        eye1.setBounds(1.3F, 18F, -3.5F, 2, 2, 2, 0F);
        cube.addChild(eye1);

        // --- mouth ---
        mouth = new ModelPart(32, 8);
        mouth.setBounds(0F, 21F, -3.5F, 1, 1, 1, 0F);
        cube.addChild(mouth);

        TexturedQuad.ATLAS_W = prevW;
        TexturedQuad.ATLAS_H = prevH;
    }

    @Override
    public void preAnimate(Object entity) { }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        // simple idle bounce
        float bounce = (float)Math.sin(ageInTicks * 0.3F) * 0.15F;
        cube.setPosition(0F, 8F + bounce * 4F, 0F);
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw,
                       float headPitch, float scale) {
        cube.render(scale);
    }
}
