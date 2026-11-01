package net.classicremastered.minecraft.model;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/** Solid, editable cuboid gun meshes. Local forward is -Z; dimensions are world units. */
public final class GunModel {
    public static final Vec3D MUZZLE = new Vec3D(0, 0, -.73f);
    private record Part(float x, float y, float z, float w, float h, float d, int color, boolean glow) { }
    private static final Part[] PHYSICS = build(false);
    private static final Part[] GRAVITY = build(true);

    public static boolean isGun(int id) { return id == 10 || id == 11; }
    public static int partCount(int id) { return (id == 11 ? GRAVITY : PHYSICS).length; }

    private static void box(List<Part> parts, float x, float y, float z, float w, float h, float d, int color) {
        parts.add(new Part(x, y, z, w, h, d, color, false));
    }

    private static Part[] build(boolean gravity) {
        List<Part> p = new ArrayList<>();
        int dark = 0x252f3b, steel = gravity ? 0x9c7960 : 0xa5b9c9;
        int trim = gravity ? 0x76503a : 0x52687f, energy = gravity ? 0xffa52e : 0x37e6fa;
        float width = gravity ? .36f : .30f;
        box(p, 0, 0, -.03f, width, .26f, .62f, dark); // receiver
        box(p, 0, .13f, .04f, width + .04f, .07f, .46f, steel); // upper shell
        box(p, 0, -.13f, .03f, width, .05f, .48f, trim);
        box(p, 0, -.28f, .18f, .13f, .32f, .17f, dark); // grip
        box(p, 0, -.42f, .18f, .17f, .06f, .20f, steel);
        for (int i = 0; i < 4; i++) box(p, 0, -.19f - i * .055f, .272f, .135f, .018f, .018f, trim);
        box(p, 0, -.26f, -.05f, .035f, .22f, .035f, steel); // trigger guard
        box(p, 0, -.36f, .055f, .035f, .035f, .25f, steel);
        box(p, 0, .05f, .32f, width + .07f, .20f, .13f, trim); // rear cap
        box(p, 0, .05f, .40f, width, .15f, .035f, steel);
        box(p, 0, 0, -.40f, width + .09f, .34f, .15f, steel); // chamber collar
        box(p, 0, 0, -.48f, width - .02f, .22f, .045f, dark);
        p.add(new Part(0, 0, -.51f, .17f, .17f, .06f, energy, true));
        p.add(new Part(0, 0, -.55f, .08f, .08f, .03f, 0xe9ffff, true));
        // Three separated prongs form a real open muzzle, rather than a sprite extrusion.
        for (int i = 0; i < 3; i++) {
            double angle = (i * 120 + 90) * Math.PI / 180;
            float x = (float) Math.cos(angle) * .23f, y = (float) Math.sin(angle) * .23f;
            box(p, x, y, -.57f, .09f, .09f, .37f, trim);
            box(p, x * .77f, y * .77f, -.73f, .11f, .11f, .10f, steel);
            p.add(new Part(x * .65f, y * .65f, -.785f, .055f, .055f, .015f, energy, true));
        }
        for (int side : new int[] {-1, 1}) {
            box(p, side * (width * .5f + .025f), .025f, -.05f, .06f, .18f, .35f, trim);
            for (int i = 0; i < 4; i++) {
                float z = .085f - i * .085f;
                p.add(new Part(side * (width * .5f + .058f), .03f, z, .015f, .12f, .028f, energy, true));
            }
            if (gravity) { // large side-mounted induction tanks distinguish the gravity gun
                box(p, side * .27f, -.02f, .06f, .12f, .23f, .46f, 0x593e30);
                for (int i = 0; i < 4; i++) box(p, side * .27f, -.02f, .23f - i * .11f, .16f, .27f, .035f, steel);
                p.add(new Part(side * .337f, -.02f, .06f, .012f, .09f, .31f, energy, true));
            }
        }
        if (!gravity) {
            box(p, 0, .21f, .035f, .10f, .085f, .34f, dark); // sight rail
            p.add(new Part(0, .258f, -.09f, .035f, .02f, .05f, energy, true));
        }
        return p.toArray(new Part[0]);
    }

    public static void render(int id, float brightness) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_CULL_FACE);
            for (Part p : id == 11 ? GRAVITY : PHYSICS) draw(p, Math.max(.25f, brightness));
        } finally { GL11.glPopAttrib(); }
    }

    private static void draw(Part p, float brightness) {
        float x0=p.x-p.w/2, x1=p.x+p.w/2, y0=p.y-p.h/2, y1=p.y+p.h/2, z0=p.z-p.d/2, z1=p.z+p.d/2;
        GL11.glBegin(GL11.GL_QUADS);
        color(p, brightness, .85f); // front -Z
        GL11.glVertex3f(x1,y0,z0); GL11.glVertex3f(x0,y0,z0); GL11.glVertex3f(x0,y1,z0); GL11.glVertex3f(x1,y1,z0);
        color(p, brightness, .65f); // back +Z
        GL11.glVertex3f(x0,y0,z1); GL11.glVertex3f(x1,y0,z1); GL11.glVertex3f(x1,y1,z1); GL11.glVertex3f(x0,y1,z1);
        color(p, brightness, .78f);
        GL11.glVertex3f(x1,y0,z1); GL11.glVertex3f(x1,y0,z0); GL11.glVertex3f(x1,y1,z0); GL11.glVertex3f(x1,y1,z1);
        color(p, brightness, .62f);
        GL11.glVertex3f(x0,y0,z0); GL11.glVertex3f(x0,y0,z1); GL11.glVertex3f(x0,y1,z1); GL11.glVertex3f(x0,y1,z0);
        color(p, brightness, 1f);
        GL11.glVertex3f(x0,y1,z1); GL11.glVertex3f(x1,y1,z1); GL11.glVertex3f(x1,y1,z0); GL11.glVertex3f(x0,y1,z0);
        color(p, brightness, .5f);
        GL11.glVertex3f(x0,y0,z0); GL11.glVertex3f(x1,y0,z0); GL11.glVertex3f(x1,y0,z1); GL11.glVertex3f(x0,y0,z1);
        GL11.glEnd();
    }

    private static void color(Part p, float brightness, float shade) {
        float light = p.glow ? 1f : brightness * shade;
        GL11.glColor3f(((p.color >> 16) & 255) / 255f * light, ((p.color >> 8) & 255) / 255f * light, (p.color & 255) / 255f * light);
    }
}
