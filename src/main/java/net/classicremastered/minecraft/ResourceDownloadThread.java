package net.classicremastered.minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;

public class ResourceDownloadThread extends Thread {
    public volatile boolean running = false;
    private volatile boolean finished = true;

    private final File mcDir; // e.g. %APPDATA%/.minecraftclassicreborn/client
    private final Minecraft mc;
    private final File resDir; // mcDir/resources

    private static final String ARCHIVE_URL = "https://classicremastered.net/downloads/resources.zip";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private int sfxCount = 0, musicCount = 0;

    public ResourceDownloadThread(File minecraftFolder, Minecraft minecraft) {
        super("Resource loader (Classic)");
        setDaemon(true);
        this.mcDir = minecraftFolder;
        this.mc = minecraft;
        this.resDir = new File(mcDir, "resources");
    }

    public static boolean resourcesPresent(File mcDir) {
        File r = new File(mcDir, "resources");
        for (String root : new String[] { "sound", "newsound", "sound3", "music" }) {
            if (hasAudio(new File(r, root))) return true;
        }
        return false;
    }

    private static boolean hasAudio(File file) {
        if (file.isFile()) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.length() > 0 && (name.endsWith(".ogg") || name.endsWith(".wav"));
        }
        File[] children = file.listFiles();
        if (children != null) for (File child : children) if (hasAudio(child)) return true;
        return false;
    }

    public void downloadSoundsOnly() {
        /* not used */ }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void run() {
        finished = false;
        running = true;
        try {
            if (!resDir.exists())
                resDir.mkdirs();

            // Reuse a local archive for offline installs, otherwise fetch from our website.
            if (!resourcesPresent(mcDir)) {
                File archive = new File(mcDir, "resources.zip");
                boolean extracted = archive.isFile() && extractZip(archive, resDir);
                if (!extracted && downloadFile(ARCHIVE_URL, archive)) {
                    extracted = extractZip(archive, resDir);
                }
                if (extracted) safeDelete(archive);
                else System.err.println("[Resources] Could not install audio resources; retrying next launch.");
            }

            // Register assets into PaulsCode.
            if (mc.soundPC instanceof PaulsCodeSoundManager) {
                PaulsCodeSoundManager sm = mc.soundPC;
                scanDir(new File(resDir, "sound"), (f, rel) -> {
                    sm.registerSound(f, rel);
                    sfxCount++;
                });
                scanDir(new File(resDir, "newsound"), (f, rel) -> {
                    sm.registerSound(f, rel);
                    sfxCount++;
                });
                scanDir(new File(resDir, "sound3"), (f, rel) -> {
                    sm.registerSound(f, rel);
                    sfxCount++;
                }); // <--- added
                registerBundledSound(sm, "mob/cow/classic_idle.ogg");
                registerBundledSound(sm, "mob/cow/classic_hurt.ogg");
                scanDir(new File(resDir, "music"), (f, rel) -> {
                    registerMusic(sm, f, rel);
                    musicCount++;
                });
                System.out.println("[Resources] Registered SFX=" + sfxCount + ", music=" + musicCount);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            running = false;
            finished = true;
        }
    }

    /* ================= Download ================= */

    private boolean downloadFile(String urlStr, File outFile) {
        System.out.println("[Resources] Downloading: " + urlStr);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "ClassicClient/1.0");
            int code = conn.getResponseCode();
            if (code / 100 == 3) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn.disconnect();
                    url = new URL(loc);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);
                }
            }
            if (conn.getResponseCode() != 200) {
                System.err.println("[Resources] HTTP " + conn.getResponseCode() + " for " + urlStr);
                return false;
            }

            File tmp = new File(outFile.getParentFile(), outFile.getName() + ".part");
            tmp.getParentFile().mkdirs();

            long total = 0;
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                    OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
                byte[] buf = new byte[1 << 16];
                int n, lastMbPrinted = 0;
                while (running && (n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    total += n;
                    int mb = (int) (total >> 20);
                    if (mb - lastMbPrinted >= 4) {
                        System.out.println("[Resources] Downloaded " + mb + " MB...");
                        lastMbPrinted = mb;
                    }
                }
            }
            if (!running || (conn.getContentLengthLong() >= 0 && total != conn.getContentLengthLong())) {
                safeDelete(tmp);
                return false;
            }

            if (outFile.exists())
                safeDelete(outFile);
            Files.move(tmp.toPath(), outFile.toPath());
            System.out.println("[Resources] Download complete: " + outFile.getAbsolutePath());
            return true;
        } catch (Exception ex) {
            System.err.println("[Resources] Download failed: " + ex);
            return false;
        } finally {
            if (conn != null)
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
        }
    }

    /* ================= Extract: .zip ================= */

    private boolean extractZip(File archive, File destDir) {
        System.out.println("[Resources] Extracting (zip): " + archive.getAbsolutePath());
        File staging = null;
        try (ZipFile zip = new ZipFile(archive)) {
            // Failed or cancelled extraction must not leave a partially installed resource tree.
            staging = Files.createTempDirectory(mcDir.toPath(), "resource-install-").toFile();
            String prefix = staging.getCanonicalPath() + File.separator;
            int files = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (!running) return false;
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("./")) name = name.substring(2);
                if (name.startsWith("resources/")) name = name.substring(10);
                if (entry.isDirectory()) continue;
                if (!(name.startsWith("sound/") || name.startsWith("newsound/")
                        || name.startsWith("sound3/") || name.startsWith("music/"))) continue;
                File out = new File(staging, name);
                if (!out.getCanonicalPath().startsWith(prefix)
                        || java.util.Arrays.asList(name.split("/")).contains("..")) {
                    throw new IOException("Unsafe resource path: " + name);
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".ogg") || lower.endsWith(".wav"))) continue;
                Files.createDirectories(out.toPath().getParent());
                try (InputStream in = zip.getInputStream(entry);
                        OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (!running) return false;
                        os.write(buffer, 0, n);
                    }
                }
                files++;
            }
            if (files == 0 || !running) return false;
            Files.createDirectories(destDir.toPath());
            for (File root : staging.listFiles()) {
                File destination = new File(destDir, root.getName());
                if (destination.isDirectory() && destination.list().length == 0) Files.delete(destination.toPath());
                Files.move(root.toPath(), destination.toPath());
            }
            System.out.println("[Resources] Extracted files: " + files);
            return true;
        } catch (Exception ex) {
            System.err.println("[Resources] zip extract failed: " + ex);
            return false;
        } finally {
            if (staging != null) deleteStaging(staging);
        }
    }

    private void deleteStaging(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteStaging(child);
        safeDelete(file);
    }

    /* ================= Register helpers ================= */

    private void registerBundledSound(PaulsCodeSoundManager sm, String path) {
        String resourcePath = "sound/" + path;
        if (!new File(resDir, resourcePath).isFile()) {
            URL bundled = ResourceDownloadThread.class.getResource("/" + resourcePath);
            if (bundled != null) {
                sm.registerSound(bundled, path);
                sfxCount++;
            }
        }
    }

    private interface Sink {
        void accept(File file, String relPath) throws Exception;
    }

    private void scanDir(File root, Sink sink) {
        if (!running || !root.isDirectory())
            return;
        final String base = root.getAbsolutePath().replace('\\', '/') + "/";
        final ArrayDeque<File> q = new ArrayDeque<>();
        q.add(root);

        while (!q.isEmpty() && running) {
            File cur = q.removeFirst();
            File[] ls = cur.listFiles();
            if (ls == null)
                continue;

            for (File f : ls) {
                if (!running)
                    break;
                if (f.isDirectory()) {
                    q.add(f);
                    continue;
                }
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".ogg") || lower.endsWith(".wav")))
                    continue;

                // keep full relative path (e.g. "mob/sheep/say1.ogg")
                String rel = f.getAbsolutePath().replace('\\', '/').substring(base.length());

                try {
                    sink.accept(f, rel);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void registerMusic(PaulsCodeSoundManager sm, File file, String rel) {
        // rel like "calm1.ogg" or "ambient/calm1.ogg" → pool "calm"
        String name = rel;
        int slash = name.lastIndexOf('/');
        if (slash >= 0)
            name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0)
            name = name.substring(0, dot);
        String pool = name.replaceAll("\\d+$", "");
        if (pool.isEmpty())
            pool = "calm";
        sm.registerMusic(pool, file);
    }

    private void safeDelete(File f) {
        try {
            if (f != null && f.exists())
                f.delete();
        } catch (Throwable ignored) {
        }
    }
}
