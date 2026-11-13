package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.enderman.EndermanAI;
import net.classicremastered.minecraft.phys.AABB;

public final class Enderman extends Mob {

    private static final long serialVersionUID = 3193678308608478868L;

    public boolean isAttacking = false; // drives model animation

    public static final int ATTACK_DURATION = 20; // attack cooldown ticks

    // Model proportions (vanilla-ish): slim (0.6) x very tall (~2.9)
    private static final float COLLISION_WIDTH = 0.6F;
    private static final float COLLISION_HEIGHT = 2.9F;

    public Enderman(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);

        this.heightOffset = 0.0F;
        this.health = 40;
        this.speed = 0.25F;
        this.deathScore = 500;
        this.coinDrop = 6;
        this.footSize = COLLISION_WIDTH;
        this.getAndSetAABB(COLLISION_WIDTH, COLLISION_HEIGHT);
        this.ai = new EndermanAI();
    }

    /** Enderman positions use feet height, including teleport destinations. */
    @Override
    public void setPos(float x, float feetY, float z) {
        this.x = x;
        this.y = feetY;
        this.z = z;
        this.bbWidth = COLLISION_WIDTH;
        this.bbHeight = COLLISION_HEIGHT;
        this.heightOffset = 0;
        getAndSetAABB(COLLISION_WIDTH, COLLISION_HEIGHT);
    }

    /**
     * Creates a tall slim AABB centered on the entity (uses current x/y/z). Called once in ctor;
     * tick() also refreshes in case engine doesn't auto-sync.
     */
    private void getAndSetAABB(float width, float height) {
        float hw = width * 0.5f;
        // Note: Entity/Mob uses this.x, this.y, this.z as the entity origin.
        this.bb =
                new AABB(
                        this.x - hw, // x0
                        this.y, // y0 (feet at entity.y)
                        this.z - hw, // z0
                        this.x + hw, // x1
                        this.y + height, // y1 (top)
                        this.z + hw // z1
                        );
    }

    @Override
    public void tick() {
        super.tick();

        // Keep the AABB in sync in case the engine doesn't auto-update it.
        // This is cheap and robust.
        float hw = COLLISION_WIDTH * 0.5f;
        this.bb.set(
                this.x - hw,
                this.y,
                this.z - hw,
                this.x + hw,
                this.y + COLLISION_HEIGHT,
                this.z + hw);
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (source instanceof Arrow && ai instanceof EndermanAI) {
            tryEvadeProjectile();
            return;
        }
        super.hurt(source, damage);
    }

    public boolean tryEvadeProjectile() {
        return health > 0
                && !removed
                && level != null
                && !level.isNetworkMode()
                && ai instanceof EndermanAI endermanAI
                && endermanAI.teleportation().teleportRandom(this);
    }

    @Override
    public boolean isPickable() {
        return !this.removed; // keep default behavior but explicit
    }

    @Override
    public boolean isPushable() {
        return !this.removed;
    }

    @Override
    public float getPickRadius() {
        // Slightly larger pick radius than default so clicking the tall mob is reliable
        return COLLISION_WIDTH;
    }
}
