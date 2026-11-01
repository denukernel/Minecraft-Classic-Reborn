package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.util.MathHelper;

public class GrapplingHookItem extends Item {
    public static final java.util.Map<Player, Vec3D> anchors = new java.util.WeakHashMap<>();

    public GrapplingHookItem(int id) {
        super(id, "Grappling Hook", "/items/grappling_hook.png", 1);
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null) return;

        if (anchors.containsKey(player)) {
            anchors.remove(player);
            level.playSound("random/click", player, 0.8f, 1.2f);
            return;
        }

        // Raycast forward to find a block
        float yaw = player.yRot, pitch = player.xRot;
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);

        float lx = sy * cp;
        float ly = sp;
        float lz = cy * cp;

        float range = 24.0f;
        Vec3D start = new Vec3D(player.x, player.y + 0.12f, player.z);
        Vec3D end = start.add(lx * range, ly * range, lz * range);

        MovingObjectPosition mop = level.clip(start, end);
        if (mop != null) {
            anchors.put(player, mop.vec);
            level.playSound("random/pop", player, 0.8f, 0.8f);
        } else {
            level.playSound("random/click", player, 0.5f, 0.5f);
        }
    }

    @Override
    public void tick(Player player, Level level) {
        if (player == null || level == null) return;

        Vec3D anchor = anchors.get(player);
        if (anchor == null) return;

        float dx = (float) (anchor.x - player.x);
        float dy = (float) (anchor.y - player.y);
        float dz = (float) (anchor.z - player.z);
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > 25.0f) {
            // Out of range, break the grapple
            anchors.remove(player);
            level.playSound("random/click", player, 0.5f, 0.5f);
            return;
        }

        if (dist > 1.5f) {
            // Apply spring pull force to the player
            float pull = 0.08f;
            player.xd += (dx / dist) * pull;
            player.yd += (dy / dist) * pull;
            player.zd += (dz / dist) * pull;

            // Apply damping to current player motion to control swinging speed
            player.xd *= 0.98f;
            player.yd *= 0.98f;
            player.zd *= 0.98f;
        }
    }
}
