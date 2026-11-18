package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Rana;
import net.classicremastered.minecraft.model.MD3Cache;
import net.classicremastered.minecraft.model.MD3Model;
import net.classicremastered.minecraft.model.MD3Renderer;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.render.TextureManager;

public final class RanaRenderer extends MobRenderer<Rana> {
    private final String md3Path; // e.g. "/test2.md3"

    // model is unused for MD3, pass null safely
    public RanaRenderer(String md3Path, String texture) {
        super((Model) null, texture);
        this.md3Path = md3Path;
    }

    @Override
    public void render(Rana mob, TextureManager tm, float partial) {
        drawMesh(tm);
    }

    @Override
    protected float groundOffset(Rana mob) {
        return 1.5f;
    }

    @Override
    protected void renderPose(Rana mob, TextureManager tm, float partial, float yaw, float pitch) {
        drawMesh(tm);
    }

    private void drawMesh(TextureManager tm) {
        MD3Model m = MD3Cache.getModel(md3Path);
        if (m != null) {
            MD3Renderer.render(m, texture, tm, MODEL_SCALE);
        }
    }
}
