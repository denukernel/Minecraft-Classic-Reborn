// File: src/com/mojang/minecraft/chat/commands/SpawnMobCommand.java
package net.classicremastered.minecraft.chat.commands;

import java.util.List;
import java.util.Locale;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;

public class SpawnMobCommand implements Command, AliasedCommand {

    // ===== Lag guards =====
    private static final int PER_CALL_CAP = 20;
    private static final int NEARBY_CAP = 40;
    private static final float NEARBY_RADIUS = 24.0f;

    // ---- Global rate limit ----
    private static final int BUCKET_CAPACITY = 30;
    private static final long REFILL_MS = 120_000L;
    private static double bucket = BUCKET_CAPACITY;
    private static long lastRefill = System.currentTimeMillis();

    private static synchronized int consumeTokens(int requested) {
        refillBucket();
        int granted = (int) Math.min(bucket, requested);
        bucket -= granted;
        return granted;
    }

    private static synchronized void refillBucket() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefill;
        if (elapsed <= 0)
            return;
        double toAdd = BUCKET_CAPACITY * (elapsed / (double) REFILL_MS);
        if (toAdd > 0) {
            bucket = Math.min(BUCKET_CAPACITY, bucket + toAdd);
            lastRefill = now;
        }
    }

    @Override
    public String getName() {
        return "spawnmob";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "spawn", "summon", "sm" };
    }

    @Override
    public String getUsage() {
        return "/spawn <mob> [count] [x y z] [mount]";
    }

    // HUD-safe text
    private static String safeAscii(String s) {
        if (s == null)
            return "";
        s = s.replace('§', '?').replace('&', '?');
        return s.replaceAll("[^\\u0020-\\u007E]", "");
    }

    // Reject list of abstract / reserved mob names
 // Reject list of abstract / reserved mob names
    private static final String[] DISALLOWED_NAMES = {
        "entity", "mob", "human", "humanoid", "humanoidmob",
        "quadrupedmob", "player", "bot", "playerclone" // added
    };

    private static boolean isDisallowedName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (String s : DISALLOWED_NAMES) {
            if (s.equals(key))
                return true;
        }
        return false;
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null || mc.player == null) {
            hud(mc, "&cNo level/player loaded.");
            return;
        }
        if (args.length < 1) {
            hud(mc, "&cUsage: " + getUsage());
            return;
        }

        try {
            MobRegistry.bootstrapDefaults();
        } catch (Throwable ignored) {
        }

        String rawName = safeAscii(args[0]);
        if (isDisallowedName(rawName)) {
            hud(mc, "&cYou cannot spawn this entity type: &f" + rawName);
            return;
        }

     // === NEW: Special case for bots ===
     if ("bot".equalsIgnoreCase(rawName) || "playerclone".equalsIgnoreCase(rawName)) {
         hud(mc, "&cBots cannot be spawned via /spawnmob.");
         hud(mc, "&7Use &e/bot spawn <count> <goal> &7instead.");
         return;
     }

        short mobId = MobRegistry.idOfName(rawName);
        if (mobId < 0) {
            hud(mc, "&cUnknown mob: &f" + rawName);
            hud(mc, "&7Available: " + String.join(", ", MobRegistry.allNames()));
            return;
        }

        int idx = 1;
        int requested = 1;
        if (idx < args.length && isInt(args[idx])) {
            try {
                requested = clampInt(Integer.parseInt(args[idx]), 1, 64);
            } catch (Exception ignored) {
            }
            idx++;
        }

        float px = mc.player.x, py = mc.player.y, pz = mc.player.z;

        if (idx + 2 < args.length) {
            Float fx = parseCoord(args[idx], px);
            Float fy = parseCoord(args[idx + 1], py);
            Float fz = parseCoord(args[idx + 2], pz);
            if (fx != null && fy != null && fz != null) {
                px = fx;
                py = fy;
                pz = fz;
                idx += 3;
            }
        }

        String mountName = (idx < args.length ? safeAscii(args[idx]).toLowerCase(Locale.ROOT) : null);
        if (mountName != null && isDisallowedName(mountName)) {
            hud(mc, "&cYou cannot spawn this entity type as a mount: &f" + mountName);
            mountName = null;
        }
        short mountId = (mountName != null && !mountName.isEmpty()) ? MobRegistry.idOfName(mountName) : -1;

        int nearby = countNearbyMobs(mc);
        int roomNearby = Math.max(0, NEARBY_CAP - nearby);
        if (roomNearby <= 0) {
            hud(mc, "&eToo many mobs nearby (" + nearby + "/" + NEARBY_CAP + ").");
            return;
        }

        int allowedByBucket = consumeTokens(requested);
        int toSpawn = Math.min(requested, Math.min(allowedByBucket, Math.min(roomNearby, PER_CALL_CAP)));
        if (toSpawn <= 0) {
            hud(mc, "&eSpawn limit reached for now.");
            return;
        }

        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            float ox = (mc.renderer.random.nextFloat() - 0.5f) * 2.0f;
            float oz = (mc.renderer.random.nextFloat() - 0.5f) * 2.0f;

            Mob mob = MobRegistry.create(mobId, mc.level, px + ox, py, pz + oz);

            // --- NEW: renderer OR legacy modelName check ---
            boolean hasRenderer = (net.classicremastered.minecraft.render.entity.RenderManager
                    .getRenderer(mob) != null);
            boolean hasLegacy = (mob != null && mob.modelName != null && !mob.modelName.isEmpty());

            if (mob == null || mob.getRegistryId() < 0 || (!hasRenderer && !hasLegacy)) {
                hud(mc, "&eSkipped invalid mob: &f" + rawName);
                continue;
            }

            mc.level.addEntity(mob);

            if (mountId >= 0) {
                Mob mount = MobRegistry.create(mountId, mc.level, px + ox, py, pz + oz);
                boolean mountRenderer = (net.classicremastered.minecraft.render.entity.RenderManager
                        .getRenderer(mount) != null);
                boolean mountLegacy = (mount != null && mount.modelName != null && !mount.modelName.isEmpty());

                if (mount != null && mount.getRegistryId() >= 0 && (mountRenderer || mountLegacy)) {
                    mc.level.addEntity(mount);
                    mob.mount(mount);
                    mob.xo = mount.xo;
                    mob.yo = mount.yo + mount.bbHeight * 0.75f;
                    mob.zo = mount.zo;
                    mob.x = mount.x;
                    mob.y = mount.y + mount.bbHeight * 0.75f;
                    mob.z = mount.z;
                    mob.yRot = mount.yRot;
                    mob.yRotO = mount.yRotO;
                } else {
                    hud(mc, "&eSkipped invalid mount: &f" + mountName);
                }
            }
            spawned++;
        }

        if (spawned == requested) {
            hud(mc, "&aSpawned &f" + spawned + "&a x &f" + rawName
                    + (mountName != null ? "&a riding &f" + mountName : "") + "&a.");
        } else {
            hud(mc, "&aSpawned &f" + spawned + "&a of &f" + requested + "&a x &f" + rawName
                    + (mountName != null ? "&a riding &f" + mountName : "") + "&a (&7nearby " + nearby + "/"
                    + NEARBY_CAP + ").");
        }
    }

    private int countNearbyMobs(Minecraft mc) {
        Player p = mc.player;
        float r = NEARBY_RADIUS;
        AABB box = p.bb.expand(r, r, r);
        @SuppressWarnings("rawtypes")
        List list = mc.level.blockMap.getEntities(p, box);
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof Mob)
                count++;
        }
        return count;
    }

    // ---------- helpers ----------
    private static void hud(Minecraft mc, String msg) {
        if (mc != null && mc.hud != null)
            mc.hud.addChat(msg);
    }

    private static int clampInt(int v, int lo, int hi) {
        return (v < lo ? lo : (v > hi ? hi : v));
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Float parseCoord(String token, float origin) {
        if (token == null || token.isEmpty())
            return null;
        token = token.trim();
        if (token.charAt(0) == '~') {
            if (token.length() == 1)
                return origin;
            try {
                return origin + Float.parseFloat(token.substring(1));
            } catch (Exception e) {
                return null;
            }
        } else {
            try {
                return Float.parseFloat(token);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
