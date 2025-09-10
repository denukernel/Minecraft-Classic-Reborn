package net.classicremastered.minecraft.model;

import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;

public final class ModelPart {

    public Vertex[] vertices;
    public TexturedQuad[] quads;
    private int u;
    private int v;

    public float x, y, z;
    public float pitch, yaw, roll;

    public boolean hasList = false;
    public int list = 0;
    public boolean mirror = false;
    public boolean render = true;
    /**
     * Copy transforms (pose + position) from another ModelPart.
     * Useful for syncing wear layers with their base limbs.
     */
    public void copyFrom(ModelPart other) {
        if (other == null) return;
        this.pitch = other.pitch;
        this.yaw   = other.yaw;
        this.roll  = other.roll;
        this.x     = other.x;
        this.y     = other.y;
        this.z     = other.z;
    }

    // --- NEW: child parts ---
    private final List<ModelPart> children = new ArrayList<ModelPart>();

    public ModelPart(int u, int v) {
        this.u = u;
        this.v = v;
    }

    // --- NEW: attach a child part that should transform with this part
    public void addChild(ModelPart child) {
        if (child != null) children.add(child);
    }

    public final void setBounds(float x, float y, float z,
                                int w, int h, int d, float inflate) {
        this.vertices = new Vertex[8];
        this.quads = new TexturedQuad[6];

        float x2 = x + w;
        float y2 = y + h;
        float z2 = z + d;

        x -= inflate; y -= inflate; z -= inflate;
        x2 += inflate; y2 += inflate; z2 += inflate;

        if (this.mirror) {
            float t = x2; x2 = x; x = t;
        }

        Vertex v000 = new Vertex(x,  y,  z,  0.0F, 0.0F);
        Vertex v100 = new Vertex(x2, y,  z,  0.0F, 8.0F);
        Vertex v110 = new Vertex(x2, y2, z,  8.0F, 8.0F);
        Vertex v010 = new Vertex(x,  y2, z,  8.0F, 0.0F);
        Vertex v001 = new Vertex(x,  y,  z2, 0.0F, 0.0F);
        Vertex v101 = new Vertex(x2, y,  z2, 0.0F, 8.0F);
        Vertex v111 = new Vertex(x2, y2, z2, 8.0F, 8.0F);
        Vertex v011 = new Vertex(x,  y2, z2, 8.0F, 0.0F);

        this.vertices[0] = v000;
        this.vertices[1] = v100;
        this.vertices[2] = v110;
        this.vertices[3] = v010;
        this.vertices[4] = v001;
        this.vertices[5] = v101;
        this.vertices[6] = v111;
        this.vertices[7] = v011;

        this.quads[0] = new TexturedQuad(new Vertex[]{v101, v100, v110, v111}, this.u + d + w, this.v + d, this.u + d + w + d, this.v + d + h); // +X
        this.quads[1] = new TexturedQuad(new Vertex[]{v000, v001, v011, v010}, this.u,              this.v + d, this.u + d,              this.v + d + h); // -X
        this.quads[2] = new TexturedQuad(new Vertex[]{v101, v001, v000, v100}, this.u + d,          this.v,     this.u + d + w,          this.v + d);     // -Y
        this.quads[3] = new TexturedQuad(new Vertex[]{v110, v010, v011, v111}, this.u + d + w,      this.v,     this.u + d + w + w,      this.v + d);     // +Y
        this.quads[4] = new TexturedQuad(new Vertex[]{v100, v000, v010, v110}, this.u + d,          this.v + d, this.u + d + w,          this.v + d + h); // -Z
        this.quads[5] = new TexturedQuad(new Vertex[]{v001, v101, v111, v011}, this.u + d + w + d,  this.v + d, this.u + d + w + d + w,  this.v + d + h); // +Z

        if (this.mirror) {
            for (int i = 0; i < this.quads.length; ++i) {
                TexturedQuad q = this.quads[i];
                Vertex[] rev = new Vertex[q.vertices.length];
                for (int j = 0; j < q.vertices.length; ++j) {
                    rev[j] = q.vertices[q.vertices.length - 1 - j];
                }
                q.vertices = rev;
            }
        }
    }

    public final void setPosition(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }

    public final void render(float scale) {
        if (!this.render) return;
        if (!this.hasList) generateList(scale);

        // If no children and no transform, keep the old fast path
        boolean noTransform = (pitch == 0.0F && yaw == 0.0F && roll == 0.0F &&
                               x == 0.0F && y == 0.0F && z == 0.0F);
        if (children.isEmpty() && noTransform) {
            GL11.glCallList(this.list);
            return;
        }

        GL11.glPushMatrix();
        if (x != 0.0F || y != 0.0F || z != 0.0F) {
            GL11.glTranslatef(x * scale, y * scale, z * scale);
        }
        if (roll != 0.0F) GL11.glRotatef(roll * 57.295776F, 0.0F, 0.0F, 1.0F);
        if (yaw  != 0.0F) GL11.glRotatef(yaw  * 57.295776F, 0.0F, 1.0F, 0.0F);
        if (pitch!= 0.0F) GL11.glRotatef(pitch* 57.295776F, 1.0F, 0.0F, 0.0F);

        GL11.glCallList(this.list);

        // render children in the parent's transformed space
        for (int i = 0; i < children.size(); i++) {
            children.get(i).render(scale);
        }
        GL11.glPopMatrix();
    }

    public void generateList(float scale) {
        this.list = GL11.glGenLists(1);
        GL11.glNewList(this.list, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);

        for (int i = 0; i < this.quads.length; ++i) {
            TexturedQuad q = this.quads[i];

            Vec3D a = q.vertices[1].vector.subtract(q.vertices[0].vector).normalize();
            Vec3D b = q.vertices[1].vector.subtract(q.vertices[2].vector).normalize();
            Vec3D n = new Vec3D(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
            ).normalize();
            GL11.glNormal3f(n.x, n.y, n.z);

            for (int v = 0; v < 4; ++v) {
                Vertex vv = q.vertices[v];
                GL11.glTexCoord2f(vv.u, vv.v);
                GL11.glVertex3f(vv.vector.x * scale, vv.vector.y * scale, vv.vector.z * scale);
            }
        }

        GL11.glEnd();
        GL11.glEndList();
        this.hasList = true;
    }
}
