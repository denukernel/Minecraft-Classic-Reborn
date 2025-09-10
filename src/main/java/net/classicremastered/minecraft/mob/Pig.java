package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.item.Item;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;

public class Pig extends QuadrupedMob {

   public static final long serialVersionUID = 0L;


   public Pig(Level var1, float var2, float var3, float var4) {
      super(var1, var2, var3, var4);
      this.heightOffset = 1.72F;
      this.modelName = "pig";
      this.textureName = "/mob/pig.png";
   }
}
