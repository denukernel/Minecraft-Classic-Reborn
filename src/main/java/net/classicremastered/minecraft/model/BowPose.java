package net.classicremastered.minecraft.model;

/** Shared right-handed bow stance for human and skeleton arm dimensions. */
public final class BowPose {
    public static void apply(HumanoidModel model,float yaw,float pitch,float draw,boolean aiming) {
        model.rightArm.roll=model.leftArm.roll=0;
        model.rightArm.yaw=model.leftArm.yaw=0;
        if(aiming) {
            model.head.yaw=yaw;model.head.pitch=pitch;
            model.rightArm.pitch=-(float)Math.PI/2+pitch;
            model.rightArm.yaw=yaw-.10f;
            model.leftArm.pitch=-(float)Math.PI/2+pitch;
            model.leftArm.yaw=yaw+.65f-.25f*draw;
        } else {
            model.rightArm.pitch=model.rightArm.pitch*.25f-.12f;
        }
    }
}
