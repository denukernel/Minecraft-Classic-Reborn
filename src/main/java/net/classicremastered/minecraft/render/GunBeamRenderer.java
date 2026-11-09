package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

/** Depth-tested muzzle-to-target beam shared by both gun models. */
public final class GunBeamRenderer {
    public static void render(Player owner, Entity mob, float partial, boolean gravity) {
        if (owner == null || mob == null)
            return;
        Vec3D muzzle = GunRenderer.muzzleWorld(owner, partial);
        render(muzzle, mob, partial, gravity);
    }

    public static void render(Vec3D muzzle, Entity mob, float partial, boolean gravity) {
        float px = muzzle.x, py = muzzle.y, pz = muzzle.z;
        float mx = mob.xo + (mob.x - mob.xo) * partial;
        float my = mob.yo + (mob.y - mob.yo) * partial + (mob.bb.y0 + mob.bb.y1) * .5f - mob.y;
        float mz = mob.zo + (mob.z - mob.zo) * partial;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            float phase = ((mob instanceof Mob living ? living.tickCount : mob.level.tickCount) + partial) * .35f;
            for (int pass = 0; pass < 2; pass++) {
                GL11.glLineWidth(pass == 0 ? 4f : 1.5f);
                if (pass == 0)
                    GL11.glColor4f(gravity ? 1f : .1f, gravity ? .48f : .65f, gravity ? .07f : 1f, .5f);
                else
                    GL11.glColor4f(1f, gravity ? .85f : 1f, gravity ? .45f : 1f, .95f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int i = 0; i <= 16; i++) {
                    float t = i / 16f, wave = (float) Math.sin(t * Math.PI) * .06f;
                    GL11.glVertex3f(px + (mx - px) * t + MathHelper.sin(phase + i) * wave,
                            py + (my - py) * t + MathHelper.cos(phase + i) * wave, pz + (mz - pz) * t);
                }
                GL11.glEnd();
            }
            float r = mob.bbWidth * .5f + .03f, h = mob.bbHeight * .5f + .03f;
            GL11.glLineWidth(1.5f);
            GL11.glColor4f(gravity ? 1f : .2f, gravity ? .6f : .85f, gravity ? .15f : 1f, .65f);
            GL11.glBegin(GL11.GL_LINES);
            for (int corner = 0; corner < 8; corner++)
                for (int axis = 0; axis < 3; axis++) {
                    int bit = 1 << axis;
                    if ((corner & bit) != 0)
                        continue;
                    int other = corner | bit;
                    GL11.glVertex3f(mx + ((corner & 1) == 0 ? -r : r), my + ((corner & 2) == 0 ? -h : h),
                            mz + ((corner & 4) == 0 ? -r : r));
                    GL11.glVertex3f(mx + ((other & 1) == 0 ? -r : r), my + ((other & 2) == 0 ? -h : h),
                            mz + ((other & 4) == 0 ? -r : r));
                }
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
