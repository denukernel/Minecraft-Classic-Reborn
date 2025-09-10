// com/mojang/minecraft/model/Model.java
package net.classicremastered.minecraft.model;

public abstract class Model {
    public float attackOffset = 0.0F;
    public float groundOffset = 23.0F;
    
    // NEW: renderer calls this once per frame so the model can read entity-specific pose flags
    public void preAnimate(Object entity) { /* no-op by default */ }

    public void setRotationAngles(float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
        // no-op; overridden by concrete models
    }

    public void render(float v1, float v2, float v3, float v4, float v5, float scale) {}
}
