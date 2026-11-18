package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.FoodParticle;
import net.classicremastered.minecraft.player.Player;

public final class ThrownEgg extends ThrownEnderPearl {
    public ThrownEgg(Level level, Player owner) {
        super(level, owner, 1.5f);
    }

    @Override
    protected boolean tracksLanding() {
        return false;
    }

    @Override
    protected Item projectileItem() {
        return Item.EGG;
    }

    @Override
    protected void onImpact(Mob victim, Vec3D impact) {
        remove();
        if (level.isNetworkMode()) return;
        if (victim != null
                && !(victim instanceof Enderman enderman && enderman.tryEvadeProjectile())) {
            victim.animateHurt(-xd, -zd);
            if (xd * xd + zd * zd >= 1e-8f) victim.knockback(this, 0, -xd, -zd);
        }
        FoodParticle.spawnFragments(level, impact, Item.EGG.getTexture(), 8);
        if (level.random.nextInt(8) != 0) return;

        int count = level.random.nextInt(32) == 0 ? 4 : 1;
        for (int i = 0; i < count; i++) {
            Chicken chick = Chicken.hatch(level, impact.x, impact.y, impact.z);
            if (!level.getCubes(chick.bb).isEmpty()) {
                // Move the chick back along the throw to clear the impact surface.
                float speed = (float) Math.sqrt(xd * xd + yd * yd + zd * zd);
                if (speed < 1e-4f) return;
                float back = .35f / speed;
                chick.setPos(
                        impact.x - xd * back,
                        impact.y - yd * back + chick.bbHeight * .5f,
                        impact.z - zd * back);
                if (!level.getCubes(chick.bb).isEmpty()) return;
            }
            level.addEntity(chick);
        }
    }
}
