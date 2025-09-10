package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/** Humanoid clone using /char.png; light melee, expires quickly. */
public final class FakePlayerClone extends Mob {
    private int life = 200; // ~10s

    public FakePlayerClone(Level l, float x, float y, float z) {
        super(l);
        this.modelName = "humanoid";
        this.textureName = "/char.png";
        this.heightOffset = 1.62F;
        this.setPos(x, y, z);
        this.setSize(0.6F, 1.8F);

        try {
            net.classicremastered.minecraft.mob.ai.BasicAttackAI ai = new net.classicremastered.minecraft.mob.ai.BasicAttackAI();
            ai.damage = 1;        // ~0.5 heart (engine units)
            ai.runSpeed = 1.05F;  // nimble
            ai.bind(l, this);
            this.ai = ai;
        } catch (Throwable ignored) {}
    }

    @Override
    public void tick() {
        super.tick();
        if (--life <= 0) this.remove();
    }
}
