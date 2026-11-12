package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigurePowers;

import org.lwjgl.opengl.GL11;

public final class RageAuraRenderer {
    private RageAuraRenderer() {}

    public static void render(StickFigure mob, float partial) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    mob.xo + (mob.x - mob.xo) * partial,
                    mob.yo + (mob.y - mob.yo) * partial - mob.heightOffset,
                    mob.zo + (mob.z - mob.zo) * partial);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            float age = mob.tickCount + partial;
            GL11.glColor4f(1, .025f, .01f, .55f);
            GL11.glLineWidth(2);
            for (int ring = 0; ring < 3; ring++) {
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int i = 0; i < 40; i++) {
                    double angle = i * Math.PI / 20 + age * .05;
                    float radius = .55f + .08f * (float) Math.sin(age * .15f + ring);
                    GL11.glVertex3f(
                            (float) Math.cos(angle) * radius,
                            .05f + ring * .8f,
                            (float) Math.sin(angle) * radius);
                }
                GL11.glEnd();
            }
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6 + age * .015;
                float x = (float) Math.cos(a) * .55f, z = (float) Math.sin(a) * .55f;
                GL11.glColor4f(1, .02f, 0, .16f);
                GL11.glVertex3f(x - .09f, 0, z);
                GL11.glVertex3f(x + .09f, 0, z);
                GL11.glColor4f(1, .08f, 0, .6f);
                GL11.glVertex3f(x, 1.1f + .6f * (float) Math.sin(age * .15 + i), z);
            }
            GL11.glEnd();
            if (mob.ai instanceof StickFigureAI ai
                    && ai.powers.phase == StickFigurePowers.Phase.RAGE_TRANSITION) {
                float progress =
                        Math.min(
                                1,
                                (ai.powers.phaseTicks + partial)
                                        / StickFigurePowers.RAGE_TRANSITION_TICKS);
                float radius = .6f + 3 * progress;
                GL11.glColor4f(1, .32f, .04f, .85f * (1 - progress));
                GL11.glLineWidth(3);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int i = 0; i < 48; i++) {
                    double angle = i * Math.PI / 24;
                    GL11.glVertex3f(
                            (float) Math.cos(angle) * radius,
                            .12f,
                            (float) Math.sin(angle) * radius);
                }
                GL11.glEnd();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
