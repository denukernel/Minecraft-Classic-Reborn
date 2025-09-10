package net.classicremastered.minecraft.model;

/** Pose & render Classic models using the mob's own fields. */
public final class ModelPose {
    private ModelPose() {}

    /** Compute the 6 pose params from the entity's classic fields and render. */
    public static void poseAndRender(Model model, Object e, float partial, float scale) {
        // Defaults if field missing
        float walkDist = 0f, walkDistO = 0f;
        float yRot = 0f, yRotO = 0f, xRot = 0f, xRotO = 0f;
        int tickCount = 0, attackTime = 0, maxAttackTime = 10;

        walkDist     = f(e, "animStep",   walkDist);    // your code uses animStep for limb phase
        walkDistO    = f(e, "animStepO",  walkDistO);
        yRot         = f(e, "yRot",       yRot);
        yRotO        = f(e, "yRotO",      yRotO);
        xRot         = f(e, "xRot",       xRot);
        xRotO        = f(e, "xRotO",      xRotO);
        tickCount    = i(e, "tickCount",  tickCount);
        attackTime   = i(e, "attackTime", attackTime);
        maxAttackTime= i(e, "ATTACK_DURATION", maxAttackTime); // const = 5 in your Mob

        // Standard params:
        float limbSwing       = walkDist;
        float limbSwingAmount = Math.abs((walkDist - walkDistO) * 4.0F);
        if (limbSwingAmount > 1f) limbSwingAmount = 1f;

        float netHeadYaw = yRotO + (yRot - yRotO) * partial; // degrees
        float headPitch  = xRotO + (xRot - xRotO) * partial; // degrees
        float ageInTicks = tickCount + partial;

        // Attack swing for ZombieModel
        if (attackTime > 0) {
            model.attackOffset = 1.0F - ((attackTime - partial) / Math.max(1, maxAttackTime));
            if (model.attackOffset < 0f) model.attackOffset = 0f;
            if (model.attackOffset > 1f) model.attackOffset = 1f;
        } else {
            model.attackOffset = 0f;
        }

        // Pose & render (your models already have 6-param render)
        model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        model.render(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    }

    // --- tiny reflection helpers (robust to field visibility across forks) ---
    private static float f(Object o, String name, float def) {
        try { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.getFloat(o); }
        catch (Throwable t) { return def; }
    }
    private static int i(Object o, String name, int def) {
        try { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(o); }
        catch (Throwable t) { return def; }
    }
}
