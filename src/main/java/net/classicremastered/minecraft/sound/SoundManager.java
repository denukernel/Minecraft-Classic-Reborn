package net.classicremastered.minecraft.sound;

import java.io.File;
import java.util.Random;

public final class SoundManager {

    // keep old fields so legacy code compiles
    public final Random random = new Random();
    public String lastMusic = null;

    public void init() {}
    public void shutdown() {}

    // registration no-ops
    public void registerSound(File file, String key) {}
    public void registerMusic(String poolKey, File file) {}

    // playback no-ops
    public void playSound(String key, float volume, float pitch) {}
    public void playSound(String key) {}
    public void playMusic(String poolKey, float volume) { lastMusic = poolKey; }
    public void playMusic(String poolKey) { lastMusic = poolKey; }
    public void stopMusic() {}
    public void setMasterVolume(float volume) {}
    public void setPerKeyGain(String key, float gain) {}


    // Some trees call playMusic(SoundPlayer, String). We just ignore the first arg.
    public void playMusic(Object ignoredSoundPlayer, String poolKey) { playMusic(poolKey); }
}
