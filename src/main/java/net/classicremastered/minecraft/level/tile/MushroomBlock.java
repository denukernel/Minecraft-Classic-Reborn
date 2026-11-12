package net.classicremastered.minecraft.level.tile;

public final class MushroomBlock extends FlowerBlock {

   protected MushroomBlock(int var1, int var2) {
      super(var1, var2);
      float var3 = 0.2F;
      this.setBounds(0.5F - var3, 0.0F, 0.5F - var3, var3 + 0.5F, var3 * 2.0F, var3 + 0.5F);
   }

    @Override
    protected boolean supports(int blockId) {
        return blockId == STONE.id || blockId == COBBLESTONE.id;
   }
}
