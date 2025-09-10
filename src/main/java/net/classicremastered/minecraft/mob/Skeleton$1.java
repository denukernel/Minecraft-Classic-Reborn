package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.SmartHostileAI;

final class Skeleton$1 extends SmartHostileAI {
    public static final long serialVersionUID = 0L;
    final Skeleton parent;

    Skeleton$1(Skeleton var1) {
        this.parent = var1;
    }

    @Override
    public void tick(Level level, Mob mob) {
        super.tick(level, mob);

        // Shoot arrow occasionally when target is a player
        if (mob.health > 0 && this.attackTarget instanceof net.classicremastered.minecraft.player.Player) {
            if (this.random.nextInt(30) == 0) { // ~1.5s average
                parent.shootArrow(level);
            }
        }
    }

    @Override
    public void beforeRemove() {
        // Flair: one last arrow on death
        if (parent.level != null && !parent.removed) {
            parent.shootArrow(parent.level);
        }
    }
}
