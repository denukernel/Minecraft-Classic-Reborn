package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Fish;
import net.classicremastered.toolkit.modeleditor.EditablePart;
import net.classicremastered.toolkit.modeleditor.ModelIO;

import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class FishModel extends Model {
    private final List<ModelPart> roots = new ArrayList<>();
    private ModelPart tail;
    private ModelPart leftFin;
    private ModelPart rightFin;
    private float leftFinRest;
    private float rightFinRest;
    private float centerHeight;
    private boolean onLand;

    FishModel(String asset) {
        groundOffset = 0;
        int oldWidth = TexturedQuad.ATLAS_W;
        int oldHeight = TexturedQuad.ATLAS_H;
        try (var stream = getClass().getResourceAsStream("/mob/fish/" + asset + ".mmdl")) {
            if (stream == null) throw new IOException("Missing fish model " + asset);
            var model = ModelIO.load(stream);
            TexturedQuad.ATLAS_W = model.atlasW;
            TexturedQuad.ATLAS_H = model.atlasH;
            for (EditablePart part : model.parts) roots.add(build(part));
        } catch (IOException e) {
            throw new IllegalStateException("Could not load fish model " + asset, e);
        } finally {
            TexturedQuad.ATLAS_W = oldWidth;
            TexturedQuad.ATLAS_H = oldHeight;
        }
    }

    private ModelPart build(EditablePart part) {
        ModelPart mesh = new ModelPart(part.u, part.v);
        mesh.mirror = part.mirror;
        mesh.setBounds(part.x, part.y, part.z, part.w, part.h, part.d, part.inflate);
        // The mob renderer draws both sides. Keep one face for each flat fin or spine.
        if (part.inflate == 0) {
            if (part.w == 0) mesh.quads = new TexturedQuad[] {mesh.quads[1]};
            else if (part.h == 0) mesh.quads = new TexturedQuad[] {mesh.quads[2]};
            else if (part.d == 0) mesh.quads = new TexturedQuad[] {mesh.quads[4]};
        }
        mesh.setPosition(part.posX, part.posY, part.posZ);
        mesh.pitch = part.pitch;
        mesh.yaw = part.yaw;
        mesh.roll = part.roll;
        switch (part.name) {
            case "tail" -> tail = mesh;
            case "left_fin" -> {
                leftFin = mesh;
                leftFinRest = part.roll;
            }
            case "right_fin" -> {
                rightFin = mesh;
                rightFinRest = part.roll;
            }
        }
        for (EditablePart child : part.children) mesh.addChild(build(child));
        return mesh;
    }

    @Override
    public void preAnimate(Object entity) {
        Fish fish = (Fish) entity;
        centerHeight = fish.bbHeight * .5f;
        onLand = !fish.isInWater();
    }

    @Override
    public void render(float swing, float amount, float age, float yaw, float pitch, float scale) {
        float flap = (float) Math.sin(age * .45f) * .35f;
        if (tail != null) tail.yaw = (float) Math.sin(age * .6f) * (onLand ? .75f : .42f);
        if (leftFin != null) leftFin.roll = leftFinRest + flap;
        if (rightFin != null) rightFin.roll = rightFinRest - flap;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, -centerHeight, 0);
            GL11.glRotatef(onLand ? 0 : -pitch, 1, 0, 0);
            if (onLand) GL11.glRotatef(75 + flap * 18, 0, 0, 1);
            for (ModelPart root : roots) root.render(scale);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
