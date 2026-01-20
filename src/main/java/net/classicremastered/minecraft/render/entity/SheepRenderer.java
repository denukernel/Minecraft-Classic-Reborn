package net.classicremastered.minecraft.render.entity;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.model.SheepModel;
import net.classicremastered.minecraft.model.SheepFurModel;
import net.classicremastered.minecraft.render.TextureManager;

public final class SheepRenderer extends MobRenderer<Sheep> {
    private final SheepFurModel fur;

    public SheepRenderer(SheepModel model, SheepFurModel fur) {
        super(model, "/mob/sheep.png");
        this.fur = fur;
    }

    @Override
    public void render(Sheep mob, TextureManager tm, float partial) {
        SheepModel s = (SheepModel) model;
        float interpGraze = mob.grazeO + (mob.graze - mob.grazeO) * partial;
        float oldY = s.head.y, oldZ = s.head.z;

        AnimInputs a = animInputs(mob, partial);
        bind(tm, texture);

        // animate
        model.preAnimate(mob);
        model.setRotationAngles(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // grazing offsets (like your Sheep.renderModel)
        s.head.y += interpGraze * 8.0F;
        s.head.z -= interpGraze;

        // base body
        model.render(a.anim, a.runAmt, a.age, a.yaw, a.pitch, MODEL_SCALE);

        // restore head offsets
        s.head.y = oldY; s.head.z = oldZ;

        // fur pass
        if (mob.hasFur) {
            bind(tm, "/mob/sheep_fur.png");
            GL11.glDisable(GL11.GL_CULL_FACE); // match old behavior

            // copy pose from s → fur (subset needed for shape match)
            fur.head.yaw  = s.head.yaw;  fur.head.pitch  = s.head.pitch;
            fur.head.y    = s.head.y;    fur.head.x      = s.head.x;
            fur.body.yaw  = s.body.yaw;  fur.body.pitch  = s.body.pitch;
            fur.leg1.pitch= s.leg1.pitch; fur.leg2.pitch = s.leg2.pitch;
            fur.leg3.pitch= s.leg3.pitch; fur.leg4.pitch = s.leg4.pitch;

            fur.head.render(MODEL_SCALE);
            fur.body.render(MODEL_SCALE);
            fur.leg1.render(MODEL_SCALE);
            fur.leg2.render(MODEL_SCALE);
            fur.leg3.render(MODEL_SCALE);
            fur.leg4.render(MODEL_SCALE);
        }
    }
}
