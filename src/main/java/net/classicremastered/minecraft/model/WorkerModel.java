package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.mob.Worker;

/** Classic Steve geometry with a work swing. */
public final class WorkerModel extends HumanoidModel {
    private Worker worker;

    @Override
    public void preAnimate(Object entity) {
        super.preAnimate(entity);
        worker = entity instanceof Worker value ? value : null;
    }

    @Override
    public void setRotationAngles(float stride, float amount, float age, float yaw, float pitch,
            float scale) {
        super.setRotationAngles(stride, amount, age, yaw, pitch, scale);
        float swing = worker == null ? 0 : worker.handSwingProgress(age - (int) age);
        rightArm.pitch -= 1.45f * (float) Math.sin(swing * Math.PI);
        rightArm.yaw += .25f * (float) Math.sin(swing * Math.PI);
    }
}
