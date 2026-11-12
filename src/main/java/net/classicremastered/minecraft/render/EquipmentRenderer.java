package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;

import org.lwjgl.opengl.GL11;

public final class EquipmentRenderer {
    private static HumanoidModel outer, leggings;

    private EquipmentRenderer() {}

    private static void copy(ModelPart to, ModelPart from) {
        to.setPosition(from.x, from.y, from.z);
        to.pitch = from.pitch;
        to.yaw = from.yaw;
        to.roll = from.roll;
    }

    private static boolean isChildModel(HumanoidModel model) {
        return model instanceof BabyZombieModel || model instanceof BabySkeletonModel
                || model instanceof FrostbiteModel cold && cold.isBabyModel();
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
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            if (isChildModel(base)) {
                BabyZombieModel.applyChildTransform(scale);
            }
            for (int slot = 0; slot < 4; slot++) {
                Equipment.Gear gear = Equipment.gear(mob.equipment[slot]);
                if (gear == null || gear.slot != slot)
                    continue;
                HumanoidModel m = slot == 2 ? leggings : outer;
                copy(m.head, base.head);
                copy(m.body, base.body);
                copy(m.rightArm, base.rightArm);
                copy(m.leftArm, base.leftArm);
                copy(m.rightLeg, base.rightLeg);
                copy(m.leftLeg, base.leftLeg);
                boolean leather = gear.armorMaterial.equals("leather");
                String texture = switch (gear.armorMaterial) {
                case "leather" -> slot == 2 ? "/armor/leather_leggings.png" : "/armor/leather.png";
                case "iron" -> slot == 2 ? "/armor/iron_leggings.png"
                        : slot == 3 ? "/armor/iron_boots.png" : "/armor/plate.png";
                default -> "/armor/" + gear.armorMaterial + (slot == 2 ? "_leggings.png" : ".png");
                };
                m.head.render = slot == 0;
                m.body.render = slot == 1 || slot == 2;
                m.rightArm.render = m.leftArm.render = slot == 1;
                m.rightLeg.render = m.leftLeg.render = slot >= 2;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load(texture));
                if (!leather) {
                    GL11.glColor4f(brightness, brightness, brightness, 1);
                } else {
                    GL11.glColor4f(
                            159 / 255f * brightness,
                            105 / 255f * brightness,
                            64 / 255f * brightness,
                            1);
                }
                renderParts(base, m, scale);
                if (leather) {
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
        if (isChildModel(base))
            BabyZombieModel.renderHead(armor.head, scale);
        else armor.head.render(scale);
        armor.body.render(scale);
        armor.rightArm.render(scale);
        armor.leftArm.render(scale);
        if (base instanceof net.classicremastered.minecraft.model.SkeletonModel skeleton) {
            skeleton.renderSeatedLegs(armor.rightLeg, armor.leftLeg, scale);
        } else {
            armor.rightLeg.render(scale);
            armor.leftLeg.render(scale);
        }
    }

    public static void held(
            Mob mob, HumanoidModel base, TextureManager tm, float scale, float brightness) {
        int stackId = mob instanceof ZombieBuilder builder
                ? builder.hasStolenHead() ? TurretItems.HEAD + 256 : Block.STONE.id
                : mob.equipment[4];
        if (stackId <= 0) return;
        GL11.glPushMatrix();
        try {
            if (isChildModel(base)) {
                BabyZombieModel.applyChildTransform(scale);
            }
            HandItemRenderer.renderStack(base.rightArm, tm, stackId, scale, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
