package net.classicremastered.toolkit.debugmodel;

import java.nio.file.*;

public final class LWJGLNatives {
    private LWJGLNatives() {}

    /** Use natives from %APPDATA%\.mcraft\client\native\windows on Windows. */
    public static void useMCraftClientNatives() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean is64 = arch.contains("64");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) {
                fail("APPDATA env var is missing; can’t locate .mcraft path.");
            }
            Path root   = Paths.get(appData, ".mcraft", "client", "native", "windows");
            Path folder = root; // you said libs live here exactly

            // Sanity-check expected files for LWJGL 2.8.4 (pick 32/64 variant)
            Path lwjgl = folder.resolve(is64 ? "lwjgl64.dll" : "lwjgl.dll");
            Path openal= folder.resolve("OpenAL32.dll"); // OpenAL usually this name even on 64-bit
            Path jdx8  = folder.resolve("jinput-dx8.dll");
            Path jraw  = folder.resolve("jinput-raw.dll");
            // (optional) jinput-wintab.dll may be absent

            if (!Files.isDirectory(folder)) fail("Natives folder not found: " + folder);
            if (!Files.exists(lwjgl))       fail("Missing " + lwjgl.getFileName() + " in " + folder);
            if (!Files.exists(openal))      fail("Missing " + openal.getFileName() + " in " + folder);
            if (!Files.exists(jdx8))        System.err.println("[warn] " + jdx8.getFileName() + " not found (may be OK).");
            if (!Files.exists(jraw))        System.err.println("[warn] " + jraw.getFileName() + " not found (may be OK).");

            System.setProperty("org.lwjgl.librarypath", folder.toAbsolutePath().toString());
            // Optional: helps diagnose bad paths
            System.setProperty("org.lwjgl.util.Debug", "true");
            System.out.println("[natives] org.lwjgl.librarypath = " + System.getProperty("org.lwjgl.librarypath"));
        } else if (os.contains("mac")) {
            // adapt if you also keep mac natives: ~/.mcraft/client/native/macos
            Path folder = Paths.get(System.getProperty("user.home"), ".mcraft", "client", "native", "macos");
            System.setProperty("org.lwjgl.librarypath", folder.toString());
        } else { // linux
            Path folder = Paths.get(System.getProperty("user.home"), ".mcraft", "client", "native", "linux");
            System.setProperty("org.lwjgl.librarypath", folder.toString());
        }
    }

    private static void fail(String msg) {
        throw new RuntimeException("LWJGL natives setup failed: " + msg);
    }
}
