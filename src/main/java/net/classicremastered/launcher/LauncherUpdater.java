package net.classicremastered.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class LauncherUpdater {
    private static final String DOWNLOADS = "https://classicremastered.net/downloads/";
    private static final String JAR = "minecraft-classic-reborn.jar";
    private static final String BUILD_ATTRIBUTE = "Classic-Reborn-Build";
    private static final Pattern RELEASE = Pattern.compile("([0-9]{13}) ([a-f0-9]{64})");
    private static final Pattern CACHED = Pattern.compile("([0-9]{13})-([a-f0-9]{64})\\.jar");
    private static final int MAX_JAR_BYTES = 256 * 1024 * 1024;

    private LauncherUpdater() {}

    static boolean handOff(Path cache) {
        if (Boolean.getBoolean("mc.dev") || Boolean.getBoolean("minecraft.developerMode"))
            return false;
        try {
            Path current =
                    Path.of(
                            ClassicLauncher.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
            if (!Files.isRegularFile(current)) return false;
            Path update = selectUpdate(current, cache, new URL(DOWNLOADS));
            if (update.equals(current)) return false;

            String java =
                    ProcessHandle.current()
                            .info()
                            .command()
                            .orElseGet(
                                    () ->
                                            Path.of(System.getProperty("java.home"), "bin", "java")
                                                    .toString());
            new ProcessBuilder(java, "-jar", update.toString(), "--updated-launcher")
                    .inheritIO()
                    .start();
            return true;
        } catch (Exception ex) {
            System.err.println("[Launcher] Could not start the update: " + ex.getMessage());
            return false;
        }
    }

    static Path selectUpdate(Path current, Path cache, URL downloads) throws IOException {
        long currentBuild = readBuild(current);
        Path cached = findCached(cache, currentBuild);
        long installedBuild = cached == null ? currentBuild : readBuild(cached);
        try {
            Release release = readRelease(new URL(downloads, "launcher-release.txt"));
            if (release.build() > installedBuild) {
                return download(new URL(downloads, JAR), cache, release);
            }
        } catch (IOException ex) {
            System.err.println("[Launcher] Update check failed: " + ex.getMessage());
        }
        return cached == null ? current : cached;
    }

    private static Path findCached(Path cache, long currentBuild) throws IOException {
        if (!Files.isDirectory(cache)) return null;
        try (Stream<Path> files = Files.list(cache)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                var match = CACHED.matcher(file.getFileName().toString());
                if (!match.matches()) continue;
                long build = Long.parseLong(match.group(1));
                if (build <= currentBuild) break;
                try {
                    if (readBuild(file) == build && checksum(file).equals(match.group(2)))
                        return file;
                } catch (IOException ignored) {
                    // An unreadable cached jar cannot replace the bundled launcher.
                }
            }
        }
        return null;
    }

    private static Release readRelease(URL url) throws IOException {
        HttpURLConnection connection = connect(url);
        try (InputStream input = response(connection)) {
            byte[] bytes = input.readNBytes(128);
            var match = RELEASE.matcher(new String(bytes, StandardCharsets.US_ASCII).strip());
            if (bytes.length == 128 || !match.matches())
                throw new IOException("Invalid release manifest");
            return new Release(Long.parseLong(match.group(1)), match.group(2));
        } finally {
            connection.disconnect();
        }
    }

    private static Path download(URL url, Path cache, Release release) throws IOException {
        Files.createDirectories(cache);
        Path destination = cache.resolve(release.build() + "-" + release.sha256() + ".jar");
        Path staging = Files.createTempFile(cache, "download-", ".part");
        try {
            HttpURLConnection connection = connect(url);
            try (InputStream input = response(connection);
                    var output = Files.newOutputStream(staging)) {
                byte[] buffer = new byte[65536];
                long deadline = System.nanoTime() + 60_000_000_000L;
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_JAR_BYTES || System.nanoTime() > deadline) {
                        throw new IOException("Update download exceeded its size or time limit");
                    }
                    output.write(buffer, 0, count);
                }
            } finally {
                connection.disconnect();
            }
            if (!checksum(staging).equals(release.sha256())
                    || readBuild(staging) != release.build()) {
                throw new IOException("Downloaded jar does not match the release manifest");
            }
            return Files.move(
                    staging,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        return connection;
    }

    private static InputStream response(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK)
            throw new IOException("Release server returned HTTP " + status);
        return connection.getInputStream();
    }

    private static long readBuild(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            if (jar.getManifest() == null
                    || !ClassicLauncher.class
                            .getName()
                            .equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))
                    || jar.getJarEntry("net/classicremastered/launcher/ClassicLauncher.class")
                            == null) {
                throw new IOException("Invalid launcher jar");
            }
            String build = jar.getManifest().getMainAttributes().getValue(BUILD_ATTRIBUTE);
            if (build == null || !build.matches("[0-9]{13}"))
                throw new IOException("Missing launcher build id");
            return Long.parseLong(build);
        }
    }

    private static String checksum(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest())
            hex.append(Character.forDigit((value & 255) >>> 4, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }

    private record Release(long build, String sha256) {}
}
