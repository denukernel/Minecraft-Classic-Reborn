package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.TurretVariant;
import net.classicremastered.minecraft.model.CrossbowTurretModel;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

import java.util.EnumMap;
import java.util.Map;

final class CrossbowTurretRenderer extends MobRenderer<CrossbowTurret> {
    private final Map<TurretVariant, CrossbowTurretModel> models =
            new EnumMap<>(TurretVariant.class);

    CrossbowTurretRenderer() {
        super(null, null);
    }

    @Override
    public void render(CrossbowTurret turret, TextureManager textures, float partial) {
        TurretVariant variant = turret.variant;
        var model = models.computeIfAbsent(variant, CrossbowTurretModel::new);
        GL11.glPushMatrix();
        try {
            boolean harpoon = variant == TurretVariant.HARPOON;
            float hurt =
                    turret.hurtDuration > 0
                            ? Math.max(0, (turret.hurtTime - partial) / turret.hurtDuration)
                            : 0;
            float collapse =
                    turret.health <= 0 ? Math.min(1, (turret.deathTime + partial) / 20f) : 0;
            float jolt = (float) Math.sin((1 - hurt) * Math.PI * 3) * hurt;
            GL11.glTranslatef(
                    turret.renderX(partial), turret.renderFeetY(partial), turret.renderZ(partial));
            GL11.glRotatef(9 * jolt + 20 * collapse * collapse, 0, 0, 1);
            GL11.glTranslatef(0, (harpoon ? .45f : 1.5f) - .22f * collapse, 0);
            GL11.glScalef(1, harpoon ? 1 : -1, harpoon ? 1 : -1);
            GL11.glColor4f(
                    1 - .5f * collapse,
                    1 - .6f * hurt - .35f * collapse,
                    1 - .6f * hurt - .35f * collapse,
                    1);
            bind(textures, variant.texture());
            float yaw = turret.renderAimYaw(partial);
            float pitch =
                    turret.renderAimPitch(partial) * (1 - collapse) + 55 * collapse + 12 * jolt;
            model.render(
                    harpoon ? yaw + 180 : -yaw,
                    harpoon ? -pitch : pitch,
                    turret.renderChassisYaw(partial),
                    turret.renderWheelPitch(partial),
                    turret.hasHead());
        } finally {
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    @Override
    public void renderWorld(CrossbowTurret mob, TextureManager tm, float partial) {
        render(mob, tm, partial);
    }
}
