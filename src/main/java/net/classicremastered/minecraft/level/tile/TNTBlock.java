package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.item.PrimedTnt;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.particle.ParticleManager;

//com/mojang/minecraft/level/tile/TNTBlock.java
public final class TNTBlock extends Block {

 public TNTBlock(int id, int tex) {
     super(id, tex, "TNT");
     // keep your sound/hardness if you set them elsewhere
 }
 @Override
 protected int getTextureId(int side) {
     // 0=bottom, 1=top, 2..5=sides
     if (side == 0) return 10; // bottom
     if (side == 1) return  9; // top
     return 8;                 // sides
 }

 // --- BREAK: drops TNT (not primed) ---
 @Override public int getDropCount() { return 1; }
 @Override public int getDrop()      { return this.id; }

 @Override
 public void onBreak(Level level, int x, int y, int z) {
     // normal block break → drop myself (super handles items via dropItems)
     super.onBreak(level, x, y, z);
 }

 @Override
 public void spawnBreakParticles(Level level, int x, int y, int z,
                                 net.classicremastered.minecraft.particle.ParticleManager pm) {
     // just debris; DO NOT prime here
     super.spawnBreakParticles(level, x, y, z, pm);
 }

 // --- IGNITE: remove block, spawn PrimedTnt ---
 public static void ignite(Level level, int x, int y, int z) {
     if (level == null) return;
     // clear the TNT block first
     level.setTile(x, y, z, 0);

     // spawn a primed entity centered (uses your PrimedTnt class)
     net.classicremastered.minecraft.item.PrimedTnt primed =
         new net.classicremastered.minecraft.item.PrimedTnt(level, x + 0.5f, y + 0.5f, z + 0.5f);
     level.addEntity(primed);

     // optional: fuse sound (you already wired Level.playSound)
     level.playSound("random/fuse", x + 0.5f, y + 0.5f, z + 0.5f, 1.0f, 1.0f);
 }

 // Optional convenience: right-clicking TNT with anything can be ignored;
 // Flint & Steel path will handle ignite from the click handler or here if you prefer.
 @Override
 public boolean onRightClick(Level level, int x, int y, int z,
                             net.classicremastered.minecraft.player.Player player, int face) {
     // Let Flint & Steel path decide; default no-op.
     return false;
 }
}

