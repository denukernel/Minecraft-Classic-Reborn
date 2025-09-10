// com/mojang/minecraft/mob/BossZombieBase.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public abstract class BossZombieBase extends Zombie {

    // Simple phase/ability bookkeeping
    protected int phase = 1;
    protected boolean enraged = false;
    protected int abilityCD = 0;   // general purpose cooldown
    protected int summonCD  = 0;   // secondary cooldown
    protected int tickAge   = 0;

    public BossZombieBase(Level level, float x, float y, float z) {
        super(level, x, y, z);
        this.deathScore = 1000; // bosses worth more
    }

    @Override
    public void tick() {
        super.tick();
        tickAge++;
        if (abilityCD > 0) abilityCD--;
        if (summonCD  > 0) summonCD--;
        bossTick(); // delegate per-boss logic
    }

    @Override
    public void hurt(net.classicremastered.minecraft.Entity src, int dmg) {
        super.hurt(src, dmg);
        if (!enraged && this.health <= 10) { // threshold tweak per boss
            enraged = true;
            onEnrage();
        }
    }

    protected void onEnrage() {}        // optional override
    protected abstract void bossTick(); // per-boss brain each tick
}
