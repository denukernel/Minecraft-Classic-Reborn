package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class ZombieIgniter extends Zombie {
    public static final short REGISTRY_ID = 12;
    private int ignitionCooldown;

    public ZombieIgniter(Level level, float x, float y, float z) {
        super(level, x, y, z);
        net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();
        equipment[4] = Item.FLINT_AND_STEEL.id + 256;
    }

    @Override
    public void tick() {
        super.tick();
        if (ignitionCooldown > 0) ignitionCooldown--;
        if (health <= 0
                || removed
                || corruptedToDefendPlayer
                || level.isNetworkMode()
                || ignitionCooldown > 0
                || isInWaterOrOnWater()
                || !(ai instanceof BasicAttackAI combat)
                || !(combat.attackTarget instanceof Mob target)
                || target.health <= 0
                || target.removed
                || CreativeModeHelper.shouldIgnoreTarget(target)
                || distanceToSqr(target) > 25
                || !combat.hasLineOfSight(this, target)) return;
        boolean lit = distanceToSqr(target) <= 6.25f && target.ignite(100);
        if (lit) {
            ignitionCooldown = 60;
            attackTime = 10;
            level.playSound("random/click", this, 1f, 1f);
        }
    }
}
