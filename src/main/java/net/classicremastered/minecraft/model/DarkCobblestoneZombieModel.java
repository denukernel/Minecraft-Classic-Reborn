package net.classicremastered.minecraft.model;

import net.classicremastered.toolkit.modeleditor.ModelIO;

import java.io.IOException;

public final class DarkCobblestoneZombieModel extends ZombieModel {
    public DarkCobblestoneZombieModel() {
        int oldWidth = TexturedQuad.ATLAS_W;
        int oldHeight = TexturedQuad.ATLAS_H;
        String path = "/mob/dark_cobblestone_zombie.mmdl";
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing mob model " + path);
            var model = ModelIO.load(stream);
            TexturedQuad.ATLAS_W = model.atlasW;
            TexturedQuad.ATLAS_H = model.atlasH;
            for (var part : model.parts) {
                ModelPart mesh = new ModelPart(part.u, part.v);
                mesh.mirror = part.mirror;
                mesh.setBounds(part.x, part.y, part.z, part.w, part.h, part.d, part.inflate);
                mesh.setPosition(part.posX, part.posY, part.posZ);
                switch (part.name) {
                    case "head" -> head = mesh;
                    case "body" -> body = mesh;
                    case "right_arm" -> rightArm = mesh;
                    case "left_arm" -> leftArm = mesh;
                    case "right_leg" -> rightLeg = mesh;
                    case "left_leg" -> leftLeg = mesh;
                    default -> throw new IOException("Unknown mob model part " + part.name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load mob model " + path, e);
        } finally {
            TexturedQuad.ATLAS_W = oldWidth;
            TexturedQuad.ATLAS_H = oldHeight;
        }
    }

    @Override
    public void preAnimate(Object entity) {
        super.preAnimate(entity);
        hasHeadOverlay = false;
        headwear.render = false;
    }
}
