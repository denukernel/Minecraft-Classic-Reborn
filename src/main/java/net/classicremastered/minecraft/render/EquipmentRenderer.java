package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;

import org.lwjgl.opengl.GL11;

public final class EquipmentRenderer {
    private static HumanoidModel outer, leggings, ironLeggings;

    private EquipmentRenderer() {}

    private static void copy(ModelPart to, ModelPart from) {
        to.setPosition(from.x, from.y, from.z);
        to.pitch = from.pitch;
        to.yaw = from.yaw;
        to.roll = from.roll;
    }

    public static void armor(Mob mob, HumanoidModel base, TextureManager tm, float scale) {
        armor(mob, base, tm, scale, mob.getRenderBrightness(1));
    }

    public static void armor(
            Mob mob, HumanoidModel base, TextureManager tm, float scale, float brightness) {
        if (Equipment.points(mob) == 0) return;
        if (outer == null) {
            outer = new HumanoidModel(.55f);
            leggings = new HumanoidModel(.25f);
            ironLeggings = new HumanoidModel(.25f);
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            if (mob instanceof BabyZombie || mob instanceof BabySkeleton) {
                BabyZombieModel.applyChildTransform(scale);
            }
            for (int slot = 0; slot < 4; slot++) {
                Equipment.Gear gear = Equipment.gear(mob.equipment[slot]);
                if (gear == null) continue;
                HumanoidModel m = slot == 2 ? (gear.iron ? ironLeggings : leggings) : outer;
                copy(m.head, base.head);
                copy(m.body, base.body);
                copy(m.rightArm, base.rightArm);
                copy(m.leftArm, base.leftArm);
                copy(m.rightLeg, base.rightLeg);
                copy(m.leftLeg, base.leftLeg);
                String texture =
                        gear.iron
                                ? slot == 2
                                        ? "/armor/iron_leggings.png"
                                        : slot == 3 ? "/armor/iron_boots.png" : "/armor/plate.png"
                                : slot == 2 ? "/armor/leather_leggings.png" : "/armor/leather.png";
                m.head.render = slot == 0;
                m.body.render = slot == 1 || slot == 2;
                m.rightArm.render = m.leftArm.render = slot == 1;
                m.rightLeg.render = m.leftLeg.render = slot >= 2;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load(texture));
                if (gear.iron) {
                    GL11.glColor4f(brightness, brightness, brightness, 1);
                } else {
                    GL11.glColor4f(
                            159 / 255f * brightness,
                            105 / 255f * brightness,
                            64 / 255f * brightness,
                            1);
                }
                renderParts(base, m, scale);
                if (!gear.iron) {
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glColor4f(brightness, brightness, brightness, 1);
                    GL11.glBindTexture(
                            GL11.GL_TEXTURE_2D, tm.load(texture.replace(".png", "_overlay.png")));
                    renderParts(base, m, scale);
                    GL11.glDepthFunc(GL11.GL_LESS);
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderParts(HumanoidModel base, HumanoidModel armor, float scale) {
        if (base instanceof BabyZombieModel || base instanceof BabySkeletonModel)
            BabyZombieModel.renderHead(armor.head, scale);
        else armor.head.render(scale);
        armor.body.render(scale);
        armor.rightArm.render(scale);
        armor.leftArm.render(scale);
        armor.rightLeg.render(scale);
        armor.leftLeg.render(scale);
    }

    public static void held(
            Mob mob, HumanoidModel base, TextureManager tm, float scale, float brightness) {
        int stackId = mob instanceof ZombieBuilder ? Block.STONE.id : mob.equipment[4];
        if (stackId <= 0) return;
        GL11.glPushMatrix();
        try {
            if (base instanceof BabyZombieModel || base instanceof BabySkeletonModel) {
                BabyZombieModel.applyChildTransform(scale);
            }
            HandItemRenderer.renderStack(base.rightArm, tm, stackId, scale, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
