package net.classicremastered.minecraft.level.tile;

public enum Tile$SoundType {
    none   ("none",   0, "none",          0.0F, 0.0F),
    grass  ("grass",  1, "step/grass",   0.6F, 1.0F),
    cloth  ("cloth",  2, "step/cloth",   0.7F, 1.2F),
    gravel ("gravel", 3, "step/gravel",  1.0F, 1.0F),
    stone  ("stone",  4, "step/stone",   1.0F, 1.0F),
    metal  ("metal",  5, "step/metal",   1.0F, 2.0F),
    wood   ("wood",   6, "step/wood",    1.0F, 1.0F);

    /** Pool key (matches ResourceDownloadThread + PaulsCode registry). */
    public final String pool;
    private final float volume;
    private final float pitch;

    Tile$SoundType(String name, int id, String poolName, float vol, float pit) {
        this.pool = poolName;
        this.volume = vol;
        this.pitch = pit;
    }

    public float getVolume() {
        return this.volume / (Block.random.nextFloat() * 0.4F + 1.0F) * 0.5F;
    }

    public float getPitch() {
        return this.pitch / (Block.random.nextFloat() * 0.2F + 0.9F);
    }
}
