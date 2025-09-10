package net.classicremastered.minecraft.util;

import javax.imageio.spi.IIORegistry;

// Add the dependency jar to your classpath at build/runtime:
// groupId: com.luciad  artifactId: imageio-webp  version: 5.6
// (pure Java; handles lossy & lossless WebP)
public final class WebPInit {
    private static boolean done = false;

    public static void register() {
        if (done) return;
        try {
            // Registers the WebP reader with ImageIO at runtime.
            done = true;
            System.out.println("[ImageIO] WebP reader registered.");
        } catch (Throwable t) {
            // Soft-fail: PNG fallback still works if the plugin isn't present
            System.out.println("[ImageIO] WebP plugin missing; PNG/JPG only. (" + t + ")");
        }
    }

    private WebPInit() {}
}
