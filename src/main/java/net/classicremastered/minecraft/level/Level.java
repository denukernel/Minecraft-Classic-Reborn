package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.IronGolem;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.LevelRenderer;
import net.classicremastered.minecraft.sound.AudioInfo;
import net.classicremastered.minecraft.sound.EntitySoundPos;
import net.classicremastered.minecraft.sound.LevelSoundPos;
import net.classicremastered.util.MathHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Level implements Serializable {

    public static final long serialVersionUID = 0L;
    public int width;
    public int height;
    public int depth;
    public byte[] blocks;
    public String name;
    public String creator;
    public long createTime;
    public int xSpawn;
    public int ySpawn;
    public int zSpawn;
    public float rotSpawn;
    protected transient ArrayList listeners = new ArrayList();
    protected transient int[] blockers;
    public transient Random random = new Random();
    private transient int randId;
    private transient ArrayList tickList;
    public BlockMap blockMap;
    private boolean networkMode;
    public transient Minecraft minecraft;
    public boolean creativeMode;
    public int waterLevel;
    public int skyColor; // --- Day/Night cycle ---
    public int dayLength = 24000; // ticks per full day (~20 min at 20 TPS)
    public int timeOfDay = 2000; // 8:00 AM start
    public boolean doDayNightCycle = true;
    private static final int SKY_DAY = 0x66B1FF; // light blue
    private static final int SKY_NIGHT = 0x020924; // deep navy
    private static final int FOG_DAY = 0xC0D8FF; // pale blue
    private static final int FOG_NIGHT = 0x0E1528; // dark blue
    private static final int CLOUD_DAY = 0xFFFFFF; // white
    private static final int CLOUD_NGT = 0x444444; // dim grey
    public int fogColor;
    public int cloudColor;
    int unprocessed;
    public int tickCount;
    public Entity player;
    public transient ParticleManager particleEngine;
    public transient Object font;
    public boolean growTrees;
    public int lastTimeOfDay = 0; // previous tick’s time
    public float renderPartial = 0f; // set each frame before rendering
    // === Apocalypse overrides ===
    public boolean forceRainbowSky = false;
    public transient LightEngine lightEngine = new LightEngine(this);
    public final LevelHelper helper;

    public Level() {
        this.randId = this.random.nextInt();
        this.tickList = new ArrayList();
        this.networkMode = false;
        this.unprocessed = 0;
        this.tickCount = 0;
        this.growTrees = false;
        this.helper = new LevelHelper(this);

        // --- ensure visuals match the 8:00 AM start ---
        this.lastTimeOfDay = this.timeOfDay; // avoid a big first-frame wrap
        updateDayNightColorsSmooth(); // set sky/fog/cloud to current time
    }

    public float getCelestialAngle(float partial) {
        int t = (int) ((timeOfDay + (partial < 0 ? 0 : partial)) % dayLength);
        return (float) t / (float) dayLength; // 0..1
    }

    /** Vanilla-ish daylight: 0.2 (midnight) .. 1.0 (noon) with a cosine curve. */
    public float getDaylightBrightness() {
        float a = getCelestialAngle(0.0f) * (float) Math.PI * 2.0f;
        float c = MathHelper.cos(a) * 0.5f + 0.5f; // 0..1, peaked at noon
        return 0.2f + 0.8f * c; // 0.2..1.0
    }

    /** Normalized daylight 0..1 (0 at midnight, 1 at noon). */
    private float daylight01() {
        float dl = (getDaylightBrightness() - 0.2f) / 0.8f;
        if (dl < 0f)
            dl = 0f;
        if (dl > 1f)
            dl = 1f;
        return dl;
    }

    /** For star alpha: dark nights, zero at day. */
    public float getStarBrightness() {
        float d = 1.0f - daylight01();
        return d * d; // softer transition
    }

    private static int lerpColor(int a, int b, float t) {
        if (t < 0f)
            t = 0f;
        if (t > 1f)
            t = 1f;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int rr = (int) (ar + (br - ar) * t + 0.5f);
        int rg = (int) (ag + (bg - ag) * t + 0.5f);
        int rb = (int) (ab + (bb - ab) * t + 0.5f);
        return (rr << 16) | (rg << 8) | rb;
    }

    /** Update sky/fog/cloud colors each tick from timeOfDay. */
    private void updateDayNightColors() {
        float t = daylight01();
        // Base lerp day <-> night
        this.skyColor = lerpColor(SKY_NIGHT, SKY_DAY, t);
        this.fogColor = lerpColor(FOG_NIGHT, FOG_DAY, t);
        this.cloudColor = lerpColor(CLOUD_NGT, CLOUD_DAY, t);

        // Optional warm tint at sunrise/sunset (~6:00 and ~18:00)
        // float ca = getCelestialAngle(0f);
        // float tw = Math.max(0f, 1f - Math.abs(ca - 0.25f) / 0.06f); // sunrise
        // tw = Math.max(tw, Math.max(0f, 1f - Math.abs(ca - 0.75f) / 0.06f)); // sunset
        // int warmSky = 0xFF7A40;
        // this.skyColor = lerpColor(this.skyColor, warmSky, Math.min(0.35f, tw *
        // 0.35f));
    }
    // --- end Day/Night helpers ---

    public void initTransient() {
        if (this.blocks == null) {
            throw new RuntimeException("The level is corrupt!");
        } else {
            this.listeners = new ArrayList();
            this.blockers = new int[this.width * this.height];
            Arrays.fill(this.blockers, this.depth);
            this.calcLightDepths(0, 0, this.width, this.height);
            this.random = new Random();
            this.randId = this.random.nextInt();
            this.tickList = new ArrayList();
            if (this.waterLevel == 0) {
                this.waterLevel = this.depth / 2;
            }

            if (this.skyColor == 0) {
                this.skyColor = 10079487;
            }

            if (this.fogColor == 0) {
                this.fogColor = 16777215;
            }

            if (this.cloudColor == 0) {
                this.cloudColor = 16777215;
            }

            if (this.xSpawn == 0 && this.ySpawn == 0 && this.zSpawn == 0) {
                this.findSpawn();
            }

            if (this.blockMap == null) {
                this.blockMap = new BlockMap(this.width, this.depth, this.height);
            }
            // At the very end of initTransient()
            // === Respawn saved mobs once runtime structures exist (registry-based) ===
            if (!this.networkMode && this.pendingEntities != null && this.blockMap != null) {
                net.classicremastered.minecraft.mob.MobRegistry.bootstrapDefaults(); // ensure registry ready

                for (int i = 0; i < this.pendingEntities.size(); i++) {
                    SavedMob s = this.pendingEntities.get(i);
                    net.classicremastered.minecraft.mob.Mob m = net.classicremastered.minecraft.mob.MobRegistry
                            .create(s.id, this, s.x, s.y, s.z);
                    if (m == null)
                        continue; // SAFETY: refuse unregistered/missing
                    m.yRot = s.yRot;
                    m.xRot = s.xRot;
                    try {
                        java.lang.reflect.Field hf = m.getClass().getField("health");
                        if (hf.getType() == int.class)
                            hf.setInt(m, s.health);
                    } catch (Throwable ignored) {
                    }
                    this.addEntity(m);
                }
                this.pendingEntities = null; // spawn only once
            }
        }
    }

    public void setData(int var1, int var2, int var3, byte[] var4) {
        this.width = var1;
        this.height = var3;
        this.depth = var2;
        this.blocks = var4;
        this.blockers = new int[var1 * var3];
        Arrays.fill(this.blockers, this.depth);
        this.calcLightDepths(0, 0, var1, var3);

        for (var1 = 0; var1 < this.listeners.size(); ++var1) {
            ((LevelRenderer) this.listeners.get(var1)).refresh();
        }

        this.tickList.clear();
        this.findSpawn();
        this.initTransient();
        System.gc();
    }

    public void findSpawn() {
        Random var1 = new Random();
        int var2 = 0;

        int var3;
        int var4;
        int var5;
        do {
            ++var2;
            var3 = var1.nextInt(this.width / 2) + this.width / 4;
            var4 = var1.nextInt(this.height / 2) + this.height / 4;
            var5 = this.getHighestTile(var3, var4) + 1;
            if (var2 == 10000) {
                this.xSpawn = var3;
                this.ySpawn = -100;
                this.zSpawn = var4;
                return;
            }
        } while ((float) var5 <= this.getWaterLevel());

        this.xSpawn = var3;
        this.ySpawn = var5;
        this.zSpawn = var4;
    }

    public void calcLightDepths(int var1, int var2, int var3, int var4) {
        for (int var5 = var1; var5 < var1 + var3; ++var5) {
            for (int var6 = var2; var6 < var2 + var4; ++var6) {
                int var7 = this.blockers[var5 + var6 * this.width];

                int var8;
                for (var8 = this.depth - 1; var8 > 0 && !this.isLightBlocker(var5, var8, var6); --var8) {
                    ;
                }

                this.blockers[var5 + var6 * this.width] = var8;
                if (var7 != var8) {
                    int var9 = var7 < var8 ? var7 : var8;
                    var7 = var7 > var8 ? var7 : var8;

                    for (var8 = 0; var8 < this.listeners.size(); ++var8) {
                        ((LevelRenderer) this.listeners.get(var8)).queueChunks(var5 - 1, var9 - 1, var6 - 1, var5 + 1,
                                var7 + 1, var6 + 1);
                    }
                }
            }
        }

    }

    public void addListener(LevelRenderer var1) {
        this.listeners.add(var1);
    }

    public void removeListener(LevelRenderer var1) {
        this.listeners.remove(var1);
    }

    public boolean isVoidY(int y) {
        return y < 0;
    }

    public boolean isLightBlocker(int var1, int var2, int var3) {
        Block var4;
        return (var4 = Block.blocks[this.getTile(var1, var2, var3)]) == null ? false : var4.isOpaque();
    }

    public ArrayList getCubes(AABB box) {
        ArrayList out = new ArrayList();

        int x0 = (int) Math.floor(box.x0);
        int x1 = (int) Math.floor(box.x1);
        int y0 = (int) Math.floor(box.y0);
        int y1 = (int) Math.floor(box.y1);
        int z0 = (int) Math.floor(box.z0);
        int z1 = (int) Math.floor(box.z1);

        for (int x = x0; x <= x1; ++x) {
            for (int y = y0; y <= y1; ++y) {
                for (int z = z0; z <= z1; ++z) {
                    AABB bb;

                    // --- Classic behavior: bedrock outside borders or below 0 ---
                    if (y < 0 || x < 0 || z < 0 || x >= this.width || z >= this.height) {
                        bb = Block.BEDROCK.getCollisionBox(x, y, z);
                        if (bb != null && box.intersectsInner(bb)) {
                            out.add(bb);
                        }
                        continue;
                    }

                    if (y >= this.depth)
                        continue; // above world = air

                    int id = getTile(x, y, z);
                    if (id == 0)
                        continue;
                    Block b = Block.blocks[id];
                    if (b == null)
                        continue;

                    bb = b.getCollisionBox(x, y, z);
                    if (bb != null && box.intersectsInner(bb)) {
                        out.add(bb);
                    }
                }
            }
        }
        return out;
    }

    public void swap(int var1, int var2, int var3, int var4, int var5, int var6) {
        if (!this.networkMode) {
            int var7 = this.getTile(var1, var2, var3);
            int var8 = this.getTile(var4, var5, var6);
            this.setTileNoNeighborChange(var1, var2, var3, var8);
            this.setTileNoNeighborChange(var4, var5, var6, var7);
            this.updateNeighborsAt(var1, var2, var3, var8);
            this.updateNeighborsAt(var4, var5, var6, var7);
        }
    }

    public boolean setTileNoNeighborChange(int var1, int var2, int var3, int var4) {
        return this.networkMode ? false : this.netSetTileNoNeighborChange(var1, var2, var3, var4);
    }

    public boolean netSetTileNoNeighborChange(int var1, int var2, int var3, int var4) {
        if (var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height) {
            if (var4 == this.blocks[(var2 * this.height + var3) * this.width + var1]) {
                return false;
            } else {
                if (var4 == 0 && (var1 == 0 || var3 == 0 || var1 == this.width - 1 || var3 == this.height - 1)
                        && (float) var2 >= this.getGroundLevel() && (float) var2 < this.getWaterLevel()) {
                    var4 = Block.WATER.id;
                }

                int idx = (var2 * this.height + var3) * this.width + var1;
                int prevId = this.blocks[idx] & 0xFF;
                this.blocks[idx] = (byte) var4;
                if (prevId != 0) {
                    Block.blocks[prevId].onRemoved(this, var1, var2, var3);
                }
                if (var4 != 0) {
                    Block.blocks[var4].onAdded(this, var1, var2, var3);
                }

                if (var4 != 0) {
                    Block.blocks[var4].onAdded(this, var1, var2, var3);
                }

                this.calcLightDepths(var1, var3, 1, 1);

                for (var4 = 0; var4 < this.listeners.size(); ++var4) {
                    ((LevelRenderer) this.listeners.get(var4)).queueChunks(var1 - 1, var2 - 1, var3 - 1, var1 + 1,
                            var2 + 1, var3 + 1);
                }

                return true;
            }
        } else {
            return false;
        }
    }

    public boolean setTile(int x, int y, int z, int id) {
        if (this.networkMode) {
            return false;
        } else if (this.setTileNoNeighborChange(x, y, z, id)) {
            this.updateNeighborsAt(x, y, z, id);

            // === Iron Golem creation check ===
            if (id == Block.PUMPKIN.id) {
                trySpawnIronGolem(x, y, z);
            }

            return true;
        } else {
            return false;
        }
    }

    // added helper
    private void trySpawnIronGolem(int x, int y, int z) {
        // check block below is iron
        if (getTile(x, y - 1, z) != Block.IRON_BLOCK.id)
            return;

        // check body below
        if (getTile(x, y - 2, z) != Block.IRON_BLOCK.id)
            return;

        // check arms (X-axis)
        boolean xArms = getTile(x - 1, y - 1, z) == Block.IRON_BLOCK.id
                && getTile(x + 1, y - 1, z) == Block.IRON_BLOCK.id;

        // check arms (Z-axis)
        boolean zArms = getTile(x, y - 1, z - 1) == Block.IRON_BLOCK.id
                && getTile(x, y - 1, z + 1) == Block.IRON_BLOCK.id;

        if (!xArms && !zArms)
            return;

        // Clear structure
        setTileNoUpdate(x, y, z, 0); // pumpkin
        setTileNoUpdate(x, y - 1, z, 0); // chest iron
        setTileNoUpdate(x, y - 2, z, 0); // base iron
        if (xArms) {
            setTileNoUpdate(x - 1, y - 1, z, 0);
            setTileNoUpdate(x + 1, y - 1, z, 0);
        }
        if (zArms) {
            setTileNoUpdate(x, y - 1, z - 1, 0);
            setTileNoUpdate(x, y - 1, z + 1, 0);
        }

        // Spawn golem
        IronGolem g = new IronGolem(this, x + 0.5f, y - 1, z + 0.5f);
        g.builtByPlayer = true; // mark as defender
        this.addEntity(g);

        // Sound / particles
        playSound("random/anvil_use", x + 0.5f, y, z + 0.5f, 1.0f, 1.0f);
        if (this.particleEngine != null) {
            for (int i = 0; i < 20; i++) {
                double px = x + 0.5 + (this.random.nextDouble() - 0.5);
                double py = y + 0.5 + this.random.nextDouble();
                double pz = z + 0.5 + (this.random.nextDouble() - 0.5);
                this.particleEngine.spawnParticle(new net.classicremastered.minecraft.particle.TerrainParticle(this,
                        (float) px, (float) py, (float) pz, 0, 0.1f, 0, Block.IRON_BLOCK));
            }
        }
    }

    public boolean netSetTile(int var1, int var2, int var3, int var4) {
        if (this.netSetTileNoNeighborChange(var1, var2, var3, var4)) {
            this.updateNeighborsAt(var1, var2, var3, var4);
            return true;
        } else {
            return false;
        }
    }

    public void updateNeighborsAt(int var1, int var2, int var3, int var4) {
        this.updateTile(var1 - 1, var2, var3, var4);
        this.updateTile(var1 + 1, var2, var3, var4);
        this.updateTile(var1, var2 - 1, var3, var4);
        this.updateTile(var1, var2 + 1, var3, var4);
        this.updateTile(var1, var2, var3 - 1, var4);
        this.updateTile(var1, var2, var3 + 1, var4);
    }

    public boolean setTileNoUpdate(int var1, int var2, int var3, int var4) {
        if (var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height) {
            if (var4 == this.blocks[(var2 * this.height + var3) * this.width + var1]) {
                return false;
            } else {
                this.blocks[(var2 * this.height + var3) * this.width + var1] = (byte) var4;
                return true;
            }
        } else {
            return false;
        }
    }

    private void updateTile(int var1, int var2, int var3, int var4) {
        if (var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height) {
            Block var5;
            int id = this.blocks[(var2 * this.height + var3) * this.width + var1] & 0xFF;
            if ((var5 = Block.blocks[id]) != null) {
                var5.onNeighborChange(this, var1, var2, var3, var4);
            }

        }
    }

    public boolean isLit(int var1, int var2, int var3) {
        return var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height
                ? var2 >= this.blockers[var1 + var3 * this.width]
                : true;
    }

    public int getTile(int var1, int var2, int var3) {
        return var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height
                ? this.blocks[(var2 * this.height + var3) * this.width + var1] & 255
                : 0;
    }

    public boolean isSolidTile(int var1, int var2, int var3) {
        Block var4;
        return (var4 = Block.blocks[this.getTile(var1, var2, var3)]) == null ? false : var4.isSolid();
    }

    public void tickEntities() {
        this.blockMap.tickAll();
    }

// Level fields
    private float lastLX, lastLY, lastLZ;
    private int lastLightTick;

// Call this instead of updateLightingContext() every tick
    public void maybeUpdateLightingContext(int tick) {
        if (player == null) {
            updateLightingContext();
            return;
        }

        float dx = Math.abs(player.x - lastLX);
        float dy = Math.abs(player.y - lastLY);
        float dz = Math.abs(player.z - lastLZ);
        boolean moved = (dx + dy + dz) > 0.5f; // tweak threshold
        boolean periodic = (tick - lastLightTick) >= 10; // every ~0.5s at 20 TPS

        if (moved || periodic) {
            updateLightingContext();
            lastLX = player.x;
            lastLY = player.y;
            lastLZ = player.z;
            lastLightTick = tick;
        }
    }

    public void tick() {
        ++this.tickCount;

        // Update per-tick lighting context (cached)
        maybeUpdateLightingContext(tickCount);

        // === Day/Night cycle & light engine update ===
        if (doDayNightCycle) {
            tickTime();
            updateDayNightColorsSmooth();

            // Ask LightEngine if skylight changed enough to rebuild chunks
            if (lightEngine.shouldRefreshChunks()) {
                for (int i = 0; i < listeners.size(); i++) {
                    ((net.classicremastered.minecraft.render.LevelRenderer) listeners.get(i)).refresh();
                }
            }
        }

        int var1 = 1;
        int var2;
        for (var2 = 1; 1 << var1 < this.width; ++var1) {
        }
        while (1 << var2 < this.height) {
            ++var2;
        }

        // ---- Scheduled block ticks (RAM safe with cap) ----
        int size = this.tickList.size();
        int processed = 0;
        int maxPerTick = 500; // cap to avoid runaway
        for (int i = 0; i < size && processed < maxPerTick; ++i) {
            NextTickListEntry e = (NextTickListEntry) this.tickList.remove(0);
            if (e.ticks > 0) {
                --e.ticks;
                this.tickList.add(e);
            } else {
                if (this.isInBounds(e.x, e.y, e.z)) {
                    int id = this.blocks[(e.y * this.height + e.z) * this.width + e.x] & 0xFF;
                    if (id == e.block && id > 0) {
                        boolean isLiquid = (id == Block.WATER.id || id == Block.STATIONARY_WATER.id
                                || id == Block.LAVA.id || id == Block.STATIONARY_LAVA.id);

                        if (isLiquid || (this.tickCount % 5 == 0)) {
                            Block.blocks[id].update(this, e.x, e.y, e.z, this.random);
                        } else {
                            // reschedule non-liquid blocks until next batch
                            e.ticks = 1;
                            this.tickList.add(e);
                        }
                    }
                }
            }
            processed++;
        }

        // ---- Random block updates ----
        this.unprocessed += this.width * this.height * this.depth;
        int updates = this.unprocessed / 200;
        this.unprocessed -= updates * 200;
        for (int i = 0; i < updates; ++i) {
            this.randId = this.randId * 3 + 1013904223;
            int r = this.randId >> 2;
            int x = r & (this.width - 1);
            int z = (r >> var1) & (this.height - 1);
            int y = (r >> (var1 + var2)) & (this.depth - 1);
            int idx = (y * this.height + z) * this.width + x;
            int id = this.blocks[idx] & 0xFF;
            if (Block.physics[id]) {
                Block.blocks[id].update(this, x, y, z, this.random);
            }
        }

        // ---- Despawn pass ----
        if (!this.isNetworkMode() && this.blockMap != null && this.blockMap.all != null) {
            Player p = (this.player instanceof Player) ? (Player) this.player : null;
            for (int i = this.blockMap.all.size() - 1; i >= 0; --i) {
                Object obj = this.blockMap.all.get(i);
                if (!(obj instanceof net.classicremastered.minecraft.mob.Mob))
                    continue;
                net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) obj;
                if (m.riding != null || m.rider != null)
                    continue;
                try {
                    if (m instanceof net.classicremastered.minecraft.mob.Villager)
                        continue;
                    java.lang.reflect.Field f = m.getClass().getField("persistent");
                    if (f.getType() == boolean.class && f.getBoolean(m))
                        continue;
                } catch (Throwable ignored) {
                }
                if (m.removed || m.health <= 0)
                    continue;
                if (m.tickCount < 20 * 10)
                    continue; // must live 10s
                if (p != null) {
                    float dx = m.x - p.x, dy = m.y - p.y, dz = m.z - p.z;
                    if (dx * dx + dy * dy + dz * dz > 40f * 40f) {
                        m.remove();
                    }
                }
            }
        }

        // ---- Universal mob spawning (RAM safe throttle) ----
        // Spawn whenever this level is authoritative (not a network-client/proxy).
        if (!this.isNetworkMode()) {
            if (this.tickCount % 20 == 0) { // once per second
                MobSpawner spawner = new MobSpawner(this);
                spawner.spawn(6, this.player, null, true);
            }
        }
    }

    public void forceUpdateLightingContext() {
        updateLightingContext(); // bypass maybeUpdateLightingContext() caching
        lastLightTick = this.tickCount; // reset the timer so it doesn’t double-run
    }

    public int countInstanceOf(Class var1) {
        int var2 = 0;

        for (int var3 = 0; var3 < this.blockMap.all.size(); ++var3) {
            Entity var4 = (Entity) this.blockMap.all.get(var3);
            if (var1.isAssignableFrom(var4.getClass())) {
                ++var2;
            }
        }

        return var2;
    }

    public boolean isInBounds(int var1, int var2, int var3) {
        return var1 >= 0 && var2 >= 0 && var3 >= 0 && var1 < this.width && var2 < this.depth && var3 < this.height;
    }

    public float getGroundLevel() {
        return this.getWaterLevel() - 2.0F;
    }

    private float lastDayFactor = -1f;

// 0.25 at midnight .. 1.0 at noon (what you used inside getBrightness)

    /** If daylight changed noticeably, force chunk rebuilds. */
    private void notifyLightingDelta() {
        float f = getDayFactor();
        if (Math.abs(f - lastDayFactor) > 0.02f) { // ~2% step
            lastDayFactor = f;
            for (int i = 0; i < this.listeners.size(); i++) {
                ((net.classicremastered.minecraft.render.LevelRenderer) this.listeners.get(i)).refresh();
            }
        }
    }

    public float getWaterLevel() {
        return (float) this.waterLevel;
    }

    public boolean containsAnyLiquid(AABB var1) {
        int var2 = (int) var1.x0;
        int var3 = (int) var1.x1 + 1;
        int var4 = (int) var1.y0;
        int var5 = (int) var1.y1 + 1;
        int var6 = (int) var1.z0;
        int var7 = (int) var1.z1 + 1;
        if (var1.x0 < 0.0F) {
            --var2;
        }

        if (var1.y0 < 0.0F) {
            --var4;
        }

        if (var1.z0 < 0.0F) {
            --var6;
        }

        if (var2 < 0) {
            var2 = 0;
        }

        if (var4 < 0) {
            var4 = 0;
        }

        if (var6 < 0) {
            var6 = 0;
        }

        if (var3 > this.width) {
            var3 = this.width;
        }

        if (var5 > this.depth) {
            var5 = this.depth;
        }

        if (var7 > this.height) {
            var7 = this.height;
        }

        for (int var10 = var2; var10 < var3; ++var10) {
            for (var2 = var4; var2 < var5; ++var2) {
                for (int var8 = var6; var8 < var7; ++var8) {
                    Block var9;
                    if ((var9 = Block.blocks[this.getTile(var10, var2, var8)]) != null
                            && var9.getLiquidType() != LiquidType.NOT_LIQUID) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean containsLiquid(AABB var1, LiquidType var2) {
        int var3 = (int) var1.x0;
        int var4 = (int) var1.x1 + 1;
        int var5 = (int) var1.y0;
        int var6 = (int) var1.y1 + 1;
        int var7 = (int) var1.z0;
        int var8 = (int) var1.z1 + 1;
        if (var1.x0 < 0.0F) {
            --var3;
        }

        if (var1.y0 < 0.0F) {
            --var5;
        }

        if (var1.z0 < 0.0F) {
            --var7;
        }

        if (var3 < 0) {
            var3 = 0;
        }

        if (var5 < 0) {
            var5 = 0;
        }

        if (var7 < 0) {
            var7 = 0;
        }

        if (var4 > this.width) {
            var4 = this.width;
        }

        if (var6 > this.depth) {
            var6 = this.depth;
        }

        if (var8 > this.height) {
            var8 = this.height;
        }

        for (int var11 = var3; var11 < var4; ++var11) {
            for (var3 = var5; var3 < var6; ++var3) {
                for (int var9 = var7; var9 < var8; ++var9) {
                    Block var10;
                    if ((var10 = Block.blocks[this.getTile(var11, var3, var9)]) != null
                            && var10.getLiquidType() == var2) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void addToTickNextTick(int var1, int var2, int var3, int var4) {
        if (!this.networkMode) {
            NextTickListEntry var5 = new NextTickListEntry(var1, var2, var3, var4);
            if (var4 > 0) {
                var3 = Block.blocks[var4].getTickDelay();
                var5.ticks = var3;
            }

            this.tickList.add(var5);
        }
    }

    public boolean isFree(AABB var1) {
        return this.blockMap.getEntities((Entity) null, var1).size() == 0;
    }

    public List findEntities(Entity var1, AABB var2) {
        return this.blockMap.getEntities(var1, var2);
    }

    public boolean isSolid(float var1, float var2, float var3, float var4) {
        return this.isSolid(var1 - var4, var2 - var4, var3 - var4) ? true
                : (this.isSolid(var1 - var4, var2 - var4, var3 + var4) ? true
                        : (this.isSolid(var1 - var4, var2 + var4, var3 - var4) ? true
                                : (this.isSolid(var1 - var4, var2 + var4, var3 + var4) ? true
                                        : (this.isSolid(var1 + var4, var2 - var4, var3 - var4) ? true
                                                : (this.isSolid(var1 + var4, var2 - var4, var3 + var4) ? true
                                                        : (this.isSolid(var1 + var4, var2 + var4, var3 - var4) ? true
                                                                : this.isSolid(var1 + var4, var2 + var4,
                                                                        var3 + var4)))))));
    }

    private boolean isSolid(float var1, float var2, float var3) {
        int var4;
        return (var4 = this.getTile((int) var1, (int) var2, (int) var3)) > 0 && Block.blocks[var4].isSolid();
    }

    public int getHighestTile(int var1, int var2) {
        int var3;
        for (var3 = this.depth; (this.getTile(var1, var3 - 1, var2) == 0
                || Block.blocks[this.getTile(var1, var3 - 1, var2)].getLiquidType() != LiquidType.NOT_LIQUID)
                && var3 > 0; --var3) {
            ;
        }

        return var3;
    }

    public void setSpawnPos(int var1, int var2, int var3, float var4) {
        this.xSpawn = var1;
        this.ySpawn = var2;
        this.zSpawn = var3;
        this.rotSpawn = var4;
    }
// AFTER

//Level.java
    public void tickTime() {
        if (!doDayNightCycle)
            return;
        lastTimeOfDay = timeOfDay;

        // 24000 / (20 TPS * 120s) = 10 units per tick
        timeOfDay = (timeOfDay + 2) % 24000;
    }

    public void setRenderPartial(float partial) {
        // 0..1 from the game’s timer.delta (fraction of the current tick)
        this.renderPartial = partial < 0 ? 0 : (partial > 1 ? 1 : partial);
    }

    private float lerpWrapped(int a, int b, float t, int mod) {
        int d = b - a;
        if (d < -mod / 2)
            d += mod;
        if (d > mod / 2)
            d -= mod;
        return ((a + d * t) % mod + mod) % mod;
    }

    public float getCelestialAngleSmooth() { // 0..1
        float t = lerpWrapped(lastTimeOfDay, timeOfDay, renderPartial, dayLength);
        return t / dayLength;
    }

    public float getDayFactorSmooth() { // 0.25..1.0 (your mapping)
        float a = getCelestialAngleSmooth() * (float) Math.PI * 2f;
        float c = MathHelper.cos(a) * 0.5f + 0.5f; // 0..1 (noon=1)
        float dl = (c - 0.2f) / 0.8f; // normalize like before
        if (dl < 0)
            dl = 0;
        if (dl > 1)
            dl = 1;
        return 0.25f + 0.75f * dl; // your outside brightness
    }

    public void updateDayNightColorsSmooth() {
        boolean rainbow = false;

        // Enable rainbow only if flag is set AND player is far enough
        if (forceRainbowSky && this.player != null) {
            float distX = Math.abs(this.player.x);
            float distZ = Math.abs(this.player.z);
            if (distX > 30_000_000 || distZ > 30_000_000) {
                rainbow = true;
            }
        }

        if (rainbow) {
            // Rainbow sky ignores day/night cycle
            float hue = (this.tickCount % 720) / 720.0f; // cycle ~36s
            java.awt.Color c = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
            this.skyColor = (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
            this.fogColor = this.skyColor;
            this.cloudColor = this.skyColor;
            return;
        }

        // === Normal day/night colors ===
        float dl = (getDayFactorSmooth() - 0.25f) / 0.75f;
        if (dl < 0f)
            dl = 0f;
        if (dl > 1f)
            dl = 1f;

        this.skyColor = lerpColor(SKY_NIGHT, SKY_DAY, dl);
        this.fogColor = lerpColor(FOG_NIGHT, FOG_DAY, dl);
        this.cloudColor = lerpColor(CLOUD_NGT, CLOUD_DAY, dl);
    }

    /**
     * True during the day. If you prefer a softer cutoff, gate on
     * getDaylightBrightness().
     */
    public boolean isDaytime() {
        // Sun above horizon => day. Cos(angle) > 0 means between sunrise and sunset.
        float a = getCelestialAngle(0f) * (float) Math.PI * 2f;
        return MathHelper.cos(a) > 0f;
    }

    /**
     * Returns true if the position is directly exposed to the sky (no opaque blocks
     * above).
     */
    public boolean isSunlitAt(float fx, float fy, float fz) {
        int x = (int) Math.floor(fx);
        int y = (int) Math.floor(fy);
        int z = (int) Math.floor(fz);
        // y >= blockers[x + z*width] means sky visible at that column (your isLit())
        return isLit(x, y, z);
    }

    /** Convenience for entity head position. */
    public boolean isSunlitAtHead(Entity e) {
        // Use head/eye height like your brightness/fog code (~+0.12F or heightOffset if
        // present)
        float headY = e.y + 0.12F;
        return isSunlitAt(e.x, headY, e.z);
    }

    /**
     * Single source of truth for whether undead (zombies/skeletons) should burn
     * here.
     */
    public boolean shouldUndeadBurnAt(Entity e) {
        if (e == null)
            return false;

        // 1) Must be daytime AND actually bright enough (guards dawn/dusk)
        if (!isDaytime())
            return false;
        if (getDaylightBrightness() < 0.55f)
            return false; // extra safety against dim light

        // 2) Must see the sky at head level
        if (!isSunlitAtHead(e))
            return false;

        // 3) Not in water (optional but classic)
        int bx = (int) Math.floor(e.x);
        int by = (int) Math.floor(e.y);
        int bz = (int) Math.floor(e.z);
        if (isWater(bx, by, bz))
            return false;
        if (isWater(bx, by + 1, bz))
            return false;

        return true;
    }

    public float getDayFactor() {
        float d = daylight01(); // 0..1 (0 midnight, 1 noon)
        return 0.25f + 0.75f * d; // 0.25..1.0
    }

    private float light_px, light_py, light_pz;
    private float light_caveFactor = 0f; // 0..1 (0 = outdoors, 1 = deep cave)
    public boolean light_insideOpaque = false;
    public Entity[] entityList;

    /** Update per-tick lighting context (cheap!). Call this once per tick. */
    public void updateLightingContext() {
        if (player == null) {
            light_caveFactor = 0f;
            light_insideOpaque = false;
            return;
        }

        light_px = player.x;
        light_py = player.y;
        light_pz = player.z;

        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y + 0.12F); // head/eyes, matches your fog check
        int pz = (int) Math.floor(player.z);

        light_insideOpaque = isLightBlocker(px, py, pz);

        // If player spot is sky-lit, treat as outdoors quickly
        boolean skyLitHere = isLit(px, (int) Math.floor(player.y), pz);
        if (skyLitHere) {
            light_caveFactor = 0f;
        } else {
            // You CAN use getCaveness(player) here because it runs ONCE per tick, not
            // per-vertex.
            float c = getCaveness(player); // 0..1
            if (light_insideOpaque)
                c = 1f; // force max darkness when inside a block
            if (c < 0f)
                c = 0f;
            else if (c > 1f)
                c = 1f;
            light_caveFactor = c;
        }
    }

    public float getBrightness(int x, int y, int z) {
        return lightEngine.getBrightness(x, y, z);
    }

    /**
     * Returns 0..1 glow factor from emissive blocks around (x,y,z). Cheap & local.
     */
    private float getEmissiveBoost(int x, int y, int z) {
        // if the cell itself glows, full bright
        int id = getTile(x, y, z);
        if (id > 0) {
            Block b = Block.blocks[id];
            if (b != null && b.getLightValue() > 0)
                return 1.0f;
        }

        final int R = 6; // search radius in blocks
        final float R2 = R * R;
        float max = 0f;

        for (int dy = -R; dy <= R; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= this.depth)
                continue;
            for (int dz = -R; dz <= R; dz++) {
                int zz = z + dz;
                if (zz < 0 || zz >= this.height)
                    continue;
                for (int dx = -R; dx <= R; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= this.width)
                        continue;

                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > R2)
                        continue;

                    int bid = getTile(xx, yy, zz);
                    if (bid <= 0)
                        continue;
                    Block bb = Block.blocks[bid];
                    if (bb == null)
                        continue;

                    int lv = bb.getLightValue(); // 0..15
                    if (lv <= 0)
                        continue;

                    float L = lv / 15.0f;
                    // simple radial falloff (linear). You can try 1/(1+d2) for smoother.
                    float fall = 1.0f - (float) Math.sqrt(d2) / (float) R;
                    if (fall < 0f)
                        fall = 0f;

                    float g = L * fall;
                    if (g > max) {
                        max = g;
                        if (max >= 1.0f)
                            return 1.0f;
                    }
                }
            }
        }
        return max;
    }

    public float getCaveness(float var1, float var2, float var3, float var4) {
        int var5 = (int) var1;
        int var14 = (int) var2;
        int var6 = (int) var3;
        float var7 = 0.0F;
        float var8 = 0.0F;

        for (int var9 = var5 - 6; var9 <= var5 + 6; ++var9) {
            for (int var10 = var6 - 6; var10 <= var6 + 6; ++var10) {
                if (this.isInBounds(var9, var14, var10) && !this.isSolidTile(var9, var14, var10)) {
                    float var11 = (float) var9 + 0.5F - var1;

                    float var12;
                    float var13;
                    for (var13 = (float) (Math.atan2((double) (var12 = (float) var10 + 0.5F - var3), (double) var11)
                            - (double) (var4 * 3.1415927F / 180.0F)
                            + 1.5707963705062866D); var13 < -3.1415927F; var13 += 6.2831855F) {
                        ;
                    }

                    while (var13 >= 3.1415927F) {
                        var13 -= 6.2831855F;
                    }

                    if (var13 < 0.0F) {
                        var13 = -var13;
                    }

                    var11 = MathHelper.sqrt(var11 * var11 + 4.0F + var12 * var12);
                    var11 = 1.0F / var11;
                    if (var13 > 1.0F) {
                        var11 = 0.0F;
                    }

                    if (var11 < 0.0F) {
                        var11 = 0.0F;
                    }

                    var8 += var11;
                    if (this.isLit(var9, var14, var10)) {
                        var7 += var11;
                    }
                }
            }
        }

        if (var8 == 0.0F) {
            return 0.0F;
        } else {
            return var7 / var8;
        }
    }
// --- Cave darkness helpers ---

    /** Returns true if the player's head is inside an opaque block (extra dark). */
    private boolean isPlayerInsideOpaque() {
        if (player == null)
            return false;
        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y + 0.12F); // eye/head area like your fog check
        int pz = (int) Math.floor(player.z);
        return isLightBlocker(px, py, pz);
    }

    /**
     * 0..1 : 0 = outdoors, 1 = deep cave. Uses existing caveness logic, biases by
     * sky visibility at player.
     */
    private float playerCaveFactor() {
        if (player == null)
            return 0f;
        // If the player is sky-lit, treat as outdoors quickly
        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y);
        int pz = (int) Math.floor(player.z);
        boolean skyLitHere = isLit(px, py, pz);
        if (skyLitHere)
            return 0f;

        // Use your existing caveness sampler for a smooth value
        float c = getCaveness(player);
        // c is 0..1; make it a bit stronger underground
        c = Math.min(1f, c * 1.15f);
        // Hard override if head is inside an opaque block
        if (isPlayerInsideOpaque())
            c = 1f;
        return c;
    }

    /** Smoothstep helper: 0..1 input curved to ease in/out. */
    private static float smoothstep01(float x) {
        if (x <= 0f)
            return 0f;
        if (x >= 1f)
            return 1f;
        return x * x * (3f - 2f * x);
    }

    /** Clamp helper */
    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Lerp helper */
    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    public float getCaveness(Entity var1) {
        float var2 = MathHelper.cos(-var1.yRot * 0.017453292F + 3.1415927F);
        float var3 = MathHelper.sin(-var1.yRot * 0.017453292F + 3.1415927F);
        float var4 = MathHelper.cos(-var1.xRot * 0.017453292F);
        float var5 = MathHelper.sin(-var1.xRot * 0.017453292F);
        float var6 = var1.x;
        float var7 = var1.y;
        float var21 = var1.z;
        float var8 = 1.6F;
        float var9 = 0.0F;
        float var10 = 0.0F;

        for (int var11 = 0; var11 <= 200; ++var11) {
            float var12 = ((float) var11 / (float) 200 - 0.5F) * 2.0F;
            int var13 = 0;

            while (var13 <= 200) {
                float var14 = ((float) var13 / (float) 200 - 0.5F) * var8;
                float var16 = var4 * var14 + var5;
                var14 = var4 - var5 * var14;
                float var17 = var2 * var12 + var3 * var14;
                var16 = var16;
                var14 = var2 * var14 - var3 * var12;
                int var15 = 0;

                while (true) {
                    if (var15 < 10) {
                        float var18 = var6 + var17 * (float) var15 * 0.8F;
                        float var19 = var7 + var16 * (float) var15 * 0.8F;
                        float var20 = var21 + var14 * (float) var15 * 0.8F;
                        if (!this.isSolid(var18, var19, var20)) {
                            ++var9;
                            if (this.isLit((int) var18, (int) var19, (int) var20)) {
                                ++var10;
                            }

                            ++var15;
                            continue;
                        }
                    }

                    ++var13;
                    break;
                }
            }
        }

        if (var9 == 0.0F) {
            return 0.0F;
        } else {
            float var22;
            if ((var22 = var10 / var9 / 0.1F) > 1.0F) {
                var22 = 1.0F;
            }

            var22 = 1.0F - var22;
            return 1.0F - var22 * var22 * var22;
        }
    }

    public byte[] copyBlocks() {
        return Arrays.copyOf(this.blocks, this.blocks.length);
    }

    public LiquidType getLiquid(int var1, int var2, int var3) {
        int var4;
        return (var4 = this.getTile(var1, var2, var3)) == 0 ? LiquidType.NOT_LIQUID
                : Block.blocks[var4].getLiquidType();
    }

    public boolean isWater(int var1, int var2, int var3) {
        int var4;
        return (var4 = this.getTile(var1, var2, var3)) > 0 && Block.blocks[var4].getLiquidType() == LiquidType.WATER;
    }

    public void setNetworkMode(boolean var1) {
        this.networkMode = var1;
    }

    /** Expose networkMode to subclasses/other systems (safe accessor). */
    public boolean isNetworkMode() {
        return this.networkMode;
    }

    public MovingObjectPosition clip(Vec3D var1, Vec3D var2) {
        if (!Float.isNaN(var1.x) && !Float.isNaN(var1.y) && !Float.isNaN(var1.z)) {
            if (!Float.isNaN(var2.x) && !Float.isNaN(var2.y) && !Float.isNaN(var2.z)) {
                int var3 = (int) Math.floor((double) var2.x);
                int var4 = (int) Math.floor((double) var2.y);
                int var5 = (int) Math.floor((double) var2.z);
                int var6 = (int) Math.floor((double) var1.x);
                int var7 = (int) Math.floor((double) var1.y);
                int var8 = (int) Math.floor((double) var1.z);
                int var9 = 20;

                while (var9-- >= 0) {
                    if (Float.isNaN(var1.x) || Float.isNaN(var1.y) || Float.isNaN(var1.z)) {
                        return null;
                    }

                    if (var6 == var3 && var7 == var4 && var8 == var5) {
                        return null;
                    }

                    float var10 = 999.0F;
                    float var11 = 999.0F;
                    float var12 = 999.0F;
                    if (var3 > var6) {
                        var10 = (float) var6 + 1.0F;
                    }

                    if (var3 < var6) {
                        var10 = (float) var6;
                    }

                    if (var4 > var7) {
                        var11 = (float) var7 + 1.0F;
                    }

                    if (var4 < var7) {
                        var11 = (float) var7;
                    }

                    if (var5 > var8) {
                        var12 = (float) var8 + 1.0F;
                    }

                    if (var5 < var8) {
                        var12 = (float) var8;
                    }

                    float var13 = 999.0F;
                    float var14 = 999.0F;
                    float var15 = 999.0F;
                    float var16 = var2.x - var1.x;
                    float var17 = var2.y - var1.y;
                    float var18 = var2.z - var1.z;
                    if (var10 != 999.0F) {
                        var13 = (var10 - var1.x) / var16;
                    }

                    if (var11 != 999.0F) {
                        var14 = (var11 - var1.y) / var17;
                    }

                    if (var12 != 999.0F) {
                        var15 = (var12 - var1.z) / var18;
                    }

                    boolean var19 = false;
                    byte var24;
                    if (var13 < var14 && var13 < var15) {
                        if (var3 > var6) {
                            var24 = 4;
                        } else {
                            var24 = 5;
                        }

                        var1.x = var10;
                        var1.y += var17 * var13;
                        var1.z += var18 * var13;
                    } else if (var14 < var15) {
                        if (var4 > var7) {
                            var24 = 0;
                        } else {
                            var24 = 1;
                        }

                        var1.x += var16 * var14;
                        var1.y = var11;
                        var1.z += var18 * var14;
                    } else {
                        if (var5 > var8) {
                            var24 = 2;
                        } else {
                            var24 = 3;
                        }

                        var1.x += var16 * var15;
                        var1.y += var17 * var15;
                        var1.z = var12;
                    }

                    Vec3D var20;
                    var6 = (int) ((var20 = new Vec3D(var1.x, var1.y, var1.z)).x = (float) Math.floor((double) var1.x));
                    if (var24 == 5) {
                        --var6;
                        ++var20.x;
                    }

                    var7 = (int) (var20.y = (float) Math.floor((double) var1.y));
                    if (var24 == 1) {
                        --var7;
                        ++var20.y;
                    }

                    var8 = (int) (var20.z = (float) Math.floor((double) var1.z));
                    if (var24 == 3) {
                        --var8;
                        ++var20.z;
                    }

                    int var22 = this.getTile(var6, var7, var8);
                    Block var21 = Block.blocks[var22];
                    if (var22 > 0 && var21.getLiquidType() == LiquidType.NOT_LIQUID) {
                        MovingObjectPosition var23;
                        if (var21.isCube()) {
                            if ((var23 = var21.clip(var6, var7, var8, var1, var2)) != null) {
                                return var23;
                            }
                        } else if ((var23 = var21.clip(var6, var7, var8, var1, var2)) != null) {
                            return var23;
                        }
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
// ADD import if you don't already have it at the file top:

    /** Returns the nearest Player within maxDist of (x,y,z), or null if none. */
    public Player getNearestPlayer(float x, float y, float z, float maxDist) {
        Player nearest = null;
        float bestD2 = (maxDist <= 0 ? Float.MAX_VALUE : maxDist * maxDist);

        // Prefer the cached singleplayer field when available and in range
        if (this.player instanceof Player) {
            float dx = this.player.x - x;
            float dy = this.player.y - y;
            float dz = this.player.z - z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= bestD2) {
                nearest = (Player) this.player;
                bestD2 = d2;
            }
        }

        // Scan all entities (works for MP / multiple players too)
        if (this.blockMap != null && this.blockMap.all != null) {
            for (int i = 0; i < this.blockMap.all.size(); i++) {
                Object e = this.blockMap.all.get(i);
                if (e instanceof Player) {
                    Player p = (Player) e;
                    float dx = p.x - x;
                    float dy = p.y - y;
                    float dz = p.z - z;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        nearest = p;
                    }
                }
            }
        }
        return nearest;
    }

    // Level.java (Classic) — REPLACE BOTH METHODS

    public void playSound(String key, Entity src, float volume, float pitch) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.settings == null || !mc.settings.sound)
            return;
        if (mc.soundPC == null)
            return;

        // Classic keys use slash or dot; the manager normalizes them.
        mc.soundPC.playSoundAt(key, src == null ? 0f : src.x, src == null ? 0f : (src.y + 0.12f),
                src == null ? 0f : src.z, volume, pitch);
    }

    public void playSound(String key, float x, float y, float z, float volume, float pitch) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.settings == null || !mc.settings.sound)
            return;
        if (mc.soundPC == null)
            return;

        mc.soundPC.playSoundAt(key, x, y, z, volume, pitch);
    }
    // Add under existing fields

    // Snapshot DTO (stable, tiny)
    private static final class SavedEntity implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        String className;
        float x, y, z;
        float yRot, xRot;
        int health;

        SavedEntity() {
        }

        SavedEntity(String cls, float x, float y, float z, float yRot, float xRot, int health) {
            this.className = cls;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.health = health;
        }
    }

    public static final class SavedMob implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public short id;
        public float x, y, z;
        public float yRot, xRot;
        public int health;

        public SavedMob() {
        }

        public SavedMob(short id, float x, float y, float z, float yRot, float xRot, int health) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.health = health;
        }
    }

    public transient java.util.List<SavedMob> pendingEntities;

    // Add to Level (anywhere in the class)
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();

        java.util.ArrayList<SavedMob> list = new java.util.ArrayList<>();
        try {
            if (this.blockMap != null && this.blockMap.all != null && !this.networkMode) {
                for (int i = 0; i < this.blockMap.all.size(); i++) {
                    Object e = this.blockMap.all.get(i);
                    if (!(e instanceof net.classicremastered.minecraft.mob.Mob))
                        continue;
                    if (e instanceof net.classicremastered.minecraft.player.Player)
                        continue;
                    if (e instanceof net.classicremastered.minecraft.net.NetworkPlayer)
                        continue;

                    net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) e;
                    if (m.removed || m.health <= 0)
                        continue;

                    // optional ‘persistent’ flag on mobs
                    try {
                        java.lang.reflect.Field f = m.getClass().getField("persistent");
                        if (f.getType() == boolean.class && !f.getBoolean(m))
                            continue;
                    } catch (Throwable ignored) {
                    }

                    short id = net.classicremastered.minecraft.mob.MobRegistry.idOf(m);
                    if (id < 0)
                        continue; // SAFETY: skip unregistered

                    list.add(new SavedMob(id, m.x, m.y, m.z, m.yRot, m.xRot, m.health));
                }
            }
        } catch (Throwable ignored) {
        }

        out.writeInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            SavedMob s = list.get(i);
            out.writeShort(s.id);
            out.writeFloat(s.x);
            out.writeFloat(s.y);
            out.writeFloat(s.z);
            out.writeFloat(s.yRot);
            out.writeFloat(s.xRot);
            out.writeInt(s.health);
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();

        try {
            int n = in.readInt();
            if (n > 0) {
                this.pendingEntities = new java.util.ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    SavedMob s = new SavedMob();
                    s.id = in.readShort();
                    s.x = in.readFloat();
                    s.y = in.readFloat();
                    s.z = in.readFloat();
                    s.yRot = in.readFloat();
                    s.xRot = in.readFloat();
                    s.health = in.readInt();
                    this.pendingEntities.add(s);
                }
            }
        } catch (java.io.EOFException ignored) {
            // older worlds with no mob list
        }
    }

    public boolean maybeGrowTree(int var1, int var2, int var3) {
        int var4 = this.random.nextInt(3) + 4;
        boolean var5 = true;

        int var6;
        int var8;
        int var9;
        for (var6 = var2; var6 <= var2 + 1 + var4; ++var6) {
            byte var7 = 1;
            if (var6 == var2) {
                var7 = 0;
            }

            if (var6 >= var2 + 1 + var4 - 2) {
                var7 = 2;
            }

            for (var8 = var1 - var7; var8 <= var1 + var7 && var5; ++var8) {
                for (var9 = var3 - var7; var9 <= var3 + var7 && var5; ++var9) {
                    if (var8 >= 0 && var6 >= 0 && var9 >= 0 && var8 < this.width && var6 < this.depth
                            && var9 < this.height) {
                        if ((this.blocks[(var6 * this.height + var9) * this.width + var8] & 255) != 0) {
                            var5 = false;
                        }
                    } else {
                        var5 = false;
                    }
                }
            }
        }

        if (!var5) {
            return false;
        } else if ((this.blocks[((var2 - 1) * this.height + var3) * this.width + var1] & 255) == Block.GRASS.id
                && var2 < this.depth - var4 - 1) {
            this.setTile(var1, var2 - 1, var3, Block.DIRT.id);

            int var13;
            for (var13 = var2 - 3 + var4; var13 <= var2 + var4; ++var13) {
                var8 = var13 - (var2 + var4);
                var9 = 1 - var8 / 2;

                for (int var10 = var1 - var9; var10 <= var1 + var9; ++var10) {
                    int var12 = var10 - var1;

                    for (var6 = var3 - var9; var6 <= var3 + var9; ++var6) {
                        int var11 = var6 - var3;
                        if (Math.abs(var12) != var9 || Math.abs(var11) != var9
                                || this.random.nextInt(2) != 0 && var8 != 0) {
                            this.setTile(var10, var13, var6, Block.LEAVES.id);
                        }
                    }
                }
            }

            for (var13 = 0; var13 < var4; ++var13) {
                this.setTile(var1, var2 + var13, var3, Block.LOG.id);
            }

            return true;
        } else {
            return false;
        }
    }

    public Entity getPlayer() {
        return this.player;
    }

    public void addEntity(Entity var1) {
        if (this.blockMap == null)
            return;

        this.blockMap.insert(var1);
        var1.setLevel(this);

        // Force chunk rebuild near the entity for renderer sync
        if (this.listeners != null) {
            for (int i = 0; i < listeners.size(); i++) {
                Object o = listeners.get(i);
                if (o instanceof net.classicremastered.minecraft.render.LevelRenderer) {
                    ((net.classicremastered.minecraft.render.LevelRenderer) o)
                            .queueChunks((int) var1.x - 1, (int) var1.y - 1, (int) var1.z - 1,
                                         (int) var1.x + 1, (int) var1.y + 1, (int) var1.z + 1);
                }
            }
        }
    }


    public void removeEntity(Entity var1) {
        this.blockMap.remove(var1);
    }

    public void explode(Entity var1, float var2, float var3, float var4, float var5) {
        int var6 = (int) (var2 - var5 - 1.0F);
        int var7 = (int) (var2 + var5 + 1.0F);
        int var8 = (int) (var3 - var5 - 1.0F);
        int var9 = (int) (var3 + var5 + 1.0F);
        int var10 = (int) (var4 - var5 - 1.0F);
        int var11 = (int) (var4 + var5 + 1.0F);

        int var13;
        float var15;
        float var16;
        for (int var12 = var6; var12 < var7; ++var12) {
            for (var13 = var9 - 1; var13 >= var8; --var13) {
                for (int var14 = var10; var14 < var11; ++var14) {
                    var15 = (float) var12 + 0.5F - var2;
                    var16 = (float) var13 + 0.5F - var3;
                    float var17 = (float) var14 + 0.5F - var4;
                    int var19;
                    if (var12 >= 0 && var13 >= 0 && var14 >= 0 && var12 < this.width && var13 < this.depth
                            && var14 < this.height && var15 * var15 + var16 * var16 + var17 * var17 < var5 * var5
                            && (var19 = this.getTile(var12, var13, var14)) > 0 && Block.blocks[var19].canExplode()) {
                        Block.blocks[var19].dropItems(this, var12, var13, var14, 0.3F);
                        this.setTile(var12, var13, var14, 0);
                        Block.blocks[var19].explode(this, var12, var13, var14);
                    }
                }
            }
        }

        List var18 = this.blockMap.getEntities(var1, (float) var6, (float) var8, (float) var10, (float) var7,
                (float) var9, (float) var11);

        for (var13 = 0; var13 < var18.size(); ++var13) {
            Entity var20;
            if ((var15 = (var20 = (Entity) var18.get(var13)).distanceTo(var2, var3, var4) / var5) <= 1.0F) {
                var16 = 1.0F - var15;
                var20.hurt(var1, (int) (var16 * 15.0F + 1.0F));
            }
        }

    }

    public Entity findSubclassOf(Class var1) {
        for (int var2 = 0; var2 < this.blockMap.all.size(); ++var2) {
            Entity var3 = (Entity) this.blockMap.all.get(var2);
            if (var1.isAssignableFrom(var3.getClass())) {
                return var3;
            }
        }

        return null;
    }

    public void removeAllNonCreativeModeEntities() {
        this.blockMap.removeAllNonCreativeModeEntities();
    }

    public void setTime(int time) {
        this.timeOfDay = time % 24000;
    }

}
