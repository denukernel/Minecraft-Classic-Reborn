package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.player.Player;

public class TeleportCommand implements Command {
    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String getUsage() {
        return "/tp <x> <y> <z>";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || sender == null || mc.level == null) return;

        if (args.length < 3) {
            if (mc.hud != null) mc.hud.addChat("&cUsage: &e" + getUsage());
            return;
        }

        try {
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);

            // --- Prevent teleporting into void ---
            if (y < -20) {
                if (mc.hud != null)
                    mc.hud.addChat("&c⚠ Cannot teleport below Y = -20 (void).");
                return;
            }

            Level lvl = mc.level;

            // === Finite level clamp ===
            if (!(lvl instanceof LevelInfiniteFlat) && !(lvl instanceof LevelInfiniteTerrain)) {
                int clampedX = Math.max(0, Math.min((int) x, lvl.width - 1));
                int clampedY = Math.max(0, Math.min((int) y, lvl.depth - 1));
                int clampedZ = Math.max(0, Math.min((int) z, lvl.height - 1));

                if (clampedX != (int) x || clampedY != (int) y || clampedZ != (int) z) {
                    if (mc.hud != null) {
                        mc.hud.addChat("&c⚠ Teleport clamped to finite world borders.");
                    }
                }

                x = clampedX;
                y = clampedY;
                z = clampedZ;
            } else {
                // === Infinite worlds (Flat/Terrain) ===
                if (mc.level.creativeMode &&
                    (Math.abs(x) >= 1_000_000 || Math.abs(z) >= 1_000_000)) {
                    mc.hud.addChat("&c⚠ Warning: Teleporting beyond 1,000,000 may cause instability!");
                }
            }

            // --- Execute teleport ---
            sender.setPos((float) x + 0.5f, (float) y, (float) z + 0.5f);
            sender.xd = sender.yd = sender.zd = 0;

            if (mc.hud != null) {
                mc.hud.addChat("&aTeleported to &7" + (int)x + " " + (int)y + " " + (int)z);
            }
        } catch (NumberFormatException e) {
            if (mc.hud != null) mc.hud.addChat("&cInvalid coordinates. Usage: &e" + getUsage());
        }
    }
}
