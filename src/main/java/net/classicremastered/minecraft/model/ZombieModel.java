// File: src/com/mojang/minecraft/model/ZombieModel.java
package net.classicremastered.minecraft.model;

import net.classicremastered.util.MathHelper;

public class ZombieModel extends HumanoidModel {

    // --- transient pose flags set per-frame from the entity ---
    private boolean builderBreaking = false;
    private boolean builderPlacing  = false;

    /** Toggle for rendering the classic "headwear"/hat layer. */
    protected boolean hasHeadOverlay = true;

    /**
     * Call this once per frame before animating.
     * Works with either public fields (poseBreaking/posePlacing) or getters
     * (isBuilderBreaking/isBuilderPlacing) on the entity.
     */
    public void syncBuilderPose(Object entity) {
        builderBreaking = false;
        builderPlacing  = false;
        if (entity == null) return;

        try {
            Class<?> cls = entity.getClass();

            // Breaking
            try {
                Object v = cls.getMethod("isBuilderBreaking").invoke(entity);
                if (v instanceof Boolean && (Boolean) v) builderBreaking = true;
            } catch (NoSuchMethodException ignored) {
                try {
                    Object v = cls.getField("poseBreaking").get(entity);
                    if (v instanceof Boolean && (Boolean) v) builderBreaking = true;
                } catch (NoSuchFieldException ignored2) {}
            }

            // Placing
            try {
                Object v = cls.getMethod("isBuilderPlacing").invoke(entity);
                if (v instanceof Boolean && (Boolean) v) builderPlacing = true;
            } catch (NoSuchMethodException ignored) {
                try {
                    Object v = cls.getField("posePlacing").get(entity);
                    if (v instanceof Boolean && (Boolean) v) builderPlacing = true;
                } catch (NoSuchFieldException ignored2) {}
            }
        } catch (Throwable ignored) {
            // default to regular zombie pose on reflection failure
        }
    }

    @Override
    public void preAnimate(Object entity) {
        // keep builder pose detection
        syncBuilderPose(entity);

        // Force-disable the hat/overlay for Husks (opaque texture → no alpha in hat slot).
        boolean isHusk = (entity instanceof net.classicremastered.minecraft.mob.Husk);
        this.hasHeadOverlay = !isHusk;

        // Hard toggle the underlying part so ANY render path obeys it.
        if (this.headwear != null) this.headwear.render = this.hasHeadOverlay;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        float a  = MathHelper.sin(this.attackOffset * 3.1415927F);
        float a2 = MathHelper.sin((1.0F - (1.0F - this.attackOffset) * (1.0F - this.attackOffset)) * 3.1415927F);

        // base zombie attack pose
        rightArm.roll = 0.0F;
        leftArm .roll = 0.0F;

        rightArm.yaw = -(0.1F - a * 0.6F);
        leftArm .yaw =  (0.1F - a * 0.6F);

        rightArm.pitch = -1.5707964F - (a * 1.2F - a2 * 0.4F);
        leftArm .pitch = -1.5707964F - (a * 1.2F - a2 * 0.4F);

        rightArm.roll += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm .roll -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        rightArm.pitch+= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        leftArm .pitch-= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;

        // --- builder-specific overrides ---
        if (builderBreaking && !builderPlacing) {
            // “mining” chop: right arm swings down, left stabilizes
            rightArm.pitch = -1.2F + MathHelper.sin(ageInTicks * 0.35F) * 0.6F;
            rightArm.yaw   = -0.2F;
            rightArm.roll  =  0.1F;

            leftArm.pitch  = -0.4F;
            leftArm.yaw    =  0.2F;
            leftArm.roll   = -0.05F;

            // slight torso lean forward
            body.pitch = 0.10F;
        } else if (builderPlacing && !builderBreaking) {
            // “placing” reach: right arm forward & slightly down, left arm counter
            rightArm.pitch = -0.6F;
            rightArm.yaw   =  0.15F;
            rightArm.roll  =  0.05F;

            leftArm.pitch  = -0.2F;
            leftArm.yaw    = -0.15F;
            leftArm.roll   = -0.05F;

            // subtle head focus
            head.pitch += 0.05F;
        }
        // If both flags true (rare), keep base attack pose to avoid jitter.
    }

    /**
     * Render that respects {@link #hasHeadOverlay} by gating {@code headwear}.
     */
    @Override
    public void render(float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // Head + optional hat layer
        if (this.head != null) this.head.render(scale);
        if (hasHeadOverlay && this.headwear != null) this.headwear.render(scale);

        // Body & limbs
        if (this.body     != null) this.body.render(scale);
        if (this.rightArm != null) this.rightArm.render(scale);
        if (this.leftArm  != null) this.leftArm.render(scale);
        if (this.rightLeg != null) this.rightLeg.render(scale);
        if (this.leftLeg  != null) this.leftLeg.render(scale);
    }
}
