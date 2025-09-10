package net.classicremastered.minecraft.util;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class Debug {
    // Toggle this to false to disable all structure logs.
    public static boolean STRUCTURES = true;

    private static String fmt(String s) { return s == null ? "" : s; }

    public static void struct(Level level, String msg) {
        if (!STRUCTURES) return;
        String line = "[STRUCT] " + fmt(msg);
        System.out.println(line); // console

        // Try to mirror to in-game chat (best-effort)
        if (level != null) {
            try {
                Object ctx = level.minecraft;
                if (ctx instanceof Minecraft) {
                    Minecraft mc = (Minecraft) ctx;
                    if (mc != null && mc.hud != null) mc.hud.addChat("&7" + line);
                }
            } catch (Throwable ignored) {}
        }
    }
}
