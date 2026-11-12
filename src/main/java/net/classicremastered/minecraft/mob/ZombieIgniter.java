package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.util.MathHelper;

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
        if (ignitionCooldown > 0)
            ignitionCooldown--;
        if (health <= 0 || removed || corruptedToDefendPlayer || level.isNetworkMode()
                || ignitionCooldown > 0 || isInWaterOrOnWater()
                || !(ai instanceof BasicAttackAI combat)
                || !(combat.attackTarget instanceof Player player) || player.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(player) || distanceToSqr(player) > 25
                || !combat.hasLineOfSight(this, player))
            return;
        boolean lit = distanceToSqr(player) <= 6.25f && player.ignite(100);
        double angle = Math.atan2(player.z - z, player.x - x);
        int tx = MathHelper.floor(x + (float) Math.cos(angle) * 1.25f);
        int tz = MathHelper.floor(z + (float) Math.sin(angle) * 1.25f);
        int ty = MathHelper.floor(bb.y0 + .1f);
        if (level.isInBounds(tx, ty, tz) && level.getTile(tx, ty, tz) == 0
                && FireBlock.canStay(level, tx, ty, tz))
            lit |= level.setTile(tx, ty, tz, Block.FIRE.id);
        if (lit) {
            ignitionCooldown = 60;
            attackTime = 10;
            level.playSound("random/click", this, 1f, 1f);
        }
    }
}
