package net.classicremastered.util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ensures LWJGL 2.8.4 Windows natives are present at:
 *   %APPDATA%/.mcraft/client/native/windows
 * If missing, downloads the official zip and extracts /native/windows/* only.
 * Then sets org.lwjgl.librarypath and net.java.games.input.librarypath to that directory.
 */
public final class LwjglNativesDownloader {

    private static final String VERSION = "2.8.4";
    // SourceForge official path; replace if you mirror it somewhere else.
    private static final String LWJGL_ZIP_URL =
            "https://downloads.sourceforge.net/project/java-game-lib/Official%20Releases/LWJGL%202.8.4/lwjgl-2.8.4.zip";

    private static final String MARKER_FILE = "lwjgl-" + VERSION + ".ok";

    // Minimal set of 32-bit natives MC Classic needs on Windows
    private static final String[] REQUIRED = {
            "lwjgl.dll", "OpenAL32.dll", "jinput-dx8.dll", "jinput-raw.dll"
    };

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private LwjglNativesDownloader() {}

    public static void setupWindowsNatives() {
        if (!isWindows()) return; // no-op on non-Windows
        if (!INITIALIZED.compareAndSet(false, true)) return;

        Path dir = getNativesDir();
        try {
            Files.createDirectories(dir);

            if (!isInstalled(dir)) {
                System.out.println("[LWJGL] natives missing; downloading 2.8.4...");
                Path zip = dir.resolve("lwjgl-" + VERSION + ".zip");
                downloadWithProgress(new URL(LWJGL_ZIP_URL), zip);
                extractWindowsNatives(zip, dir);
                Files.deleteIfExists(zip);
                // Write marker
                Files.write(dir.resolve(MARKER_FILE),
                        ("ok " + VERSION + " " + System.currentTimeMillis()).getBytes("UTF-8"));
            }

            // Point LWJGL & JInput to the folder
            System.setProperty("org.lwjgl.librarypath", dir.toString());
            System.setProperty("net.java.games.input.librarypath", dir.toString());
            System.out.println("[LWJGL] library path = " + dir);

        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare LWJGL natives in " + dir, e);
        }
    }

    public static Path getNativesDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            // Fallback (very rare on Windows)
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, ".mcraft", "client", "native", "windows");
    }

    private static boolean isInstalled(Path dir) {
        // If marker exists and required DLLs exist, we’re good
        if (!Files.exists(dir.resolve(MARKER_FILE))) return false;
        for (String r : REQUIRED) {
            if (!Files.exists(dir.resolve(r))) return false;
        }
        return true;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private static void downloadWithProgress(URL url, Path out) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);

        int code = con.getResponseCode();
        if (code / 100 == 3) { // handle redirect manually if needed
            String loc = con.getHeaderField("Location");
            if (loc != null) {
                con.disconnect();
                downloadWithProgress(new URL(loc), out);
                return;
            }
        } else if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " " + con.getResponseMessage());
        }

        long size = Math.max(0, con.getContentLengthLong());
        try (InputStream in = new BufferedInputStream(con.getInputStream());
             OutputStream fo = new BufferedOutputStream(Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            byte[] buf = new byte[1 << 16];
            long read = 0;
            long lastPrint = 0;
            for (int n; (n = in.read(buf)) >= 0; ) {
                fo.write(buf, 0, n);
                read += n;
                long now = System.currentTimeMillis();
                if (now - lastPrint > 1000) {
                    lastPrint = now;
                    if (size > 0) {
                        int pct = (int) (100 * read / size);
                        System.out.println("[LWJGL] downloading... " + pct + "%");
                    } else {
                        System.out.println("[LWJGL] downloading... " + (read / 1024) + " KiB");
                    }
                }
            }
        } finally {
            con.disconnect();
        }
    }

    private static void extractWindowsNatives(Path zip, Path outDir) throws IOException {
        final String wantedPrefix = "lwjgl-" + VERSION + "/native/windows/";
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry e;
            int count = 0;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!name.startsWith(wantedPrefix) || e.isDirectory()) continue;

                String fileName = name.substring(wantedPrefix.length());
                Path dst = outDir.resolve(fileName);
                Files.createDirectories(dst.getParent());

                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(
                        dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                    byte[] buf = new byte[1 << 15];
                    for (int n; (n = zin.read(buf)) >= 0; ) out.write(buf, 0, n);
                }
                count++;
                System.out.println("[LWJGL] extracted " + fileName);
            }
            if (count == 0) throw new IOException("No /native/windows entries found in " + zip);
        }
    }
}
