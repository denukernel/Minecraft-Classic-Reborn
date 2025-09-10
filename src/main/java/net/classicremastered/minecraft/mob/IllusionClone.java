package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

/** Harmless visual duplicate that drifts and expires. */
public final class IllusionClone extends Mob {
    private int life = 120; // ~6s

    public IllusionClone(Level l, float x, float y, float z) {
        super(l);
        this.modelName  = "zombie";          // keep boss look; swap to "humanoid" if preferred
        this.textureName= "/mob/zombie.png"; // or "/char.png"
        this.heightOffset = 1.62F;
        this.setPos(x, y, z);
        this.setSize(0.6F, 1.8F);
        this.noPhysics = true;
        this.makeStepSound = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (--life <= 0) this.remove();
        // gentle drift
        this.xd *= 0.9f; this.zd *= 0.9f;
        if ((life % 10)==0 && level != null) {
            this.xd += (level.random.nextFloat()-0.5f)*0.02f;
            this.zd += (level.random.nextFloat()-0.5f)*0.02f;
        }
    }
}
