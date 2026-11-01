package net.classicremastered.minecraft.util;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class Debug {
    // Toggle this to false to disable all structure logs.
    public static boolean STRUCTURES = true;

    // Real-time Profiler for debugging performance bottlenecks in Eclipse
    public static boolean PROFILER = false;
    public static boolean PRINT_CONSOLE_PROFILER = false;

    // Section accumulation timers (in nanoseconds)
    public static long timeChunkMeshNanos = 0;
    public static long timeSortChunksNanos = 0;
    public static long timeLightEngineNanos = 0;
    public static long timeTickNanos = 0;
    public static long timeRenderNanos = 0;
    public static int chunkMeshCount = 0;

    public static String lastProfilerSummary = "";

    public static void recordChunkMesh(long nanos) {
        if (!PROFILER) return;
        timeChunkMeshNanos += nanos;
        chunkMeshCount++;
    }

    public static void recordLightEngine(long nanos) {
        if (!PROFILER) return;
        timeLightEngineNanos += nanos;
    }

    public static void recordSortChunks(long nanos) {
        if (!PROFILER) return;
        timeSortChunksNanos += nanos;
    }

    public static void recordTick(long nanos) {
        if (!PROFILER) return;
        timeTickNanos += nanos;
    }

    public static void recordRender(long nanos) {
        if (!PROFILER) return;
        timeRenderNanos += nanos;
    }

    public static String flushProfiler(int frames) {
        if (!PROFILER || frames <= 0) return "";
        double meshMs = timeChunkMeshNanos / (1_000_000.0 * frames);
        double sortMs = timeSortChunksNanos / (1_000_000.0 * frames);
        double tickMs = timeTickNanos / (1_000_000.0 * frames);

        String summary = String.format(
            "[PROFILER] %d FPS | Chunks Meshed: %d/s (%.2f ms/frame) | sortChunks: %.2f ms | tick: %.2f ms",
            frames, chunkMeshCount, meshMs, sortMs, tickMs
        );

        if (PRINT_CONSOLE_PROFILER) {
            System.out.println(summary);
        }

        lastProfilerSummary = summary;

        timeChunkMeshNanos = 0;
        timeSortChunksNanos = 0;
        timeLightEngineNanos = 0;
        timeTickNanos = 0;
        timeRenderNanos = 0;
        chunkMeshCount = 0;

        return summary;
    }

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
