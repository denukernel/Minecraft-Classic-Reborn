package net.classicremastered.minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;

public class ResourceDownloadThread extends Thread {
    public volatile boolean running = false;
    private volatile boolean finished = true;

    private final File mcDir; // e.g. %APPDATA%/.mcraft/client
    private final Minecraft mc;
    private final File resDir; // mcDir/resources

    // === Hardcoded archive ===
    private static final String ARCHIVE_URL = "https://archive.org/download/Beta-1.7.3/resources.tar.gz"; // direct
                                                                                                          // .tar.gz

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
        return new File(r, "sound").isDirectory() || new File(r, "newsound").isDirectory()
                || new File(r, "music").isDirectory();
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

            // 1) If missing, fetch + extract from the hardcoded URL
            if (!resourcesPresent(mcDir)) {
                File tarGz = new File(mcDir, "resources.tar.gz");
                if (downloadFile(ARCHIVE_URL, tarGz)) {
                    boolean ok = extractTarGz(tarGz, resDir);
                    safeDelete(tarGz);
                    if (!ok)
                        System.err.println("[Resources] Extraction failed (tar.gz).");
                }
            }

            // 2) Register assets into PaulsCode
            // 2) Register assets into PaulsCode
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
            if (!running) {
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

    /* ================= Extract: .tar.gz ================= */

    private boolean extractTarGz(File tarGz, File destDir) {
        System.out.println("[Resources] Extracting (tar.gz): " + tarGz.getAbsolutePath());
        int files = 0;
        try (InputStream fin = new BufferedInputStream(new FileInputStream(tarGz));
                GZIPInputStream gin = new GZIPInputStream(fin)) {

            byte[] header = new byte[512];
            while (running) {
                int read = readFully(gin, header, 0, 512);
                if (read == -1)
                    break;
                if (read != 512)
                    break;

                // End-of-archive: two 512-byte zero blocks
                if (isAllZero(header)) {
                    readFully(gin, header, 0, 512);
                    break;
                }

                String name = tarString(header, 0, 100);
                long size = parseOctal(header, 124, 12);
                byte type = header[156]; // '0' file, '5' dir

                if (name.startsWith("./"))
                    name = name.substring(2);
                name = name.replace('\\', '/');

                if (name.startsWith("resources/"))
                    name = name.substring("resources/".length());

                if (type == '5') {
                    new File(destDir, name).mkdirs();
                } else if (type == '0' || type == 0) {
                    // Extract only our audio roots
                    if (name.startsWith("sound/") || name.startsWith("newsound/") || name.startsWith("sound3/")
                            || name.startsWith("music/")) {
                        File out = new File(destDir, name);
                        File p = out.getParentFile();
                        if (p != null && !p.exists())
                            p.mkdirs();

                        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                            copyExact(gin, os, size);
                        }
                        files++;
                    } else {
                        skipExact(gin, size);
                    }
                }

                long pad = (512 - (size % 512)) % 512;
                if (pad > 0)
                    skipExact(gin, pad);
            }

            System.out.println("[Resources] Extracted files: " + files);
            return files > 0;
        } catch (Exception ex) {
            System.err.println("[Resources] tar.gz extract failed: " + ex);
            return false;
        }
    }

    private int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(b, off + total, len - total);
            if (n < 0)
                return total == 0 ? -1 : total;
            total += n;
        }
        return total;
    }

    private boolean isAllZero(byte[] b) {
        for (byte v : b)
            if (v != 0)
                return false;
        return true;
    }

    private String tarString(byte[] b, int off, int len) throws UnsupportedEncodingException {
        int end = off;
        int max = off + len;
        while (end < max && b[end] != 0)
            end++;
        return new String(b, off, end - off, "US-ASCII");
    }

    private long parseOctal(byte[] b, int off, int len) {
        long val = 0;
        int i = off, end = off + len;
        while (i < end && (b[i] == 0x20 || b[i] == 0))
            i++; // skip spaces/NULs
        for (; i < end; i++) {
            byte c = b[i];
            if (c == 0 || c == 0x20)
                break;
            if (c < '0' || c > '7')
                break;
            val = (val << 3) + (c - '0');
        }
        return val;
    }

    private void copyExact(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[1 << 16];
        long left = n;
        while (left > 0 && running) {
            int toRead = (int) Math.min(buf.length, left);
            int r = in.read(buf, 0, toRead);
            if (r < 0)
                throw new EOFException("Unexpected EOF in tar stream");
            out.write(buf, 0, r);
            left -= r;
        }
    }

    private void skipExact(InputStream in, long n) throws IOException {
        byte[] buf = new byte[1 << 15];
        long left = n;
        while (left > 0) {
            int toRead = (int) Math.min(buf.length, left);
            int r = in.read(buf, 0, toRead);
            if (r < 0)
                throw new EOFException("Unexpected EOF skipping tar entry");
            left -= r;
        }
    }

    /* ================= Register helpers ================= */

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
