package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.player.Player;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;
import java.util.*;

public final class LevelRenderer {
    public Level level;
    public TextureManager textureManager;
    public int listId;
    public IntBuffer buffer = BufferUtils.createIntBuffer(65536);
    public List<Chunk> chunks = new ArrayList<>();
    private Chunk[] loadQueue;
    public Chunk[] chunkCache;
    private int xChunks, yChunks, zChunks;
    private int baseListId, baseListCount;
    public Minecraft minecraft;
    private int[] chunkDataCache = new int[65536];
    public int ticks = 0;
    private float lastLoadX = -9999, lastLoadY = -9999, lastLoadZ = -9999;
    public float cracks;
    public boolean waterReady = false;
    public final Map<Long, Chunk> infiniteCache = new HashMap<>();
    private final List<Chunk> renderWindow = new ArrayList<>();
    private int windowX = Integer.MIN_VALUE, windowZ = Integer.MIN_VALUE, windowView = -1;
    private static final long BUILD_BUDGET_NANOS = 3_000_000L;

    private static long key3(int cx, int sy, int cz) {
        return ((((long) cx) << 32) ^ (cz & 0xffffffffL)) ^ (((long) sy) << 56);
    }

    public LevelRenderer(Minecraft mc, TextureManager tex) {
        minecraft = mc;
        textureManager = tex;
        listId = GL11.glGenLists(2);
    }

    private boolean isInfiniteLevel() {
        return level instanceof LevelInfiniteFlat || level instanceof LevelInfiniteTerrain;
    }

    public void markDirty(int cx, int sy, int cz) {
        markDirty(infiniteCache.get(key3(cx, sy, cz)));
    }

    public void markDirty(Chunk c) {
        if (c == null) return;
        c.setAllDirty();
        if (!c.inBuildQueue) {
            c.inBuildQueue = true;
            c.loaded = true;
            chunks.add(c);
        }
    }

    /** One shared, time-limited rebuild batch per rendered view. */
    public void updateSomeChunks() {
        long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
        Player player = minecraft == null ? null : minecraft.player;
        for (int built = 0; built < 8 && !chunks.isEmpty(); built++) {
            int best = 0;
            if (player != null) {
                for (int i = 1; i < chunks.size(); i++) {
                    Chunk candidate = chunks.get(i), previous = chunks.get(best);
                    if ((candidate.visible && !previous.visible)
                            || (candidate.visible == previous.visible
                                    && candidate.distanceSquared(player)
                                            < previous.distanceSquared(player))) best = i;
                }
            }
            Chunk c = chunks.get(best);
            int last = chunks.size() - 1;
            chunks.set(best, chunks.get(last));
            chunks.remove(last);
            c.inBuildQueue = false;
            c.loaded = false;
            if (c.dirty[0] || c.dirty[1]) c.update();
            // A single mesh is indivisible; do not start another once the budget is spent.
            if (System.nanoTime() >= deadline) break;
        }
    }

    public void onLightingChanged() {
        if (isInfiniteLevel()) {
            for (Chunk c : infiniteCache.values()) markDirty(c);
        } else if (chunkCache != null) {
            for (Chunk c : chunkCache) markDirty(c);
        }
    }

    public final void refresh() {
        for (Chunk c : infiniteCache.values()) c.disposeLists();
        infiniteCache.clear();
        renderWindow.clear();
        windowX = windowZ = Integer.MIN_VALUE;
        windowView = -1;
        if (chunkCache != null) for (Chunk c : chunkCache) c.dispose();
        if (baseListId != 0 && baseListCount > 0) GL11.glDeleteLists(baseListId, baseListCount);
        baseListId = baseListCount = 0;
        chunkCache = loadQueue = null;
        chunks.clear();
        waterReady = false;
        buffer.clear().limit(0);
        lastLoadX = lastLoadY = lastLoadZ = -9999;
        xChunks = yChunks = zChunks = 0;
        if (level == null || isInfiniteLevel()) return;
        xChunks = (level.width + 15) / 16;
        yChunks = (level.depth + 15) / 16;
        zChunks = (level.height + 15) / 16;
        int count = xChunks * yChunks * zChunks;
        baseListCount = count * 2;
        if (baseListCount > 0) baseListId = GL11.glGenLists(baseListCount);
        chunkCache = new Chunk[count];
        loadQueue = new Chunk[count];
        for (int cx = 0; cx < xChunks; cx++)
            for (int cy = 0; cy < yChunks; cy++)
                for (int cz = 0; cz < zChunks; cz++) {
                    int idx = (cz * yChunks + cy) * xChunks + cx;
                    Chunk c = new Chunk(level, cx << 4, cy << 4, cz << 4, 16, baseListId + idx * 2);
                    chunkCache[idx] = loadQueue[idx] = c;
                    markDirty(c);
                }
    }

    private void prepareInfiniteWindow(Player player) {
        int cx = (int) Math.floor(player.x / 16.0), cz = (int) Math.floor(player.z / 16.0);
        int distance =
                minecraft != null && minecraft.settings != null
                        ? minecraft.settings.viewDistance
                        : 0;
        int view =
                switch (distance) {
                    case 0 -> 8;
                    case 1 -> 6;
                    case 2 -> 4;
                    default -> 2;
                };
        if (cx == windowX && cz == windowZ && view == windowView) return;
        windowX = cx;
        windowZ = cz;
        windowView = view;
        // Keep a small reuse margin while bounding GPU meshes during long journeys.
        Set<Chunk> removed = new HashSet<>();
        Iterator<Chunk> iterator = infiniteCache.values().iterator();
        while (iterator.hasNext()) {
            Chunk c = iterator.next();
            if (c.outsideColumns(cx, cz, view + 2)) {
                removed.add(c);
                iterator.remove();
                c.disposeLists();
            }
        }
        chunks.removeIf(removed::contains);
        renderWindow.clear();
        if (minecraft != null && minecraft.settings != null && minecraft.settings.multithreaded) {
            if (level instanceof LevelInfiniteTerrain terrain)
                terrain.chunks().prefetch(cx, cz, view);
            else if (level instanceof LevelInfiniteFlat flat) flat.chunks.prefetch(cx, cz, view);
        }
        int slices = (level.depth + 15) >> 4;
        for (int ring = 0; ring <= view; ring++)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (int sy = 0; sy < slices; sy++) {
                        long key = key3(cx + dx, sy, cz + dz);
                        Chunk c = infiniteCache.get(key);
                        if (c == null) {
                            c =
                                    new Chunk(
                                            level,
                                            (cx + dx) << 4,
                                            sy << 4,
                                            (cz + dz) << 4,
                                            16,
                                            GL11.glGenLists(2));
                            infiniteCache.put(key, c);
                            markDirty(c);
                        }
                        renderWindow.add(c);
                    }
                }
    }

    public final int sortChunks(Player player, int pass, float partial) {
        long start = System.nanoTime();
        try {
            boolean infinite = isInfiniteLevel();
            if (pass == 0) {
                if (infinite) prepareInfiniteWindow(player);
                else {
                    float dx = player.x - lastLoadX,
                            dy = player.y - lastLoadY,
                            dz = player.z - lastLoadZ;
                    if (dx * dx + dy * dy + dz * dz > 64) {
                        lastLoadX = player.x;
                        lastLoadY = player.y;
                        lastLoadZ = player.z;
                        Arrays.sort(loadQueue, new ChunkDistanceComparator(player));
                    }
                }
                Frustrum frustum = FrustrumImpl.update();
                if (infinite) for (Chunk c : renderWindow) c.clip(frustum);
                else for (Chunk c : loadQueue) c.clip(frustum);
                updateSomeChunks();
            }
            int len = 0;
            if (infinite) {
                ensureDrawCapacity(renderWindow.size());
                for (Chunk c : renderWindow)
                    if (c.visible) len = c.appendLists(chunkDataCache, len, pass);
            } else if (loadQueue != null) {
                ensureDrawCapacity(loadQueue.length);
                for (Chunk c : loadQueue)
                    if (c.visible) len = c.appendLists(chunkDataCache, len, pass);
            }
            buffer.clear();
            if (len > 0) {
                buffer.put(chunkDataCache, 0, len).flip();
                GL11.glBindTexture(
                        GL11.GL_TEXTURE_2D,
                        textureManager.load(
                                infinite && pass == 1
                                        ? "/water.png"
                                        : net.classicremastered.minecraft.render.BlockTextureAtlas
                                                .RESOURCE));
                GL11.glCallLists(buffer);
            }
            if (pass == 0) renderPistonMotion(partial);
            if (pass == 0)
                net.classicremastered.minecraft.level.ChestController.render(
                        level, textureManager, FrustrumImpl.update());
            return len;
        } finally {
            net.classicremastered.minecraft.util.Debug.recordSortChunks(System.nanoTime() - start);
        }
    }

    private void renderPistonMotion(float partial) {
        var motions = level.redstone().motions();
        if (motions.isEmpty()) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureManager.load(BlockTextureAtlas.RESOURCE));
        ShapeRenderer shape = ShapeRenderer.instance;
        for (var motion : motions) {
            var a = motion.from();
            var b = motion.to();
            float remain = 1 - Math.min(1, (level.tickCount - motion.started() + partial) / 2);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(
                        (a.x() - b.x()) * remain,
                        (a.y() - b.y()) * remain,
                        (a.z() - b.z()) * remain);
                shape.begin();
                if (motion.block() == Block.PISTON_HEAD.id) {
                    net.classicremastered.minecraft.level.tile.BlockModels.piston(
                            shape,
                            b.x(),
                            b.y(),
                            b.z(),
                            motion.facing(),
                            true,
                            true,
                            motion.sticky(),
                            level.getRenderBrightness(b.x(), b.y(), b.z()));
                } else {
                    Block.blocks[motion.block()].render(level, b.x(), b.y(), b.z(), shape);
                }
                shape.end();
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private void ensureDrawCapacity(int count) {
        if (count <= chunkDataCache.length) return;
        int capacity = Math.max(count, chunkDataCache.length * 2);
        chunkDataCache = new int[capacity];
        buffer = BufferUtils.createIntBuffer(capacity);
    }

    public final void queueChunks(int x0, int y0, int z0, int x1, int y1, int z1) {
        int cx0 = Math.floorDiv(x0, 16),
                cy0 = Math.max(0, Math.floorDiv(y0, 16)),
                cz0 = Math.floorDiv(z0, 16);
        int cx1 = Math.floorDiv(x1, 16),
                cy1 = Math.min((level.depth - 1) / 16, Math.floorDiv(y1, 16)),
                cz1 = Math.floorDiv(z1, 16);
        boolean infinite = isInfiniteLevel();
        if (!infinite) {
            cx0 = Math.max(0, cx0);
            cz0 = Math.max(0, cz0);
            cx1 = Math.min(xChunks - 1, cx1);
            cz1 = Math.min(zChunks - 1, cz1);
        }
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cy = cy0; cy <= cy1; cy++)
                for (int cz = cz0; cz <= cz1; cz++) {
                    if (infinite) markDirty(cx, cy, cz);
                    else markDirty(chunkCache[(cz * yChunks + cy) * xChunks + cx]);
                }
    }

    public void onTimeOfDayMaybeChanged() {}

    public void renderEntities(float partial) {
        if (level == null) return;
        for (Entity e : level.entityList) {
            if (!e.removed) e.render(textureManager, partial);
        }

        // Draw gravity gun and physics gun beams for grabbed mobs
        for (Entity e : level.entityList) {
            if (e instanceof Player) {
                Player p = (Player) e;
                HeldItemRenderer.renderWorld(p, partial);
                int selId = p.inventory.getSelected();
                if (selId >= 256 && (selId - 256) == 11) {
                    net.classicremastered.minecraft.mob.Mob grabbed =
                            net.classicremastered.minecraft.level.itemstack.GravityGunItem
                                    .getGrabbedMob(p);
                    if (grabbed != null && !grabbed.removed) {
                        net.classicremastered.minecraft.level.itemstack.GravityGunItem
                                .renderMobBeam(p, grabbed, textureManager, partial);
                    }
                }
                if (selId >= 256 && (selId - 256) == 10) {
                    net.classicremastered.minecraft.mob.Mob grabbed =
                            net.classicremastered.minecraft.level.itemstack.PhysicsGunItem
                                    .getGrabbed(p);
                    if (grabbed != null && !grabbed.removed) {
                        net.classicremastered.minecraft.level.itemstack.PhysicsGunItem
                                .renderMobBeam(p, grabbed, textureManager, partial);
                    }
                }
            }
        }
    }
}
