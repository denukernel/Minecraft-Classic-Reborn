package net.classicremastered.minecraft.sound;

/**
 * Optional neutral container for decoded PCM. Not required by SoundReader anymore.
 * Keep for debugging or delete if unused.
 */
public final class SimpleSoundData {
    public final short[] pcm;   // interleaved L,R,L,R,...
    public final int sampleRate;
    public final int channels;

    public SimpleSoundData(short[] pcm, int sampleRate, int channels) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }
}
