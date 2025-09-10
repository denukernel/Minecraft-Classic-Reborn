package net.classicremastered.minecraft.level.tile;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;

public class LiquidBlock extends Block {

   protected LiquidType type;
   protected int stillId;
   protected int movingId;


   protected LiquidBlock(int var1, LiquidType var2) {
      super(var1, "Liquid");
      this.type = var2;
      this.textureId = 14;
      if(var2 == LiquidType.LAVA) {
         this.textureId = 30;
      }

      Block.liquid[var1] = true;
      this.movingId = var1;
      this.stillId = var1 + 1;
      float var4 = 0.01F;
      float var3 = 0.1F;
      this.setBounds(var4 + 0.0F, 0.0F - var3 + var4, var4 + 0.0F, var4 + 1.0F, 1.0F - var3 + var4, var4 + 1.0F);
      this.setPhysics(true);
      if(var2 == LiquidType.LAVA) {
         this.setTickDelay(16);
      }

   }

   public final boolean isCube() {
      return false;
   }

   public final void onPlace(Level level, int x, int y, int z) {
      level.addToTickNextTick(x, y, z, this.movingId);
   }

   public void update(Level level, int x, int y, int z, Random rand) {
      boolean var7 = false;
      z = z;
      y = y;
      x = x;
      level = level;
      boolean var8 = false;

      boolean var6;
      do {
         --y;
         if(level.getTile(x, y, z) != 0 || !this.canFlow(level, x, y, z)) {
            break;
         }

         if(var6 = level.setTile(x, y, z, this.movingId)) {
            var8 = true;
         }
      } while(var6 && this.type != LiquidType.LAVA);

      ++y;
      if(this.type == LiquidType.WATER || !var8) {
         var8 = var8 | this.flow(level, x - 1, y, z) | this.flow(level, x + 1, y, z) | this.flow(level, x, y, z - 1) | this.flow(level, x, y, z + 1);
      }

      if(!var8) {
         level.setTileNoUpdate(x, y, z, this.stillId);
      } else {
         level.addToTickNextTick(x, y, z, this.movingId);
      }

   }

   private boolean canFlow(Level var1, int var2, int var3, int var4) {
      if(this.type == LiquidType.WATER) {
         for(int var7 = var2 - 2; var7 <= var2 + 2; ++var7) {
            for(int var5 = var3 - 2; var5 <= var3 + 2; ++var5) {
               for(int var6 = var4 - 2; var6 <= var4 + 2; ++var6) {
                  if(var1.getTile(var7, var5, var6) == Block.SPONGE.id) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private boolean flow(Level level, int x, int y, int z) {
       if (level.getTile(x, y, z) == 0) {
           if (!this.canFlow(level, x, y, z)) {
               return false;
           }

           if (level.setTile(x, y, z, this.movingId)) {
               // 💧 Immediately update neighbor, no waiting
               Block.blocks[this.movingId].update(level, x, y, z, random);
               return true;
           }
       }
       return false;
   }

   @Override
   public int getLightValue() {
       return this.type == LiquidType.LAVA ? 15 : 0; // max brightness for lava
   }


   @Override
   public final boolean canRenderSide(Level level, int x, int y, int z, int side) {
       // Use proper infinite bounds check
       if (!level.isInBounds(x, y, z)) {
           return false;
       }

       int id = level.getTile(x, y, z);
       if (id == this.movingId || id == this.stillId) {
           return false; // skip self-adjacent liquids
       }

       // Special case: top face should render if any neighbor is air
       if (side == 1) {
           if (level.getTile(x - 1, y, z) == 0 ||
               level.getTile(x + 1, y, z) == 0 ||
               level.getTile(x, y, z - 1) == 0 ||
               level.getTile(x, y, z + 1) == 0) {
               return true;
           }
       }

       return super.canRenderSide(level, x, y, z, side);
   }

   public final void renderInside(ShapeRenderer shapeRenderer, int x, int y, int z, int side) {
      super.renderInside(shapeRenderer, x, y, z, side);
      super.renderSide(shapeRenderer, x, y, z, side);
   }

   public final boolean isOpaque() {
      return true;
   }
   @Override
   protected final float getBrightness(Level level, int x, int y, int z) {
       return level.getBrightness(x, y, z);
   }

   public final boolean isSolid() {
      return false;
   }

   public final LiquidType getLiquidType() {
      return this.type;
   }

   public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
       if (changedId != 0) {
           LiquidType other = Block.blocks[changedId].getLiquidType();
           if ((this.type == LiquidType.WATER && other == LiquidType.LAVA)
            || (this.type == LiquidType.LAVA  && other == LiquidType.WATER)) {
               level.setTile(x, y, z, Block.STONE.id);
               return;
           }
       }

       // Always reschedule THIS liquid block, not the neighbor
       level.addToTickNextTick(x, y, z, this.movingId);
   }


   @Override
   public final int getTickDelay() {
       return this.type == LiquidType.LAVA ? 5 : 2; // lava slower, water every tick
   }


   public final void dropItems(Level var1, int var2, int var3, int var4, float var5) {}

   public final void onBreak(Level var1, int var2, int var3, int var4) {}

   public final int getDropCount() {
      return 0;
   }

   public final int getRenderPass() {
      return this.type == LiquidType.WATER?1:0;
   }

	@Override
	public AABB getCollisionBox(int x, int y, int z)
	{
		return null;
	}
}
