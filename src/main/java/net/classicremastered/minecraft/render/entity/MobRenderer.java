package net.classicremastered.minecraft.render.entity;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public abstract class MobRenderer<T extends Mob> {
    protected final Model model;
    protected final String texture;
    protected static final float MODEL_SCALE = 0.0625F;

    protected MobRenderer(Model model, String texture) {
        this.model = model;
        this.texture = texture;
    }

    // Common animation inputs computed the exact same way Mob.render() does
    protected final AnimInputs animInputs(T mob, float partial) {
        float anim   = mob.animStepO + (mob.animStep - mob.animStepO) * partial;
        float runAmt = mob.oRun      + (mob.run      - mob.oRun)      * partial;
        float body   = mob.yBodyRotO + (mob.yBodyRot - mob.yBodyRotO) * partial;
        float yaw    = mob.yRotO     + (mob.yRot     - mob.yRotO)     * partial - body;
        float pitch  = mob.xRotO     + (mob.xRot     - mob.xRotO)     * partial;
        float age    = (float) mob.tickCount + partial;

        // classic swing offset for models that use it
        if (model != null) {
            float off = ((float) mob.attackTime - partial) / (float) Mob.ATTACK_DURATION;
            if (off < 0f) off = 0f; else if (off > 1f) off = 1f;
            model.attackOffset = off;
        }
        return new AnimInputs(anim, runAmt, age, yaw, pitch);
    }

    protected final void bind(TextureManager tm, String path) {
        int id = tm.load(path);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public abstract void render(T mob, TextureManager tm, float partial);

    protected static final class AnimInputs {
        public final float anim, runAmt, age, yaw, pitch;
        public AnimInputs(float anim, float runAmt, float age, float yaw, float pitch) {
            this.anim = anim; this.runAmt = runAmt; this.age = age; this.yaw = yaw; this.pitch = pitch;
        }
    }
}
