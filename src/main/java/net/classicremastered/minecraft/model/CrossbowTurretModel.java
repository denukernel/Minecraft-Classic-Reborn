package net.classicremastered.minecraft.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.classicremastered.minecraft.mob.TurretVariant;
import net.classicremastered.toolkit.modeleditor.EditablePart;
import net.classicremastered.toolkit.modeleditor.ModelIO;

/** Runtime turret meshes built from the same cuboids as their editable models. */
public final class CrossbowTurretModel {
    private final List<ModelPart> roots = new ArrayList<>();
    private final List<ModelPart> wheels = new ArrayList<>();
    private ModelPart head;
    private ModelPart chassis;

    public CrossbowTurretModel() {
        this(TurretVariant.CROSSBOW);
    }

    public CrossbowTurretModel(TurretVariant variant) {
        int oldWidth = TexturedQuad.ATLAS_W;
        int oldHeight = TexturedQuad.ATLAS_H;
        String path = "/turret/" + variant.asset + ".mmdl";
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null)
                throw new IOException("Missing turret model " + path);
            var model = ModelIO.load(stream);
            TexturedQuad.ATLAS_W = model.atlasW;
            TexturedQuad.ATLAS_H = model.atlasH;
            for (EditablePart editable : model.parts)
                roots.add(part(editable));
            if (head == null)
                throw new IOException("Missing turret head in " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load turret model " + path, exception);
        } finally {
            TexturedQuad.ATLAS_W = oldWidth;
            TexturedQuad.ATLAS_H = oldHeight;
        }
    }

    private ModelPart part(EditablePart editable) {
        ModelPart part = new ModelPart(editable.u, editable.v);
        part.mirror = editable.mirror;
        part.setBounds(editable.x, editable.y, editable.z, editable.w, editable.h, editable.d,
                editable.inflate);
        part.setPosition(editable.posX, editable.posY, editable.posZ);
        part.pitch = editable.pitch;
        part.yaw = editable.yaw;
        part.roll = editable.roll;
        if (editable.name.equals("head"))
            head = part;
        if (editable.name.equals("chassis"))
            chassis = part;
        if (editable.name.startsWith("wheel_"))
            wheels.add(part);
        for (EditablePart child : editable.children)
            part.addChild(part(child));
        return part;
    }

    public void render(float yaw, float pitch) {
        render(yaw, pitch, 0f, 0f);
    }

    public void render(float yaw, float pitch, float chassisYaw, float wheelPitch) {
        render(yaw, pitch, chassisYaw, wheelPitch, true);
    }

    public void render(float yaw, float pitch, float chassisYaw, float wheelPitch, boolean hasHead) {
        if (chassis != null)
            chassis.yaw = (float) Math.toRadians(chassisYaw);
        for (ModelPart wheel : wheels)
            wheel.pitch = wheelPitch;
        head.yaw = (float) Math.toRadians(-yaw + 180 - chassisYaw);
        head.pitch = (float) Math.toRadians(pitch);
        head.render = hasHead;
        for (ModelPart root : roots)
            root.render(1f / 16f);
    }
}
