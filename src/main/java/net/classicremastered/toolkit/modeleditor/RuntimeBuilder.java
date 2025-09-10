package net.classicremastered.toolkit.modeleditor;

import net.classicremastered.minecraft.model.*;

public final class RuntimeBuilder {
    private RuntimeBuilder() {}

    public static Model buildClassicModel(final EditableModel em) {
        // set atlas size for TexturedQuad during model build
        final int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = em.atlasW; TexturedQuad.ATLAS_H = em.atlasH;

        Model m = new Model() {
            // we don't need fields; parts are rendered via hierarchy roots
            @Override
            public void render(float a, float b, float c, float yaw, float pitch, float scale) {
                for (EditablePart ep : em.parts) {
                    ModelPart p = toPart(ep, scale);
                    p.render(scale);
                }
            }
        };

        // restore atlas
        TexturedQuad.ATLAS_W = prevW; TexturedQuad.ATLAS_H = prevH;
        return m;
    }

    private static ModelPart toPart(EditablePart ep, float scale) {
        ModelPart p = new ModelPart(ep.u, ep.v);
        p.mirror = ep.mirror;
        p.setBounds(ep.x, ep.y, ep.z, ep.w, ep.h, ep.d, ep.inflate);
        p.setPosition(ep.posX, ep.posY, ep.posZ);
        p.pitch = ep.pitch; p.yaw = ep.yaw; p.roll = ep.roll;
        for (EditablePart child : ep.children) {
            ModelPart c = toPart(child, scale);
            p.addChild(c);
        }
        return p;
    }
}
