package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;

import org.lwjgl.opengl.GL11;

public final class EquipmentRenderer {
    private static HumanoidModel outer, leggings;
    private static HumanoidModel compactOuter, compactLeggings, frostbiteLeggings;

    private EquipmentRenderer() {}

    private static boolean isCompactBaby(HumanoidModel model) {
        return model instanceof FrostbiteModel cold && cold.isBabyModel();
    }

    private static boolean hasScaledHead(HumanoidModel model) {
        return model instanceof BabyZombieModel || model instanceof BabySkeletonModel;
    }

    private static boolean isChildModel(HumanoidModel model) {
        return hasScaledHead(model) || isCompactBaby(model);
    }

    private static HumanoidMob legacyArmor(Mob mob, HumanoidModel model) {
        if (!(model instanceof BabyZombieModel
                        || model instanceof HuskModel
                        || model instanceof FrostbiteModel
                        || mob instanceof TeenZombie
                        || mob instanceof TeenSkeleton)
                || !(mob instanceof HumanoidMob humanoid)
                || (mob.equipmentRolled && !mob.legacyArmor)
                || !(humanoid.helmet || humanoid.armor)) {
            return null;
        }
        return humanoid;
    }

    public static void armor(Mob mob, HumanoidModel base, TextureManager tm, float scale) {
        armor(mob, base, tm, scale, mob.getRenderBrightness(1));
    }

    public static void armor(
            Mob mob, HumanoidModel base, TextureManager tm, float scale, float brightness) {
        HumanoidMob legacy = legacyArmor(mob, base);
        if (Equipment.points(mob) == 0 && legacy == null) return;
        if (outer == null) {
            outer = new HumanoidModel(.55f);
            leggings = new HumanoidModel(.25f);
        }
        boolean compactBaby = isCompactBaby(base);
        if (compactBaby && compactOuter == null) {
            compactOuter = BabyHumanoidArmorModel.compactArmor(.55F);
            compactLeggings = BabyHumanoidArmorModel.compactArmor(.25F);
        }
        if (base instanceof FrostbiteModel && !compactBaby && frostbiteLeggings == null) {
            // The adult frostbite has an ice layer inflated by .25 model units.
            frostbiteLeggings = new HumanoidModel(.35F);
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
            if (compactBaby) {
                scale *= 2.0F;
            }
            for (int slot = 0; slot < 4; slot++) {
                Equipment.Gear gear = mob.equipment == null ? null : Equipment.gear(mob.equipment[slot]);
                boolean equipped = gear != null && gear.slot == slot;
                if (!equipped
                        && (legacy == null
                                || !(slot == 0 && legacy.helmet || slot == 1 && legacy.armor))) {
                    continue;
                }
                HumanoidModel m = outer;
                if (compactBaby) {
                    m = slot == 2 ? compactLeggings : compactOuter;
                } else if (slot == 2) {
                    m = base instanceof FrostbiteModel ? frostbiteLeggings : leggings;
                }
                m.head.copyFrom(base.head);
                m.body.copyFrom(base.body);
                m.rightArm.copyFrom(base.rightArm);
                m.leftArm.copyFrom(base.leftArm);
                m.rightLeg.copyFrom(base.rightLeg);
                m.leftLeg.copyFrom(base.leftLeg);
                String material = equipped ? gear.armorMaterial : "iron";
                boolean leather = material.equals("leather");
                String texture =
                        switch (material) {
                            case "leather" ->
                                    slot == 2
                                            ? "/armor/leather_leggings.png"
                                            : "/armor/leather.png";
                            case "iron" ->
                                    slot == 2
                                            ? "/armor/iron_leggings.png"
                                            : slot == 3
                                                    ? "/armor/iron_boots.png"
                                                    : "/armor/plate.png";
                            default ->
                                    "/armor/" + material + (slot == 2 ? "_leggings.png" : ".png");
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
        if (hasScaledHead(base)) BabyZombieModel.renderHead(armor.head, scale);
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
