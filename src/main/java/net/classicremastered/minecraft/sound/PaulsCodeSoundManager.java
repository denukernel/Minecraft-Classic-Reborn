package net.classicremastered.minecraft.sound;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.codecs.CodecJOrbis;
import paulscode.sound.codecs.CodecWav;
import paulscode.sound.libraries.LibraryLWJGLOpenAL;

import java.io.File;
import java.net.URL;
import java.util.*;

public final class PaulsCodeSoundManager {
    public final Random rng = new Random();

    // Pool name -> URL list
    private final Map<String, List<URL>> sfx = new HashMap<>();
    private final Map<String, List<URL>> musicPools = new HashMap<>();

    private final SoundSystem sys;
    private final Set<String> registeredKeys = new HashSet<>();

    // Far Lands audio chaos
    public boolean farlandsActive = false;
    public long playerDist = 0; // updated from Minecraft.java
    private int musicGlitchCounter = 0;
    private int herobrineCounter = 0; // NEW: timer for fake chat

    private float master = 1.0f;
    private int seq = 0;

    private GameSettings settings;

    public PaulsCodeSoundManager(GameSettings settings) {
        this.settings = settings;   // <--- REQUIRED
        try {
            try {
                SoundSystemConfig.addLibrary(LibraryLWJGLOpenAL.class);
            } catch (Throwable openalMissing) {
                SoundSystemConfig.addLibrary(paulscode.sound.libraries.LibraryJavaSound.class);
            }
            SoundSystemConfig.setCodec("ogg", CodecJOrbis.class);
            SoundSystemConfig.setCodec("wav", CodecWav.class);
        } catch (Throwable t) {
            System.err.println("[Audio] init failed: " + t);
        }
        sys = new SoundSystem();
    }

    /* ---------------- registry helpers ---------------- */

    public boolean hasSound(String key) {
        if (key == null)
            return false;
        key = key.replace('/', '.'); // normalize
        return registeredKeys.contains(key);
    }

    public void registerSound(File file, String id) {
        String noExt = id;
        int dot = noExt.lastIndexOf('.');
        if (dot > 0)
            noExt = noExt.substring(0, dot);

        int slash = noExt.lastIndexOf('/');
        String folder = (slash >= 0) ? noExt.substring(0, slash) : "";
        String base = (slash >= 0) ? noExt.substring(slash + 1) : noExt;

        String pooledName = base.replaceAll("\\d+$", "");
        String poolKey = (folder.isEmpty() ? pooledName : (folder + "/" + pooledName));

        try {
            sfx.computeIfAbsent(poolKey, k -> new ArrayList<>()).add(file.toURI().toURL());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerMusic(String poolKey, File file) {
        try {
            URL url = file.toURI().toURL();
            musicPools.computeIfAbsent(poolKey, k -> new ArrayList<>()).add(url);
            registeredKeys.add(poolKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ---------------- listener (Classic Player) ---------------- */

    public void setListener(Player p, float partial) {
        if (p == null)
            return;

        float x = p.xo + (p.x - p.xo) * partial;
        float y = p.yo + (p.y - p.yo) * partial;
        float z = p.zo + (p.z - p.zo) * partial;

        float pitch = p.xRotO + (p.xRot - p.xRotO) * partial;
        float yaw = p.yRotO + (p.yRot - p.yRotO) * partial;

        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);

        float fx = -sy * cp, fy = sp, fz = -cy * cp;
        float ux = -sy * sp, uy = cp, uz = -cy * sp;

        sys.setListenerPosition(x, y, z);
        sys.setListenerOrientation(fx, fy, fz, ux, uy, uz);
    }

    /* ---------------- playback ---------------- */

    // Normal play (subject to corruption if farlandsActive)
    public void playSound(String key, float volume, float pitch) {
        playSoundInternal(key, 0f, 0f, 0f, volume, pitch, false, false);
    }

    // Positional play (subject to corruption if farlandsActive)
    public void playSoundAt(String key, float x, float y, float z, float volume, float pitch) {
        playSoundInternal(key, x, y, z, volume, pitch, true, false);
    }

    // Clean play (NEVER corrupted, for GUI/menu sounds)
    public void playSoundClean(String key, float volume, float pitch) {
        if (!settings.sound) return;
        playSoundInternal(key, 0f, 0f, 0f, volume, pitch, false, true);
    }


    private static String poolKeyOf(String key) {
        if (key == null)
            return null;
        key = key.replace('\\', '/');
        int slash = key.lastIndexOf('/');
        if (slash < 0)
            return key.replaceAll("\\d+$", "");
        String folder = key.substring(0, slash);
        String base = key.substring(slash + 1);
        base = base.replaceAll("\\d+$", "");
        return folder + "/" + base;
    }

    private void playSoundInternal(String key, float x, float y, float z, float volume, float pitch, boolean positional,
            boolean forceClean) {
        if (key == null || volume <= 0f)
            return;
        key = key.replace('\\', '/');

        String chosenKey = key;

        // Far Lands corruption rules (unchanged)
        if (farlandsActive && !forceClean) {
            if (playerDist > 5_000_000) {
                List<String> keys = new ArrayList<>(sfx.keySet());
                if (!keys.isEmpty()) {
                    chosenKey = keys.get(rng.nextInt(keys.size()));
                }
            } else if (playerDist > 1_000_000 && rng.nextFloat() < 0.3f) {
                List<String> keys = new ArrayList<>(sfx.keySet());
                if (!keys.isEmpty()) {
                    chosenKey = keys.get(rng.nextInt(keys.size()));
                }
            }
        }

        List<URL> list = sfx.get(chosenKey);
        if (list == null || list.isEmpty()) {
            String pooled = poolKeyOf(chosenKey);
            if (pooled != null)
                list = sfx.get(pooled);
        }
        if (list == null || list.isEmpty()) {
            System.out.println("[Audio] Missing sound: " + chosenKey + " (orig=" + key + ")");
            return;
        }

        URL u = list.get(rng.nextInt(list.size()));
        String id = "sfx_" + (seq = (seq + 1) & 0xFF);
        boolean priority = volume > 1.0f;

        // --- FIXED attenuation ---
        // Was 16.0f → way too short, caused mobs to sound quiet up close.
        // Use ~32f like vanilla MC, fade out by ~32 blocks.
        float attDist = 32.0f; // or 64.0f if you want longer mob sounds
        int attModel  = positional ? SoundSystemConfig.ATTENUATION_LINEAR : SoundSystemConfig.ATTENUATION_NONE;

        if (positional) {
            sys.newSource(priority, id, u, u.toString(), false, x, y, z, attModel, attDist);
        } else {
            sys.newSource(priority, id, u, u.toString(), false, 0, 0, 0, 0, 0);
        }
        sys.setPitch(id, Math.max(0.01f, pitch));
        sys.setVolume(id, Math.min(1f, volume) * master);
        sys.play(id);
    }

    /* ---------------- music ---------------- */

    public void playMusic(String poolKey) {
        playMusic(poolKey, 1.0f);
    }

    public void playMusic(String poolKey, float volume) {
        List<URL> list = musicPools.get(poolKey);
        if (list == null || list.isEmpty()) {
            System.out.println("[Audio] Missing music: " + poolKey);
            return;
        }
        URL u = list.get(rng.nextInt(list.size()));
        if (sys.playing("BgMusic"))
            sys.stop("BgMusic");
        sys.newStreamingSource(true, "BgMusic", u, u.toString(), true, 0, 0, 0, 2, 32f);
        sys.setVolume("BgMusic", Math.min(1f, volume) * master);
        sys.play("BgMusic");
    }

    public void stopMusic() {
        if (sys.playing("BgMusic"))
            sys.stop("BgMusic");
    }

    /* ---------------- Far Lands corruption tick ---------------- */

    public void tickFarlandsAudio() {
        if (!farlandsActive)
            return;

        // Music corruption
        if (sys.playing("BgMusic")) {
            musicGlitchCounter++;
            if (musicGlitchCounter >= 40) { // ~2s
                musicGlitchCounter = 0;

                int type = rng.nextInt(5);
                String restartKey = "calm1"; // safe default
                if (!musicPools.isEmpty()) {
                    List<String> keys = new ArrayList<>(musicPools.keySet());
                    restartKey = keys.get(rng.nextInt(keys.size()));
                }

                switch (type) {
                case 0:
                    sys.setPitch("BgMusic", 0.5f + rng.nextFloat() * 2f);
                    break;
                case 1:
                    sys.setVolume("BgMusic", rng.nextFloat() * master * 2f);
                    break;
                case 2:
                    sys.stop("BgMusic");
                    playMusic(restartKey);
                    break;
                case 3:
                    playMusic(restartKey);
                    break;
                case 4:
                    sys.stop("BgMusic");
                    playMusic(restartKey);
                    break;
                }
            }
        }
    }

    /* ---------------- Herobrine chat injection ---------------- */

    public String maybeGetHerobrineMessage() {
        if (!farlandsActive)
            return null;

        herobrineCounter++;
        if (herobrineCounter >= 600) { // ~30s at 20TPS
            herobrineCounter = 0;

            if (rng.nextFloat() < 0.2f) { // 20% chance
                if (playerDist > 5_000_000) {
                    String[] msgs = { "&7Herobrine joined the game", "&7Herobrine left the game",
                            "&cHerobrine is watching you", "&7..." };
                    return msgs[rng.nextInt(msgs.length)];
                } else {
                    // milder messages for 1–5M
                    String[] msgs = { "&7Herobrine joined the game", "&7Herobrine left the game" };
                    return msgs[rng.nextInt(msgs.length)];
                }
            }
        }
        return null;
    }

    /* ---------------- misc ---------------- */

    public void setMasterVolume(float v) {
        master = Math.max(0f, Math.min(1f, v));
    }

    private int herobrineFigureTimer = 0;

    public boolean shouldSpawnHerobrineFigure() {
        if (!farlandsActive || playerDist < 6_000_000)
            return false;

        if (--herobrineFigureTimer <= 0) {
            herobrineFigureTimer = 20 * (30 + 5);
            // 30s lifetime + 5s delay before respawn
            return true;
        }
        return false;
    }

    public void resetAudioState() {
        farlandsActive = false;
        playerDist = 0;
        musicGlitchCounter = 0;
        herobrineCounter = 0;
        try {
            if (sys.playing("BgMusic")) {
                sys.stop("BgMusic");
            }
        } catch (Throwable ignored) {
        }
        System.out.println("[FarLandsAudio] Reset to normal state.");
    }

    public void shutdown() {
        try {
            sys.cleanup();
        } catch (Throwable ignored) {
        }
    }
}
