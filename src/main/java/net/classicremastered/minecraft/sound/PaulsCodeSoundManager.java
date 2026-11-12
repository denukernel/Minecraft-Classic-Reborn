package net.classicremastered.minecraft.sound;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;
import paulscode.sound.Channel;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.Source;
import paulscode.sound.codecs.CodecJOrbis;
import paulscode.sound.codecs.CodecWav;
import paulscode.sound.libraries.LibraryJavaSound;
import paulscode.sound.libraries.LibraryLWJGLOpenAL;

import java.io.File;
import java.net.URL;
import java.util.*;

public final class PaulsCodeSoundManager {
    public final Random rng = new Random();

    // Pool name -> URL list
    private final Map<String, List<URL>> sfx = new HashMap<>();
    private final Map<String, List<URL>> musicPools = new HashMap<>();

    private static final String FAR_LANDS_MELODY = "FarLandsMelody";
    private static final String FAR_LANDS_HARMONY = "FarLandsHarmony";
    private static final String MOB_SOURCE_PREFIX = "mob_sfx_";
    private static final float SOUND_RANGE = 32f;
    private static final float MOB_PAN_LIMIT = .6f;
    private static final float MOB_PAN_RADIUS = 8f;
    // Mob gain is set from the real distance. Linear attenuation keeps JavaSound panning enabled.
    private static final float MOB_PAN_RANGE = Float.MAX_VALUE;
    private static final float MINOR_THIRD = (float) Math.pow(2.0, 3.0 / 12.0);
    private final SoundSystem sys;
    private int farLandsTrackTick;
    private boolean farLandsTrackStarted;
    private final Set<String> registeredKeys = new HashSet<>();

    // Far Lands audio chaos
    public boolean farlandsActive = false;
    public long playerDist = 0; // updated from Minecraft.java
    private int musicGlitchCounter = 0;
    private int herobrineCounter = 0; // NEW: timer for fake chat

    private float master = 1.0f;
    private long seq;
    private volatile ListenerPosition listener;
    private final Map<String, MobPlayback> mobPlaybacks = new HashMap<>();

    private record MobPlayback(float x, float y, float z, float volume, long started) {
    }

    private record ListenerPosition(float x, float y, float z, float forwardX, float forwardZ) {
        float mobGain(float sourceX, float sourceY, float sourceZ) {
            float dx = sourceX - x, dy = sourceY - y, dz = sourceZ - z;
            return Math.max(0f, 1f - MathHelper.sqrt(dx * dx + dy * dy + dz * dz) / SOUND_RANGE);
        }

        Vec3D mobPanPosition(float sourceX, float sourceY, float sourceZ) {
            float dx = sourceX - x, dy = sourceY - y, dz = sourceZ - z;
            float distance = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
            float right = distance > 0f ? (dx * -forwardZ + dz * forwardX) / distance : 0f;
            float side = right * MOB_PAN_LIMIT;
            float forward = MathHelper.sqrt(1f - side * side);
            return new Vec3D(x + MOB_PAN_RADIUS * (forwardX * forward - forwardZ * side), y,
                    z + MOB_PAN_RADIUS * (forwardZ * forward + forwardX * side));
        }
    }

    public static final class MobBudgetOpenAL extends LibraryLWJGLOpenAL {
        public MobBudgetOpenAL() throws SoundSystemException {
        }

        @Override
        public void play(Source source) {
            if (canPlay(source, normalChannels))
                super.play(source);
            else
                removeSource(source.sourcename);
        }
    }

    public static final class MobBudgetJavaSound extends LibraryJavaSound {
        public MobBudgetJavaSound() throws SoundSystemException {
        }

        @Override
        public void play(Source source) {
            if (canPlay(source, normalChannels))
                super.play(source);
            else
                removeSource(source.sourcename);
        }
    }

    private static boolean canPlay(Source source, List<Channel> channels) {
        if (source == null || !source.sourcename.startsWith(MOB_SOURCE_PREFIX))
            return true;
        int reserve = Math.min(4, Math.max(0, channels.size() - 1));
        int free = 0;
        for (Channel channel : channels) {
            if (!channel.playing() && ++free > reserve)
                return true;
        }
        return false;
    }

    private GameSettings settings;

    public PaulsCodeSoundManager(GameSettings settings) {
        this.settings = settings;   // <--- REQUIRED
        try {
            try {
                SoundSystemConfig.addLibrary(MobBudgetOpenAL.class);
            } catch (Throwable openalMissing) {
                System.err.println("[Audio] OpenAL unavailable: " + openalMissing);
            }
            SoundSystemConfig.addLibrary(MobBudgetJavaSound.class);
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
        try {
            registerSound(file.toURI().toURL(), id);
        } catch (java.net.MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void registerSound(URL url, String id) {
        String noExt = id;
        int dot = noExt.lastIndexOf('.');
        if (dot > 0)
            noExt = noExt.substring(0, dot);

        int slash = noExt.lastIndexOf('/');
        String folder = (slash >= 0) ? noExt.substring(0, slash) : "";
        String base = (slash >= 0) ? noExt.substring(slash + 1) : noExt;

        String pooledName = base.replaceAll("\\d+$", "");
        String poolKey = (folder.isEmpty() ? pooledName : (folder + "/" + pooledName));

        sfx.computeIfAbsent(poolKey, k -> new ArrayList<>()).add(url);
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
        if (p == null) {
            clearMobPlayback();
            return;
        }

        float x = p.xo + (p.x - p.xo) * partial;
        float y = p.yo + (p.y - p.yo) * partial;
        float z = p.zo + (p.z - p.zo) * partial;

        float pitch = p.xRotO + (p.xRot - p.xRotO) * partial;
        float yaw = p.aimYaw(partial);

        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);

        float fx = sy * cp, fy = sp, fz = cy * cp;
        float ux = -sy * sp, uy = cp, uz = -cy * sp;

        var next = new ListenerPosition(x, y, z, sy, cy);
        synchronized (SoundSystemConfig.THREAD_SYNC) {
            sys.setListenerPosition(x, y, z);
            sys.setListenerOrientation(fx, fy, fz, ux, uy, uz);
            boolean changed = !next.equals(listener);
            listener = next;
            long now = System.nanoTime();
            var entries = mobPlaybacks.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                String id = entry.getKey();
                MobPlayback sound = entry.getValue();
                // The sound thread may not have processed the queued play command yet.
                if (now - sound.started > 250_000_000L && !sys.playing(id)) {
                    entries.remove();
                } else if (changed) {
                    Vec3D position = next.mobPanPosition(sound.x, sound.y, sound.z);
                    sys.setPosition(id, position.x, position.y, position.z);
                    sys.setVolume(id, sound.volume * master * next.mobGain(sound.x, sound.y, sound.z));
                }
            }
        }
    }

    private void clearMobPlayback() {
        synchronized (SoundSystemConfig.THREAD_SYNC) {
            listener = null;
            for (String id : mobPlaybacks.keySet())
                sys.removeSource(id);
            mobPlaybacks.clear();
        }
    }

    /* ---------------- playback ---------------- */

    // Normal play (subject to corruption if farlandsActive)
    public void playSound(String key, float volume, float pitch) {
        playSoundInternal(key, 0f, 0f, 0f, volume, pitch, false, false, false);
    }

    // Positional play (subject to corruption if farlandsActive)
    public void playSoundAt(String key, float x, float y, float z, float volume, float pitch) {
        playSoundAt(key, x, y, z, volume, pitch, false);
    }

    public void playSoundAt(String key, float x, float y, float z, float volume, float pitch,
            boolean mobSound) {
        playSoundInternal(key, x, y, z, volume, pitch, true, false, mobSound);
    }

    // Clean play (NEVER corrupted, for GUI/menu sounds)
    public void playSoundClean(String key, float volume, float pitch) {
        if (!settings.sound) return;
        playSoundInternal(key, 0f, 0f, 0f, volume, pitch, false, true, false);
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

    private void playSoundInternal(String key, float x, float y, float z, float volume, float pitch,
            boolean positional, boolean forceClean, boolean mobSound) {
        if (key == null || volume <= 0f)
            return;
        ListenerPosition currentListener = listener;
        boolean mixedMob = mobSound && currentListener != null;
        float distanceGain = mixedMob ? currentListener.mobGain(x, y, z) : 1f;
        if (distanceGain <= 0f)
            return;
        key = key.replace('\\', '/');

        if (settings != null && !settings.allowMobSounds) {
            if (key.startsWith("mob/") || key.startsWith("mob.")) {
                return;
            }
        }

        String chosenKey = key;

        if (farlandsActive && !forceClean
                && (playerDist > 5_000_000 || playerDist > 1_000_000 && rng.nextFloat() < 0.3f)) {
            chosenKey = randomFarLandsSoundKey(key);
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
        int attenuation = positional ? SoundSystemConfig.ATTENUATION_LINEAR
                : SoundSystemConfig.ATTENUATION_NONE;
        synchronized (SoundSystemConfig.THREAD_SYNC) {
            String id = (mobSound ? MOB_SOURCE_PREFIX : "sfx_") + ++seq;
            Vec3D position = mixedMob ? currentListener.mobPanPosition(x, y, z) : new Vec3D(x, y, z);
            float sourceVolume = Math.min(1f, volume);
            if (mixedMob)
                mobPlaybacks.put(id, new MobPlayback(x, y, z, sourceVolume, System.nanoTime()));
            sys.newSource(mobSound || volume > 1f, id, u, u.toString(), false, position.x, position.y,
                    position.z, attenuation, mixedMob ? MOB_PAN_RANGE : positional ? SOUND_RANGE : 0f);
            sys.setTemporary(id, true);
            sys.setPitch(id, Math.max(0.01f, pitch));
            sys.setVolume(id, sourceVolume * master * distanceGain);
            sys.play(id);
        }
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

    private synchronized String randomFarLandsSoundKey(String fallback) {
        if (sfx.isEmpty()) return fallback;
        List<String> keys = new ArrayList<>(sfx.keySet());
        return keys.get(rng.nextInt(keys.size()));
    }

    public synchronized void tickFarLandsSoundtrack(boolean enabled) {
        if (!enabled || !settings.sound) {
            stopFarLandsSoundtrack();
            return;
        }

        if (!farLandsTrackStarted) {
            List<URL> clips = sfx.get("note/pling");
            if (clips == null || clips.isEmpty()) return;
            URL clip = clips.get(rng.nextInt(clips.size()));
            sys.newSource(true, FAR_LANDS_MELODY, clip, clip.toString(), false,
                    0f, 0f, 0f, SoundSystemConfig.ATTENUATION_NONE, 0f);
            sys.newSource(true, FAR_LANDS_HARMONY, clip, clip.toString(), false,
                    0f, 0f, 0f, SoundSystemConfig.ATTENUATION_NONE, 0f);
            farLandsTrackStarted = true;
        }

        farLandsTrackTick = (farLandsTrackTick + 1) % (16 * 6);
        if (farLandsTrackTick % 6 != 0) return;
        int step = farLandsTrackTick / 6;
        int thirds = (step >= 8 ? 1 : 0) + step % 2;
        float pitch = .6f * (float) Math.pow(MINOR_THIRD, thirds);
        playFarLandsNote(FAR_LANDS_MELODY, pitch, .9f);
        playFarLandsNote(FAR_LANDS_HARMONY, pitch * MINOR_THIRD, .7f);
    }

    private void playFarLandsNote(String source, float pitch, float volume) {
        if (sys.playing(source)) sys.stop(source);
        sys.setPitch(source, pitch);
        sys.setVolume(source, volume * master);
        sys.play(source);
    }

    public synchronized void stopFarLandsSoundtrack() {
        if (!farLandsTrackStarted) return;
        if (sys.playing(FAR_LANDS_MELODY)) sys.stop(FAR_LANDS_MELODY);
        if (sys.playing(FAR_LANDS_HARMONY)) sys.stop(FAR_LANDS_HARMONY);
        sys.removeSource(FAR_LANDS_MELODY);
        sys.removeSource(FAR_LANDS_HARMONY);
        farLandsTrackStarted = false;
        farLandsTrackTick = 0;
    }

    public void tickFarlandsAudio() {
        if (!farlandsActive || !settings.music) return;

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
        clearMobPlayback();
        farlandsActive = false;
        stopFarLandsSoundtrack();
        playerDist = 0;
        musicGlitchCounter = 0;
        herobrineCounter = 0;
        try {
            if (sys.playing("BgMusic")) {
                sys.stop("BgMusic");
            }
        } catch (Throwable ignored) {
        }
    }

    public void shutdown() {
        try {
            sys.cleanup();
        } catch (Throwable ignored) {
        }
    }
}
