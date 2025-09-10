package net.classicremastered.minecraft.mob.ai;

import java.io.Serializable;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;

public abstract class AI implements Serializable {

   public static final long serialVersionUID = 0L;
   public int defaultLookAngle = 0;


   public void tick(Level var1, Mob var2) {}

   public void beforeRemove() {}

   public void hurt(Entity var1, int var2) {}
}
