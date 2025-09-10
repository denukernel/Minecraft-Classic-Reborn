package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;

/**
 * Generic triangle-mesh Model renderer for imported OBJ data.
 * Texture is bound externally (via entity.textureName).
 */
public class MeshModel extends Model {
    protected final ObjMesh.Data mesh;

    // Configurable tweaks
    public float scale  = 0.09f;   // overall model size
    public float yawFix = 180f;    // Classic models face -Z by default
    public float pivotY = 0.0f;    // vertical offset
    public boolean orientZupToYup = true;

    /**
     * Forward axis correction: rotates OBJ forward (+X) into Classic forward (-Z).
     * Set per model if needed (e.g. Bob-omb uses +90).
     */
    public float forwardFix = 0f;

    public MeshModel(ObjMesh.Data data) {
        this.mesh = data;
    }

    public void render(float walk, float walkTime, float bob, float yaw, float pitch, float scaleParam) {
        GL11.glPushMatrix();

        // Correct orientation
        GL11.glRotatef(yawFix, 0f, 1f, 0f);
        if (forwardFix != 0f) {
            GL11.glRotatef(forwardFix, 0f, 1f, 0f);
        }

        // Adjust vertical pivot
        if (pivotY != 0f) {
            GL11.glTranslatef(0f, pivotY, 0f);
        }

        // Reorient OBJ coordinate system (Z-up → Y-up)
        if (orientZupToYup) {
            GL11.glRotatef(-90f, 1f, 0f, 0f);
        }

        // Apply scale
        GL11.glScalef(scale, scale, scale);

        // Basic alpha-test rendering
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);

        GL11.glBegin(GL11.GL_TRIANGLES);
        for (ObjMesh.Tri t : mesh.tris) {
            emit(mesh.verts.get(t.a));
            emit(mesh.verts.get(t.b));
            emit(mesh.verts.get(t.c));
        }
        GL11.glEnd();

        GL11.glPopMatrix();
    }

    private static void emit(ObjMesh.Vertex v) {
        GL11.glNormal3f(v.nx, v.ny, v.nz);
        GL11.glTexCoord2f(v.u, v.v);
        GL11.glVertex3f(v.x, v.y, v.z);
    }
}
