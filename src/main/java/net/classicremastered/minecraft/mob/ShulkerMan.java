package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.model.ModelShulkerMan;

public final class ShulkerMan extends Mob {

    private final ModelShulkerMan model = new ModelShulkerMan();

    public ShulkerMan(Level level, float x, float y, float z) {
        super(level);
        setPos(x, y, z);

        textureName = "/mob/stuckerman.png";
        modelName = "shulkerman";
        health = 20;
        coinDrop = 2;
        heightOffset = 1.7F;

        ai = new BasicAttackAI();
        ((BasicAttackAI) ai).bind(level, this);
    }

    @Override
    public void tick() {
        super.tick();

        // Lid animation
        if (ai instanceof BasicAttackAI basic && basic.attackTarget != null) {
            float dx = basic.attackTarget.x - x;
            float dz = basic.attackTarget.z - z;
            float distSq = dx * dx + dz * dz;
            if (distSq < 25.0F) {
                model.lid.pitch = (float) Math.toRadians(-25F);
            } else {
                model.lid.pitch *= 0.9F;
            }
        } else {
            model.lid.pitch = (float) Math.sin(tickCount * 0.05F) * 0.15F;
        }
    }

    @Override
    public void die(net.classicremastered.minecraft.Entity cause) {
        if (dead || removed)
            return;
        super.die(cause);
        if (level != null
                && (level.minecraft == null
                        || level.minecraft.settings == null
                        || level.minecraft.settings.allowMobSounds)) {
            level.playSound("random/explode", this, 1.0F, 1.0F);
        }
    }
}
