// com.mcraft.api.sound.SoundRegistryContext
package com.mcraft.api.sound;

import java.io.File;
import java.util.*;

import net.classicremastered.minecraft.sound.PaulsCodeSoundManager;
import net.classicremastered.minecraft.sound.SoundEvent;

public final class SoundRegistryContext {
    private final Map<String, List<File>> pools = new HashMap<>(); // "mob/sheep/say" -> files

    public void register(String poolKey, File file) {
        pools.computeIfAbsent(poolKey, k -> new ArrayList<>()).add(file);
    }

    // Convenience for SoundEvent
    public void register(SoundEvent evt, File file) {
        register(evt.key, file);
    }

    /** Push all registered sounds into the live Paulscode manager. */
    public void flushTo(PaulsCodeSoundManager pcm) {
        if (pcm == null)
            return;
        for (var e : pools.entrySet()) {
            String pool = e.getKey();
            for (File f : e.getValue()) {
                try {
                    pcm.registerSound(f, pool + ".ogg");
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
