package net.classicremastered.minecraft.model;

public final class CowModel extends AnimalModel {
    public CowModel() {
        super(12, 0);
        int previousWidth = TexturedQuad.ATLAS_W;
        int previousHeight = TexturedQuad.ATLAS_H;
        TexturedQuad.ATLAS_W = TexturedQuad.ATLAS_H = 64;
        try {
            head = part(0, 0, -4, -4, -6, 8, 8, 6, 0, 4, -8, false);
            ModelPart muzzle = part(1, 33, -3, 1, -7, 6, 3, 1, 0, 0, 0, false);
            ModelPart rightHorn = part(22, 0, -5, -5, -5, 1, 3, 1, 0, 0, 0, false);
            ModelPart leftHorn = part(22, 0, 4, -5, -5, 1, 3, 1, 0, 0, 0, false);
            body = part(18, 4, -6, -10, -7, 12, 18, 10, 0, 5, 2, false);
            ModelPart udder = part(52, 0, -2, 2, -8, 4, 6, 1, 0, 0, 0, false);
            leg1 = part(0, 16, -2, 0, -2, 4, 12, 4, -4, 12, 7, false);
            leg2 = part(0, 16, -2, 0, -2, 4, 12, 4, 4, 12, 7, true);
            leg3 = part(0, 16, -2, 0, -2, 4, 12, 4, -4, 12, -5, false);
            leg4 = part(0, 16, -2, 0, -2, 4, 12, 4, 4, 12, -5, true);
            head.addChild(muzzle);
            head.addChild(rightHorn);
            head.addChild(leftHorn);
            body.addChild(udder);
        } finally {
            TexturedQuad.ATLAS_W = previousWidth;
            TexturedQuad.ATLAS_H = previousHeight;
        }
    }

    private static ModelPart part(
            int u,
            int v,
            int x,
            int y,
            int z,
            int width,
            int height,
            int depth,
            int pivotX,
            int pivotY,
            int pivotZ,
            boolean mirror) {
        ModelPart part = new ModelPart(u, v);
        part.mirror = mirror;
        part.setBounds(x, y, z, width, height, depth, 0);
        part.setPosition(pivotX, pivotY, pivotZ);
        return part;
    }
}
