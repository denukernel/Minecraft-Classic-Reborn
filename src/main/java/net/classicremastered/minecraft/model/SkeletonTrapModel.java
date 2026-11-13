package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.SkeletonTrap;
import net.classicremastered.minecraft.render.HandItemRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.toolkit.modeleditor.ModelIO;

import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkeletonTrapModel extends Model {
    private static final SkeletonTrapBone[] BONES = SkeletonTrapBone.values();
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();
    private SkeletonTrap trap;
    private float partial;
    private TextureManager textures;

    public SkeletonTrapModel() {
        groundOffset = SkeletonTrapBone.GROUND_OFFSET;
        int oldWidth = TexturedQuad.ATLAS_W;
        int oldHeight = TexturedQuad.ATLAS_H;
        try (var stream = getClass().getResourceAsStream("/mob/skeleton_trap.mmdl")) {
            if (stream == null) throw new IOException("Missing skeleton trap model");
            var model = ModelIO.load(stream);
            TexturedQuad.ATLAS_W = model.atlasW;
            TexturedQuad.ATLAS_H = model.atlasH;
            for (var part : model.parts) {
                ModelPart mesh = new ModelPart(part.u, part.v);
                mesh.mirror = part.mirror;
                mesh.setBounds(part.x, part.y, part.z, part.w, part.h, part.d, part.inflate);
                mesh.setPosition(part.posX, part.posY, part.posZ);
                parts.put(part.name, mesh);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load skeleton trap model", ex);
        } finally {
            TexturedQuad.ATLAS_W = oldWidth;
            TexturedQuad.ATLAS_H = oldHeight;
        }
    }

    @Override
    public void preAnimate(Object entity) {
        trap = (SkeletonTrap) entity;
    }

    public void setRenderContext(TextureManager textures, float partial) {
        this.textures = textures;
        this.partial = partial;
    }

    @Override
    public void render(float swing, float amount, float age, float yaw, float pitch, float scale) {
        float fall = trap.collapseProgress(partial);
        float stride = (float) Math.cos(swing * .6662f) * 1.4f * amount * (1 - fall);
        ModelPart head = parts.get("head");
        head.yaw = (float) Math.toRadians(yaw) * (1 - fall);
        head.pitch = (float) Math.toRadians(pitch) * (1 - fall);
        parts.get("right_arm").pitch = -stride - (trap.isDrawingBow() ? 1.4f : 0f);
        parts.get("left_arm").pitch = stride - (trap.isDrawingBow() ? 1.4f : 0f);
        parts.get("right_leg").pitch = stride;
        parts.get("left_leg").pitch = -stride;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    0,
                    SkeletonTrapBone.FALL_OFFSET_Y * scale * fall,
                    SkeletonTrapBone.FALL_OFFSET_Z * scale * fall);
            GL11.glScalef(1, 1, 1 + (SkeletonTrapBone.FALL_DEPTH - 1) * fall);
            GL11.glRotatef(90f * fall, 1, 0, 0);
            for (SkeletonTrapBone bone : BONES) {
                if (trap.isBoneBroken(bone)) continue;
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(
                            bone.scatterX * scale * fall, bone.scatterY * scale * fall, 0);
                    parts.get(bone.partName).render(scale);
                } finally {
                    GL11.glPopMatrix();
                }
            }
            if (fall == 0f) {
                HandItemRenderer.renderBow(
                        parts.get("right_arm"),
                        textures,
                        trap.getBowTexture(),
                        scale,
                        trap.getRenderBrightness(partial),
                        1f);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }
}
