package net.classicremastered.minecraft.player;

import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.LongTag;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

/** Lifetime totals, independent of the current spendable balance. */
public final class PlayerStats implements Serializable {
    private static final long serialVersionUID = 1L;
    private long kills;
    private long deaths;
    private long coins;
    private long score;
    private static final String MOB_PREFIX = "mobKills.";
    private Map<String, Long> mobKills;

    public long kills() {
        return kills;
    }

    public long deaths() {
        return deaths;
    }

    public long coins() {
        return coins;
    }

    public long score() {
        return score;
    }

    private void add(long kills, long coins, long score) {
        this.kills += kills;
        this.coins += coins;
        this.score += score;
    }

    static String mobType(Mob mob) {
        short id = MobRegistry.idOf(mob);
        return id >= 0 ? Short.toString(id) : mob.getClass().getName();
    }

    public static String mobName(String type) {
        try {
            return MobRegistry.displayName(Short.parseShort(type));
        } catch (NumberFormatException ignored) {
            String name = type.substring(type.lastIndexOf('.') + 1);
            return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        }
    }

    public Map<String, Long> mobKills() {
        return mobKills == null ? Map.of() : Map.copyOf(mobKills);
    }

    void record(String mobType, long coins, long score, boolean died) {
        add(mobType == null ? 0 : 1, coins, score);
        if (died) deaths++;
        if (mobType != null) {
            if (mobKills == null) mobKills = new HashMap<>();
            mobKills.merge(mobType, 1L, Long::sum);
        }
    }

    public CompoundTag saveWorld() {
        CompoundTag tag = new CompoundTag("LifetimeStats");
        tag.putLong("MobKills", kills);
        tag.putLong("Deaths", deaths);
        tag.putLong("MineCoinsEarned", coins);
        tag.putLong("ScoreEarned", score);
        CompoundTag breakdown = new CompoundTag("MobKillsByType");
        mobKills().forEach(breakdown::putLong);
        tag.put(breakdown);
        return tag;
    }

    public void loadWorld(CompoundTag tag) {
        kills = Math.max(0, tag.getLong("MobKills"));
        deaths = Math.max(0, tag.getLong("Deaths"));
        coins = Math.max(0, tag.getLong("MineCoinsEarned"));
        score = Math.max(0, tag.getLong("ScoreEarned"));
        mobKills = new HashMap<>();
        for (var entry : tag.getCompound("MobKillsByType").getAllTags()) {
            if (entry instanceof LongTag count && count.value > 0) {
                mobKills.put(entry.getName(), count.value);
            }
        }
    }

    private static Path globalFile() {
        String appData = System.getenv("APPDATA");
        String folder =
                appData == null || appData.isBlank() ? System.getProperty("user.home") : appData;
        return Path.of(folder, ".classicreborn", "stats.properties");
    }

    public static synchronized PlayerStats loadGlobal() throws IOException {
        PlayerStats stats = new PlayerStats();
        Path file = globalFile();
        if (Files.notExists(file)) return stats;
        Properties data = new Properties();
        try (var input = Files.newInputStream(file)) {
            data.load(input);
            stats.add(
                    Long.parseLong(data.getProperty("mobKills", "0")),
                    Long.parseLong(data.getProperty("mineCoinsEarned", "0")),
                    Long.parseLong(data.getProperty("scoreEarned", "0")));
            stats.deaths = Math.max(0, Long.parseLong(data.getProperty("deaths", "0")));
            stats.mobKills = new HashMap<>();
            for (String key : data.stringPropertyNames()) {
                if (!key.startsWith(MOB_PREFIX)) continue;
                long count = Long.parseLong(data.getProperty(key));
                if (count > 0) stats.mobKills.put(key.substring(MOB_PREFIX.length()), count);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid global stats", invalid);
        }
        return stats;
    }

    static synchronized void recordGlobal(String mobType, long coins, long score, boolean died)
            throws IOException {
        PlayerStats stats = loadGlobal();
        stats.record(mobType, coins, score, died);
        Properties data = new Properties();
        data.setProperty("mobKills", Long.toString(stats.kills));
        data.setProperty("deaths", Long.toString(stats.deaths));
        data.setProperty("mineCoinsEarned", Long.toString(stats.coins));
        data.setProperty("scoreEarned", Long.toString(stats.score));
        stats.mobKills()
                .forEach((type, count) -> data.setProperty(MOB_PREFIX + type, count.toString()));
        Path destination = globalFile();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "stats-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                data.store(output, "Classic Reborn lifetime statistics");
            }
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
