package net.classicremastered.minecraft.sound;

import net.classicremastered.minecraft.sound.SoundCategory;

/**
 * A registry object representing one sound entry.
 * Mods can register these with the SoundRegistryContext.
 */
public final class SoundEvent {
    public final String key;           // resource key, e.g. "mob/sheep/say1"
    public final SoundCategory category;
    public final float defaultVolume;
    public final float defaultPitch;

    public SoundEvent(String key, SoundCategory cat, float vol, float pitch) {
        this.key = key;
        this.category = cat;
        this.defaultVolume = vol;
        this.defaultPitch = pitch;
    }

    @Override
    public String toString() {
        return "SoundEvent[" + key + " in " + category + "]";
    }
}
