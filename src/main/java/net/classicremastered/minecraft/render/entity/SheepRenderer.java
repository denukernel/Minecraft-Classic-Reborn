package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.model.SheepFurModel;
import net.classicremastered.minecraft.model.SheepModel;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

public final class SheepRenderer extends MobRenderer<Sheep> {
    private final SheepFurModel fur;

    public SheepRenderer(SheepModel model, SheepFurModel fur) {
        super(model, "/mob/sheep.png");
        this.fur = fur;
    }

    @Override
    public void render(Sheep mob, TextureManager tm, float partial) {
        bindSkin(mob, tm);
        drawPose(mob, tm, partial, mob.renderHeadYaw(partial), mob.renderHeadPitch(partial), true);
    }

    @Override
    protected void renderPose(Sheep mob, TextureManager tm, float partial, float yaw, float pitch) {
        drawPose(mob, tm, partial, yaw, pitch, false);
    }

    private void drawPose(
            Sheep mob, TextureManager tm, float partial, float yaw, float pitch, boolean basePass) {
        SheepModel s = (SheepModel) model;
        float interpGraze = mob.grazeO + (mob.graze - mob.grazeO) * partial;
        float oldY = s.head.y, oldZ = s.head.z;

        AnimInputs a = animInputs(mob, partial);

        // animate
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);

        // Keep the body and wool in the same grazing pose.
        s.head.y += interpGraze * 8.0F;
        s.head.z -= interpGraze;

        // base body
        model.render(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);

        // fur pass
        if (mob.hasFur) {
            if (basePass) bind(tm, "/mob/sheep_fur.png");
            GL11.glDisable(GL11.GL_CULL_FACE);

            // copy pose from s → fur (subset needed for shape match)
            fur.head.yaw = s.head.yaw;
            fur.head.pitch = s.head.pitch;
            fur.head.y = s.head.y;
            fur.head.x = s.head.x;
            fur.head.z = s.head.z;
            fur.body.yaw = s.body.yaw;
            fur.body.pitch = s.body.pitch;
            fur.leg1.pitch = s.leg1.pitch;
            fur.leg2.pitch = s.leg2.pitch;
            fur.leg3.pitch = s.leg3.pitch;
            fur.leg4.pitch = s.leg4.pitch;

            fur.head.render(MODEL_SCALE);
            fur.body.render(MODEL_SCALE);
            fur.leg1.render(MODEL_SCALE);
            fur.leg2.render(MODEL_SCALE);
            fur.leg3.render(MODEL_SCALE);
            fur.leg4.render(MODEL_SCALE);
        }

        // restore head offsets
        s.head.y = oldY;
        s.head.z = oldZ;
    }
}
