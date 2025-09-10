package net.classicremastered.minecraft.util;

import java.nio.file.Files;
import java.nio.file.Path;

import net.classicremastered.util.LwjglNativesDownloader;

/** No download. Ensures JInput uses the same natives folder as LWJGL. */
public final class JInputBootstrap {

    private static final String[] REQUIRED = { "jinput-dx8.dll", "jinput-raw.dll" };

    private JInputBootstrap() {}

    public static void setupWindows() {
        // Use the exact same dir as LWJGL
        Path dir = LwjglNativesDownloader.getNativesDir();

        try {
            // Make sure LWJGL natives (which include JInput DLLs) are present
            LwjglNativesDownloader.setupWindowsNatives();

            // Re-check JInput DLLs (LWJGL zip contains them)
            for (String n : REQUIRED) {
                if (!Files.exists(dir.resolve(n))) {
                    throw new IllegalStateException("Missing " + n + " in " + dir +
                        " (LWJGL 2.8.4 zip should include it).");
                }
            }

            // Point JInput to the folder
            System.setProperty("net.java.games.input.librarypath", dir.toString());
            System.out.println("[JInput] library path = " + dir);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare JInput natives in " + dir, e);
        }
    }
}
