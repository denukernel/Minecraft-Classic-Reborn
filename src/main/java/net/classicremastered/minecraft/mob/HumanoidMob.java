package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;

public abstract class HumanoidMob extends Mob {

    public static final long serialVersionUID = 0L;
    public boolean helmet = Math.random() < 0.20000000298023224D;
    public boolean armor = Math.random() < 0.20000000298023224D;

    public HumanoidMob(Level var1, float var2, float var3, float var4) {
        super(var1);
        this.setPos(var2, var3, var4);
    }
}
