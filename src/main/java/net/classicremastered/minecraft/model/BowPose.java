package net.classicremastered.minecraft.model;

/** Shared right-handed bow stance for human and skeleton arm dimensions. */
public final class BowPose {
    public static void apply(HumanoidModel model, float yaw, float pitch, boolean aiming) {
        if (aiming) {
            model.rightArm.roll = model.leftArm.roll = 0;
            model.head.yaw = yaw;
            model.head.pitch = pitch;
            model.rightArm.pitch = model.leftArm.pitch = -(float) Math.PI / 2 + pitch;
            model.rightArm.yaw = yaw - .1f;
            model.leftArm.yaw = yaw + .5f;
        } else {
            model.rightArm.pitch = model.rightArm.pitch * .5f - (float) Math.PI / 10;
            model.rightArm.yaw = 0;
        }
    }
}
