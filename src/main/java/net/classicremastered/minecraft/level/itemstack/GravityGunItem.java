// com/mojang/minecraft/level/itemstack/GravityGunItem.java
package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

public final class GravityGunItem extends ToolItem {

    public GravityGunItem(int id) {
        super(id, "Gravity Gun", "/items/bow.png");
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        hud(player, "&e[DEBUG] GravityGun used");

        HeldBlockEntity held = findHeld(level, player);
        if (held != null && held.isHeld()) {
            held.throwNow(0.95f);
            hud(player, "&7Block launched!");
            return;
        }

        int[] pick = pickBlock(level, player, 5.5f);
        if (pick == null) {
            hud(player, "&8(no block)");
            return;
        }

        int bx = pick[0], by = pick[1], bz = pick[2], id = pick[3];
        if (id == 0 || id == Block.BEDROCK.id) {
            hud(player, "&8(cannot grab)");
            return;
        }

        level.netSetTile(bx, by, bz, 0);
        HeldBlockEntity e = new HeldBlockEntity(level, player, id);
        level.addEntity(e);

        if (e.removed) {
            hud(player, "&c[DEBUG] Entity removed immediately!");
        }

        hud(player, "&7Block grabbed!");
    }


    private static HeldBlockEntity findHeld(Level lvl, Player p) {
        var list = lvl.findEntities(p, p.bb.grow(16,16,16));
        for (int i=0;i<list.size();i++) {
            Object o = list.get(i);
            if (o instanceof HeldBlockEntity) {
                HeldBlockEntity hb = (HeldBlockEntity)o;
                if (hb.isHeld()) return hb;
            }
        }
        return null;
    }

    /** returns {x,y,z,id} or null */
    private static int[] pickBlock(Level lvl, Player p, float maxDist) {
        float ex = p.x, ey = p.y + p.heightOffset, ez = p.z;
        float yaw = p.yRot * (float)Math.PI/180f;
        float pitch = p.xRot * (float)Math.PI/180f;

        float fx = -net.classicremastered.util.MathHelper.sin(yaw) * net.classicremastered.util.MathHelper.cos(pitch);
        float fy = -net.classicremastered.util.MathHelper.sin(pitch);
        float fz =  net.classicremastered.util.MathHelper.cos(yaw) * net.classicremastered.util.MathHelper.cos(pitch);

        var mop = lvl.clip(new Vec3D(ex,ey,ez), new Vec3D(ex+fx*maxDist, ey+fy*maxDist, ez+fz*maxDist));
        if (mop == null) return null;
        int x = mop.x, y = mop.y, z = mop.z;
        return new int[]{x,y,z, lvl.getTile(x,y,z)};
    }

    private static void hud(Player p, String msg) {
        if (p != null && p.level != null && p.level.minecraft != null) {
            p.level.minecraft.hud.addChat(msg);
        }
    }
}
