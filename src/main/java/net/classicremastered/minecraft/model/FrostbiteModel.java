package net.classicremastered.minecraft.model;

import java.io.IOException;
import net.classicremastered.toolkit.modeleditor.EditablePart;
import net.classicremastered.toolkit.modeleditor.ModelIO;

public final class FrostbiteModel extends ZombieModel {
    private final boolean baby;

    public FrostbiteModel() {
        this("frostbite", false);
    }

    public FrostbiteModel(String asset, boolean baby) {
        this.baby = baby;
        int oldWidth = TexturedQuad.ATLAS_W;
        int oldHeight = TexturedQuad.ATLAS_H;
        try (var stream = getClass().getResourceAsStream("/mob/" + asset + ".mmdl")) {
            if (stream == null)
                throw new IOException("Missing zombie model " + asset);
            var model = ModelIO.load(stream);
            TexturedQuad.ATLAS_W = model.atlasW;
            TexturedQuad.ATLAS_H = model.atlasH;
            for (EditablePart part : model.parts) {
                ModelPart mesh = build(part);
                switch (part.name) {
                case "head" -> head = mesh;
                case "body" -> body = mesh;
                case "right_arm" -> rightArm = mesh;
                case "left_arm" -> leftArm = mesh;
                case "right_leg" -> rightLeg = mesh;
                case "left_leg" -> leftLeg = mesh;
                default -> throw new IOException("Unknown Frostbite part: " + part.name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load zombie model " + asset, e);
        } finally {
            TexturedQuad.ATLAS_W = oldWidth;
            TexturedQuad.ATLAS_H = oldHeight;
        }
    }

    public boolean isBabyModel() {
        return baby;
    }

    private static ModelPart build(EditablePart part) {
        ModelPart mesh = new ModelPart(part.u, part.v);
        mesh.setBounds(part.x, part.y, part.z, part.w, part.h, part.d, part.inflate);
        mesh.setPosition(part.posX, part.posY, part.posZ);
        for (EditablePart child : part.children) {
            mesh.addChild(build(child));
        }
        return mesh;
    }

    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw,
            float headPitch, float scale) {
        if (!baby) {
            super.render(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
            return;
        }
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            BabyZombieModel.applyChildTransform(scale);
            scale *= 2.0F;
            head.render(scale);
            body.render(scale);
            rightArm.render(scale);
            leftArm.render(scale);
            rightLeg.render(scale);
            leftLeg.render(scale);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }

    @Override
    public void preAnimate(Object entity) {
        super.preAnimate(entity);
        hasHeadOverlay = false;
        headwear.render = false;
    }
}
