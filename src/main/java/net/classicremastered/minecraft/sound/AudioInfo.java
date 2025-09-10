package net.classicremastered.minecraft.sound;

public class AudioInfo
{
	public float volume = 1.0F;
    public final String key;
    public final float pitch;
    public AudioInfo(String key, float volume, float pitch) {
        this.key = key; this.volume = volume; this.pitch = pitch;
    }
	public int update(short[] var1, int var2)
	{
		return 0;
	}
}
