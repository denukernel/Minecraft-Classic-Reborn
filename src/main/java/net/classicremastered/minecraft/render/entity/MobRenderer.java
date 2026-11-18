package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.level.itemstack.BowItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.mob.BabySkeleton;
import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.HumanoidMob;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.model.BabyZombieModel;
import net.classicremastered.minecraft.model.ChickenModel;
import net.classicremastered.minecraft.model.GunModel;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.model.SkeletonModel;
import net.classicremastered.minecraft.model.SkeletonTrapModel;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.HeldItemRenderer;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

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
        float anim = mob.animStepO + (mob.animStep - mob.animStepO) * partial;
        float runAmt = mob.oRun + (mob.run - mob.oRun) * partial;
        float yaw = mob.renderHeadYaw(partial);
        float pitch = mob.renderHeadPitch(partial);
        float age = (float) mob.tickCount + partial;

        // classic swing offset for models that use it
        if (model != null) {
            float off = ((float) mob.attackTime - partial) / (float) Mob.ATTACK_DURATION;
            if (off < 0f) off = 0f;
            else if (off > 1f) off = 1f;
            model.attackOffset = off;
        }
        return new AnimInputs(anim, runAmt, age, yaw, pitch);
    }

    protected final void bind(TextureManager tm, String path) {
        int id = tm.load(path);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public abstract void render(T mob, TextureManager tm, float partial);

    public void renderWorld(T mob, TextureManager tm, float partial) {
        MobRenderPipeline.render(mob, this, tm, partial);
    }

    public Model model(T mob) {
        return model;
    }

    protected float groundOffset(T mob) {
        Model body = model(mob);
        return body == null ? 23f : body.groundOffset;
    }

    protected boolean usesSharedDamageOverlay() {
        return true;
    }

    protected void renderBase(
            T mob, TextureManager tm, float partial, float yaw, float pitch, float brightness) {
        render(mob, tm, partial);
    }

    protected void bindSkin(T mob, TextureManager tm) {
        if (texture == null) mob.bindTexture(tm);
        else bind(tm, texture);
    }

    /** Draw the same geometry using the texture and blend state selected by the caller. */
    protected void renderPose(T mob, TextureManager tm, float partial, float yaw, float pitch) {
        Model body = model(mob);
        if (body == null) return;
        AnimInputs a = animInputs(mob, partial);
        if (body instanceof SkeletonModel skeleton) {
            skeleton.setRenderTextures(tm);
            skeleton.setRenderPartial(partial);
        }
        if (body instanceof SkeletonTrapModel trap) trap.setRenderContext(tm, partial);
        if (body instanceof ChickenModel chicken && mob instanceof Chicken)
            chicken.syncFromEntity((Chicken) mob);
        body.preAnimate(mob);
        if (body instanceof HumanoidModel humanoid) {
            humanoid.setSneakProgress(
                    mob instanceof Player player ? player.getSneakProgress(partial) : 0);
        }
        body.setRotationAngles(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);
        if (body instanceof HumanoidModel humanoid && mob instanceof Player player) {
            humanoid.setAttachmentTextures(tm);
            try {
                renderPlayerPose(player, humanoid, a, yaw, pitch, partial);
            } finally {
                humanoid.setAttachmentTextures(null);
            }
        } else if (body instanceof HumanoidModel humanoid && mob instanceof Defender defender) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            try {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                defender.renderHandAction(humanoid, yaw, pitch, MODEL_SCALE, partial);
            } finally {
                GL11.glPopAttrib();
            }
        } else {
            body.render(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);
        }
        if (mob instanceof HumanoidMob
                && mob.hasHair
                && body instanceof HumanoidModel humanoid
                && !(body instanceof BabyZombieModel)
                && !(mob instanceof BabySkeleton)) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            try {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                humanoid.headwear.yaw = humanoid.head.yaw;
                humanoid.headwear.pitch = humanoid.head.pitch;
                humanoid.headwear.render(MODEL_SCALE);
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    protected final void renderLegacyArmor(HumanoidMob mob, TextureManager tm) {
        Model body = model;
        float scale = MODEL_SCALE;
        if (mob.equipmentRolled && !mob.legacyArmor) return;
        if (!(mob.helmet || mob.armor)) return;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load("/armor/plate.png"));
        GL11.glDisable(GL11.GL_CULL_FACE);

        boolean isBaby = mob.isBaby() && mob instanceof Zombie;
        HumanoidModel armorModel =
                (HumanoidModel)
                        Mob.modelCache.getModel(
                                mob instanceof BabySkeleton
                                        ? "skeleton_baby.armor"
                                        : isBaby ? "humanoid.armor.baby" : "humanoid.armor");

        // Visibility flags
        armorModel.head.render = mob.helmet;
        armorModel.body.render = mob.armor;
        armorModel.rightArm.render = mob.armor;
        armorModel.leftArm.render = mob.armor;
        armorModel.rightLeg.render = false;
        armorModel.leftLeg.render = false;

        if (body instanceof HumanoidModel base) {
            armorModel.head.yaw = base.head.yaw;
            armorModel.head.pitch = base.head.pitch;
            armorModel.head.setPosition(base.head.x, base.head.y, base.head.z);
            armorModel.rightArm.pitch = base.rightArm.pitch;
            armorModel.rightArm.roll = base.rightArm.roll;
            armorModel.leftArm.pitch = base.leftArm.pitch;
            armorModel.leftArm.roll = base.leftArm.roll;
            armorModel.rightLeg.pitch = base.rightLeg.pitch;
            armorModel.leftLeg.pitch = base.leftLeg.pitch;
        }

        GL11.glPushMatrix();
        if (isBaby) {
            BabyZombieModel.applyChildTransform(scale);
            BabyZombieModel.renderHead(armorModel.head, scale);
        } else {
            armorModel.head.render(scale);
        }
        armorModel.body.render(scale);
        armorModel.rightArm.render(scale);
        armorModel.leftArm.render(scale);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private static void renderPlayerPose(
            Player player,
            HumanoidModel model,
            AnimInputs a,
            float yaw,
            float pitch,
            float partial) {
        int selected = player.inventory.getSelected();
        float swing = player.handSwingProgress(partial);
        if (GunModel.isGun(selected - 256)) {
            model.renderHoldingGun(a.anim, a.runAmt, a.age, yaw, pitch, MODEL_SCALE);
        } else if (selected == 262) {
            model.renderHoldingBow(
                    yaw, pitch, MODEL_SCALE, ((BowItem) Item.BOW).isDrawing(player), swing);
        } else {
            model.renderHandAction(
                    MODEL_SCALE,
                    selected > 0,
                    swing,
                    player.developerPowers().swordPose(player, selected, partial),
                    HeldItemRenderer.eatingTime(player, partial));
        }
    }

    protected static final class AnimInputs {
        public final float anim, runAmt, age, yaw, pitch;

        public AnimInputs(float anim, float runAmt, float age, float yaw, float pitch) {
            this.anim = anim;
            this.runAmt = runAmt;
            this.age = age;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
