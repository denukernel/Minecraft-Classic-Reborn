package net.classicremastered.minecraft.render;

public class Frustrum {

    public float[][] frustrum = new float[16][16];
    public float[] projection = new float[16];
    public float[] modelview = new float[16];
    public float[] clipping = new float[16];

    public final boolean isBoxInFrustrum(
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] plane = frustrum[i];
            // The corner farthest along the plane normal has the greatest signed distance.
            float x = plane[0] >= 0 ? maxX : minX;
            float y = plane[1] >= 0 ? maxY : minY;
            float z = plane[2] >= 0 ? maxZ : minZ;
            if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= 0) return false;
        }

        return true;
    }
}
