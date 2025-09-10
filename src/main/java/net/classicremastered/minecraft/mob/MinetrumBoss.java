package net.classicremastered.minecraft.mob;

import static net.classicremastered.minecraft.mob.ai.BossAttackAI.*;

import java.util.ArrayList;
import java.util.Iterator;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public final class MinetrumBoss extends BossZombieBase {

    // Mode rotation
    private int mode = 0;      // 0=throw, 1=tree crush, 2=water->lava, 3=pillars
    private int modeCD = 0;

    // Auto-revert for temp blocks
    private static final class TempBlock {
        int x,y,z,original,ttl;
        TempBlock(int x,int y,int z,int orig,int ttl){this.x=x;this.y=y;this.z=z;this.original=orig;this.ttl=ttl;}
    }
    private final ArrayList<TempBlock> tempBlocks = new ArrayList<>();

    public MinetrumBoss(Level l, float x, float y, float z) {
        super(l, x, y, z);
        this.health = 250;
        try { ((net.classicremastered.minecraft.mob.ai.BasicAttackAI)this.ai).runSpeed = 0.95f; } catch (Throwable ignored){}
    }

    @Override protected void onEnrage() {
        if (level != null && level.minecraft != null)
            level.minecraft.hud.addChat("&6Minetrum bends the earth!");
    }

    @Override protected void bossTick() {
        tickTempBlocks();

        Player p = target(this, 18f);
        if (p == null) return;
        face(this, p.x, p.z, 5f);

        if (!enraged && this.health <= 125) { enraged = true; onEnrage(); }

        if (modeCD > 0) { modeCD--; return; }

        switch (mode) {
            case 0: throwBlockAt(p);      mode = 1; break;
            case 1: treeCrush(p);         mode = 2; break;
            case 2: waterToLavaPulse();   mode = 3; break;
            default: pillarsAround(p);    mode = 0; break;
        }
        modeCD = enraged ? 70 : 100; // faster while enraged
    }

    // --- abilities ---

    private void throwBlockAt(Player p) {
        if (level == null) return;
        // Simple LOS check
        if (level.clip(new net.classicremastered.minecraft.model.Vec3D(this.x, this.y+this.heightOffset, this.z),
                       new net.classicremastered.minecraft.model.Vec3D(p.x,    p.y+p.heightOffset,     p.z)) == null) {
            p.hurt(this, 3);
            if (level.minecraft != null)
                level.minecraft.hud.addChat("&7Minetrum hurls debris at you!");
        }
    }

    private void treeCrush(Player p) {
        if (level == null) return;
        int px = (int)Math.floor(p.x), py = (int)Math.floor(p.y), pz = (int)Math.floor(p.z);
        int x = px, y = py + 2, z = pz;
        if (!inBounds(x,y,z)) return;

        int idAbove = level.getTile(x,y,z);
        if (idAbove == 0) {
            int LOG = Block.LOG.id;
            level.netSetTile(x,y,z, LOG);
            tempBlocks.add(new TempBlock(x,y,z, 0, 20)); // 1s revert
            p.hurt(this, 10);
            if (level.minecraft != null)
                level.minecraft.hud.addChat("&2Minetrum crushes you with timber!");
        } else {
            p.hurt(this, 8);
        }
    }

    private void waterToLavaPulse() {
        if (level == null) return;
        int cx = (int)Math.floor(this.x), cy = (int)Math.floor(this.y), cz = (int)Math.floor(this.z);
        int converted = 0, limit = enraged ? 18 : 12;
        for (int dx = -4; dx <= 4 && converted < limit; dx++) {
            for (int dz = -4; dz <= 4 && converted < limit; dz++) {
                int x = cx + dx, z = cz + dz;
                for (int dy = -2; dy <= 1 && converted < limit; dy++) {
                    int y = cy + dy;
                    if (!inBounds(x,y,z)) continue;
                    int id = level.getTile(x,y,z);
                    if (id > 0 && Block.blocks[id].getLiquidType() == LiquidType.WATER) {
                        level.netSetTile(x,y,z, Block.LAVA.id);
                        tempBlocks.add(new TempBlock(x,y,z, id, 160)); // revert ~8s
                        converted++;
                    }
                }
            }
        }
        if (converted > 0 && level.minecraft != null)
            level.minecraft.hud.addChat("&cMinetrum scorches the waters!");
    }

    private void pillarsAround(Player p) {
        if (level == null) return;
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2}};
        int STONE = Block.STONE.id;
        int baseX = (int)Math.floor(p.x), baseZ = (int)Math.floor(p.z), baseY = (int)Math.floor(p.y);

        int ttl = enraged ? 100 : 140; // ~5s/7s
        for (int[] off : offsets) {
            int x = baseX + off[0], z = baseZ + off[1];
            for (int h = 0; h < 2; h++) {
                int y = baseY + h;
                if (!inBounds(x,y,z)) continue;
                int orig = level.getTile(x,y,z);
                if (orig == 0) {
                    level.netSetTile(x,y,z, STONE);
                    tempBlocks.add(new TempBlock(x,y,z, 0, ttl));
                }
            }
        }
        if (level.minecraft != null)
            level.minecraft.hud.addChat("&7Minetrum shapes stone around you!");
    }

    private void tickTempBlocks() {
        if (tempBlocks.isEmpty() || level == null) return;
        Iterator<TempBlock> it = tempBlocks.iterator();
        while (it.hasNext()) {
            TempBlock t = it.next();
            if (--t.ttl <= 0) {
                int cur = level.getTile(t.x,t.y,t.z);
                if (t.original == 0) {
                    if (cur != 0) level.netSetTile(t.x,t.y,t.z, 0);
                } else {
                    if (cur != t.original) level.netSetTile(t.x,t.y,t.z, t.original);
                }
                it.remove();
            }
        }
    }

    private boolean inBounds(int x,int y,int z){
        return x>=0 && y>=0 && z>=0 && x<level.width && y<level.depth && z<level.height;
    }
}
