package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.mob.IronGolem;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.ExplosionParticle;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.LevelRenderer;
import net.classicremastered.util.MathHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Level implements Serializable {

    public static final long serialVersionUID = 0L;
    public int width;
    public int height;
    public int depth;
    public byte[] blocks;
    public String name;
    public String creator;
    public transient java.io.File saveFile;
    public long createTime;
    public int xSpawn;
    public int ySpawn;
    public int zSpawn;
    public float rotSpawn;
    protected transient ArrayList listeners = new ArrayList();
    protected transient int[] blockers;
    public transient Random random = new Random();
    private transient int randId;
    private transient LinkedHashMap<NextTickListEntry, Integer> tickList = new LinkedHashMap<>();
    public BlockMap blockMap;
    private boolean networkMode;
    public transient Minecraft minecraft;
    private transient VillagerDoorManager villagerDoorManager;
    private transient RedstoneEngine redstoneEngine;
    public Long biomeSeed;
    public boolean flatBiomes;
    public boolean oceanBiomes;

    public RedstoneEngine redstone() {
        if (redstoneEngine == null) redstoneEngine = new RedstoneEngine(this);
        return redstoneEngine;
    }

    public VillagerDoorManager villagerDoors() {
        if (villagerDoorManager == null) villagerDoorManager = new VillagerDoorManager(this);
        return villagerDoorManager;
    }

    public void onBlockChanged(int x, int y, int z) {
        if (redstoneEngine != null) redstoneEngine.changed(x, y, z);
        if (villagerDoorManager != null) villagerDoorManager.blockChanged(x, y, z);
        if (lightEngine != null) lightEngine.blockChanged(x, y, z);
        for (Object listener : listeners) {
            ((LevelRenderer) listener).queueChunks(x - 9, 0, z - 9, x + 9, depth - 1, z + 9);
        }
        if (minecraft != null
                && minecraft.levelRenderer != null
                && !listeners.contains(minecraft.levelRenderer))
            minecraft.levelRenderer.queueChunks(x - 9, 0, z - 9, x + 9, depth - 1, z + 9);
    }

    public float getRenderBrightness(int x, int y, int z) {
        float light = getBrightness(x, y, z);
        return minecraft != null && minecraft.settings != null
                ? minecraft.settings.renderBrightness(light)
                : light;
    }

    public boolean creativeMode;
    public transient boolean spectatorMode;
    public int waterLevel;
    public int skyColor; // --- Day/Night cycle ---
    // 24,000 world time units in 19,200 game ticks (16 minutes at 20 TPS).
    private static final int DAY_TIME_QUARTERS_PER_TICK = 5;
    private transient int dayTimeQuarter;
    private transient int lastDayTimeQuarter;
    public int dayLength = 24000; // world time units per full day
    public int timeOfDay = 2000; // 8:00 AM start
    public boolean doMobSpawning = true;
    private int gameRulesVersion = 1;
    public boolean doMobLoot = true;
    public boolean keepInventory = false;
    public boolean doDayNightCycle = true;
    public java.util.Set<net.classicremastered.minecraft.level.structure.GeneratedHouse> generatedHouses = java.util.concurrent.ConcurrentHashMap
            .newKeySet();
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
    public transient LevelHelper helper;

    public transient java.util.HashMap<Long, Byte> flowLevels = new java.util.HashMap<>();
    private transient java.util.HashMap<Long, Byte> cactusAges = new java.util.HashMap<>();

    public static long getCoordKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public int getCactusAge(int x, int y, int z) {
        return cactusAges.getOrDefault(getCoordKey(x, y, z), (byte) 0);
    }

    public void setCactusAge(int x, int y, int z, int age) {
        long key = getCoordKey(x, y, z);
        if (age == 0) cactusAges.remove(key);
        else cactusAges.put(key, (byte) age);
    }

    public int getFlowLevel(int x, int y, int z) {
        int tile = getTile(x, y, z);
        if (tile != Block.WATER.id
                && tile != Block.STATIONARY_WATER.id
                && tile != Block.LAVA.id
                && tile != Block.STATIONARY_LAVA.id) {
            return -1;
        }

        if (this.flowLevels == null) {
            this.flowLevels = new java.util.HashMap<>();
        }

        long key = getCoordKey(x, y, z);
        Byte val = this.flowLevels.get(key);
        if (val != null) {
            return val.intValue();
        }

        if (tile == Block.STATIONARY_WATER.id || tile == Block.STATIONARY_LAVA.id) {
            return 0;
        }
        return (tile == Block.WATER.id) ? 7 : 3;
    }

    public void setWaterFlowLevel(int x, int y, int z, int flow) {
        flowLevels.put(getCoordKey(x, y, z), (byte) flow);
        for (Object listener : listeners) {
            ((LevelRenderer) listener).queueChunks(x - 1, y - 1, z - 1, x + 1, y, z + 1);
        }
        if (minecraft != null
                && minecraft.levelRenderer != null
                && !listeners.contains(minecraft.levelRenderer)) {
            minecraft.levelRenderer.queueChunks(x - 1, y - 1, z - 1, x + 1, y, z + 1);
        }
    }

    protected void updateFlowLevelMap(int x, int y, int z, int id) {
        if (id != Block.CACTUS.id && !cactusAges.isEmpty()) cactusAges.remove(getCoordKey(x, y, z));
        if (this.flowLevels == null) {
            this.flowLevels = new java.util.HashMap<>();
        }
        if (id == Block.WATER.id
                || id == Block.STATIONARY_WATER.id
                || id == Block.LAVA.id
                || id == Block.STATIONARY_LAVA.id) {
            long key = getCoordKey(x, y, z);
            if (!this.flowLevels.containsKey(key)) {
                this.flowLevels.put(key, (byte) 0);
            }
        } else {
            this.flowLevels.remove(getCoordKey(x, y, z));
        }
    }

    public Level() {
        this.randId = this.random.nextInt();
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
        float advance =
                doDayNightCycle
                        ? DAY_TIME_QUARTERS_PER_TICK * Math.max(0f, Math.min(1f, partial))
                        : 0;
        return celestialAngleAt(timeOfDay + (dayTimeQuarter + advance) * .25f);
    }

    private float celestialAngleAt(float time) {
        // Keep the noon-based clock and ease the orbit:
        // slower at the horizons, faster overhead, as in the modern daylight cycle.
        float fraction = ((time % dayLength) + dayLength) % dayLength / dayLength;
        float eased = (1f - (float) Math.cos(fraction * Math.PI)) * 0.5f;
        return fraction + (eased - fraction) / 3f;
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
        if (dl < 0f) dl = 0f;
        if (dl > 1f) dl = 1f;
        return dl;
    }

    /** For star alpha: dark nights, zero at day. */
    public float getStarBrightness() {
        float sunHeight = MathHelper.cos(getCelestialAngleSmooth() * (float) Math.PI * 2);
        float dark = Math.max(0, Math.min(1, (-sunHeight - .15f) / .6f));
        return dark * dark * (3 - 2 * dark);
    }

    private static int lerpColor(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
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
        RidingLinks ridingLinks = new RidingLinks();
        initTransient(ridingLinks);
        ridingLinks.restore();
    }

    void initTransient(RidingLinks ridingLinks) {
        if (helper == null) helper = new LevelHelper(this);
        if (lightEngine == null) lightEngine = new LightEngine(this);
        if (cactusAges == null) cactusAges = new java.util.HashMap<>();
        if (this.blocks == null) {
            throw new RuntimeException("The level is corrupt!");
        } else {
            this.listeners = new ArrayList();
            this.blockers = new int[this.width * this.height];
            Arrays.fill(this.blockers, this.depth);
            if (this.flowLevels == null) this.flowLevels = new java.util.HashMap<>();
            this.calcLightDepths(0, 0, this.width, this.height);
            this.random = new Random();
            this.randId = this.random.nextInt();
            if (this.tickList == null) this.tickList = new LinkedHashMap<>();
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
                net.classicremastered.minecraft.mob.MobRegistry
                        .bootstrapDefaults(); // ensure registry ready

                for (int i = 0; i < this.pendingEntities.size(); i++) {
                    SavedMob s = this.pendingEntities.get(i);
                    net.classicremastered.minecraft.mob.Mob m =
                            net.classicremastered.minecraft.mob.MobRegistry.create(
                                    s.id, this, s.x, s.y, s.z);
                    if (m == null || m.getRegistryId() != s.id) continue;
                    net.classicremastered.minecraft.level.itemstack.Equipment.load(m, s.extraData);
                    if (s.extraData != null) m.loadPersistence(s.extraData);
                    VillagerDoorManager.loadHome(m, s.extraData);
                    m.moveTo(s.x, s.y, s.z, s.yRot, s.xRot);
                    m.health = m.lastHealth = s.health;
                    this.addEntity(m);
                    ridingLinks.read(m, s.extraData);
                }
                this.pendingEntities = null; // spawn only once
            }
        }
    }

    public void setData(int var1, int var2, int var3, byte[] var4) {
        redstoneEngine = null;
        this.width = var1;
        this.height = var3;
        this.depth = var2;
        this.blocks = var4;
        this.blockers = new int[var1 * var3];
        Arrays.fill(this.blockers, this.depth);
        this.calcLightDepths(0, 0, var1, var3);
        this.updateEmissiveList();

        for (var1 = 0; var1 < this.listeners.size(); ++var1) {
            ((LevelRenderer) this.listeners.get(var1)).refresh();
        }

        this.tickList.clear();
        this.flowLevels.clear();
        this.cactusAges.clear();
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
                for (var8 = this.depth - 1;
                        var8 > 0 && !this.isLightBlocker(var5, var8, var6);
                        --var8) {
                    ;
                }

                this.blockers[var5 + var6 * this.width] = var8;
                if (var7 != var8) {
                    int var9 = var7 < var8 ? var7 : var8;
                    var7 = var7 > var8 ? var7 : var8;

                    for (var8 = 0; var8 < this.listeners.size(); ++var8) {
                        ((LevelRenderer) this.listeners.get(var8))
                                .queueChunks(
                                        var5 - 1, var9 - 1, var6 - 1, var5 + 1, var7 + 1, var6 + 1);
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
        return (var4 = Block.blocks[this.getTile(var1, var2, var3)]) == null
                ? false
                : var4.isOpaque();
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

                    if (y >= this.depth) continue; // above world = air

                    int id = getTile(x, y, z);
                    if (id == 0) continue;
                    Block b = Block.blocks[id];
                    if (b == null) continue;

                    b.addCollisionBoxes(this, x, y, z, box, out);
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
        if (var1 >= 0
                && var2 >= 0
                && var3 >= 0
                && var1 < this.width
                && var2 < this.depth
                && var3 < this.height) {
            if (var4 == this.blocks[(var2 * this.height + var3) * this.width + var1]) {
                return false;
            } else {
                if (var4 == 0
                        && (var1 == 0
                                || var3 == 0
                                || var1 == this.width - 1
                                || var3 == this.height - 1)
                        && (float) var2 >= this.getGroundLevel()
                        && (float) var2 < this.getWaterLevel()) {
                    var4 = Block.WATER.id;
                }

                int idx = (var2 * this.height + var3) * this.width + var1;
                int prevId = this.blocks[idx] & 0xFF;
                this.blocks[idx] = (byte) var4;
                this.onBlockChanged(var1, var2, var3);
                this.updateEmissiveSource(var1, var2, var3, prevId, var4);
                this.updateFlowLevelMap(var1, var2, var3, var4);
                if (prevId != 0) {
                    Block.blocks[prevId].onRemoved(this, var1, var2, var3);
                }
                if (var4 != 0) {
                    Block.blocks[var4].onAdded(this, var1, var2, var3);
                }

                this.calcLightDepths(var1, var3, 1, 1);

                for (var4 = 0; var4 < this.listeners.size(); ++var4) {
                    ((LevelRenderer) this.listeners.get(var4))
                            .queueChunks(
                                    var1 - 1, var2 - 1, var3 - 1, var1 + 1, var2 + 1, var3 + 1);
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
        if (getTile(x, y - 1, z) != Block.IRON_BLOCK.id) return;

        // check body below
        if (getTile(x, y - 2, z) != Block.IRON_BLOCK.id) return;

        // check arms (X-axis)
        boolean xArms =
                getTile(x - 1, y - 1, z) == Block.IRON_BLOCK.id
                        && getTile(x + 1, y - 1, z) == Block.IRON_BLOCK.id;

        // check arms (Z-axis)
        boolean zArms =
                getTile(x, y - 1, z - 1) == Block.IRON_BLOCK.id
                        && getTile(x, y - 1, z + 1) == Block.IRON_BLOCK.id;

        if (!xArms && !zArms) return;

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
                this.particleEngine.spawnParticle(
                        new net.classicremastered.minecraft.particle.TerrainParticle(
                                this,
                                (float) px,
                                (float) py,
                                (float) pz,
                                0,
                                0.1f,
                                0,
                                Block.IRON_BLOCK));
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
        if (var1 >= 0
                && var2 >= 0
                && var3 >= 0
                && var1 < this.width
                && var2 < this.depth
                && var3 < this.height) {
            int old = this.blocks[(var2 * this.height + var3) * this.width + var1] & 0xFF;
            if (var4 == old) {
                return false;
            } else {
                this.blocks[(var2 * this.height + var3) * this.width + var1] = (byte) var4;
                this.onBlockChanged(var1, var2, var3);
                this.updateFlowLevelMap(var1, var2, var3, var4);
                this.updateEmissiveSource(var1, var2, var3, old, var4);
                return true;
            }
        } else {
            return false;
        }
    }

    private void updateTile(int x, int y, int z, int changedId) {
        if (isInBounds(x, y, z)) {
            Block block = Block.blocks[getTile(x, y, z)];
            if (block != null) {
                block.onNeighborChange(this, x, y, z, changedId);
            }
        }
    }

    public boolean isLit(int var1, int var2, int var3) {
        return var1 >= 0
                        && var2 >= 0
                        && var3 >= 0
                        && var1 < this.width
                        && var2 < this.depth
                        && var3 < this.height
                ? var2 >= this.blockers[var1 + var3 * this.width]
                : true;
    }

    public int getTile(int var1, int var2, int var3) {
        return var1 >= 0
                        && var2 >= 0
                        && var3 >= 0
                        && var1 < this.width
                        && var2 < this.depth
                        && var3 < this.height
                ? this.blocks[(var2 * this.height + var3) * this.width + var1] & 255
                : 0;
    }

    public boolean isSolidTile(int var1, int var2, int var3) {
        Block var4;
        return (var4 = Block.blocks[this.getTile(var1, var2, var3)]) == null
                ? false
                : var4.isSolid();
    }

    public void tickEntities() {
        this.blockMap.tickAll();
        lightEngine.refreshBurningEntities();
    }

    void queueLightRegion(int x, int y, int z, int radius) {
        for (Object listener : listeners)
            if (listener instanceof LevelRenderer renderer)
                renderer.queueChunks(x - radius, y - radius, z - radius, x + radius, y + radius,
                        z + radius);
        if (minecraft != null && minecraft.levelRenderer != null
                && !listeners.contains(minecraft.levelRenderer))
            minecraft.levelRenderer.queueChunks(x - radius, y - radius, z - radius, x + radius,
                    y + radius, z + radius);
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

        tickDayNightCycle();

        int var1 = 1;
        int var2;
        for (var2 = 1; 1 << var1 < this.width; ++var1) {}
        while (1 << var2 < this.height) {
            ++var2;
        }

        tickScheduledBlocks(500);

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
            if (shouldRandomTick(id, x, y, z)) {
                Block.blocks[id].update(this, x, y, z, this.random);
            }
        }

        despawnDistantMobs();

        // ---- Universal mob spawning (RAM safe throttle) ----
        // Spawn whenever this level is authoritative (not a network-client/proxy).
        if (!this.isNetworkMode()) {
            if (this.tickCount % 20 == 0) { // once per second
                MobSpawner spawner = new MobSpawner(this);
                spawner.spawn(6, this.player, null, true);
            }
        }
    }

    private boolean isEntityChunkLoaded(Entity entity) {
        int cx = MathHelper.floor(entity.x) >> 4, cz = MathHelper.floor(entity.z) >> 4;
        if (this instanceof LevelInfiniteTerrain terrain)
            return terrain.chunks().isChunkLoaded(cx, cz);
        if (this instanceof LevelInfiniteFlat flat) return flat.chunks().isChunkLoaded(cx, cz);
        return true;
    }

    public boolean isEntityActive(Entity entity) {
        if (entity instanceof Player || entity.rider instanceof Player)
            return true;
        if (entity.riding != null)
            entity = entity.riding;
        if (!isEntityChunkLoaded(entity)) return false;
        if (!(this instanceof LevelInfiniteTerrain) && !(this instanceof LevelInfiniteFlat))
            return true;
        for (Entity candidate : blockMap.all)
            if (candidate instanceof Player
                    && !candidate.removed
                    && Math.abs(entity.x - candidate.x) <= 128
                    && Math.abs(entity.z - candidate.z) <= 128) return true;
        return player != null
                && !player.removed
                && Math.abs(entity.x - player.x) <= 128
                && Math.abs(entity.z - player.z) <= 128;
    }

    public java.util.Set<net.classicremastered.minecraft.level.structure.CobblestoneTower.Entrance>
            cobblestoneTowers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    protected void despawnDistantMobs() {
        if (isNetworkMode() || blockMap == null) return;
        java.util.List<Player> players = new java.util.ArrayList<>();
        for (Entity entity : blockMap.all)
            if (entity instanceof Player player && !player.removed && player.health > 0)
                players.add(player);
        if (this.player instanceof Player player && !players.contains(player) && player.health > 0)
            players.add(player);
        for (Entity entity : new java.util.ArrayList<>(blockMap.all)) {
            if (!(entity instanceof net.classicremastered.minecraft.mob.Mob mob)
                    || !isEntityChunkLoaded(mob)
                    || mob.removed
                    || mob.health <= 0) continue;
            if (!MobSpawner.isMonster(mob) || mob.isPersistenceRequired()) {
                mob.despawnIdleTicks = 0;
                continue;
            }
            if (isDaytime()
                    && !(mob instanceof net.classicremastered.minecraft.mob.Husk)
                    && getDaylightBrightness() >= 0.6f
                    && (!(mob instanceof net.classicremastered.minecraft.mob.Zombie zombie)
                            || !zombie.isSunlightVulnerable())
                    && isSunlitAtHead(mob)) {
                mob.remove();
                continue;
            }
            if (players.isEmpty()) continue;
            float nearest = Float.MAX_VALUE;
            for (Player player : players) {
                float dx = mob.x - player.x, dy = mob.y - player.y, dz = mob.z - player.z;
                nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
            }
            if (nearest <= 32 * 32) mob.despawnIdleTicks = 0;
            else if (nearest > 128 * 128
                    || ++mob.despawnIdleTicks > 600 && random.nextInt(800) == 0) mob.remove();
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
        return var1 >= 0
                && var2 >= 0
                && var3 >= 0
                && var1 < this.width
                && var2 < this.depth
                && var3 < this.height;
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
                Object l = this.listeners.get(i);
                if (l instanceof net.classicremastered.minecraft.render.LevelRenderer) {
                    ((net.classicremastered.minecraft.render.LevelRenderer) l).onLightingChanged();
                }
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

    void saveBlockState(net.classicremastered.nbt.CompoundTag root) {
        var flows = new net.classicremastered.nbt.ListTag("FlowLevels", (byte) 10);
        for (var entry : flowLevels.entrySet()) {
            var tag = new net.classicremastered.nbt.CompoundTag(null);
            tag.putLong("Key", entry.getKey());
            tag.putByte("Depth", entry.getValue());
            flows.add(tag);
        }
        root.put(flows);
        var cacti = new net.classicremastered.nbt.ListTag("CactusAges", (byte) 10);
        for (var entry : cactusAges.entrySet()) {
            var tag = new net.classicremastered.nbt.CompoundTag(null);
            tag.putLong("Key", entry.getKey());
            tag.putByte("Age", entry.getValue());
            cacti.add(tag);
        }
        root.put(cacti);
        var ticks = new net.classicremastered.nbt.ListTag("ScheduledTicks", (byte) 10);
        for (var entry : tickList.entrySet()) {
            NextTickListEntry block = entry.getKey();
            var tag = new net.classicremastered.nbt.CompoundTag(null);
            tag.putInt("X", block.x());
            tag.putInt("Y", block.y());
            tag.putInt("Z", block.z());
            tag.putInt("Block", block.block());
            tag.putInt("Delay", Math.max(0, entry.getValue() - tickCount));
            ticks.add(tag);
        }
        root.put(ticks);
    }

    void loadBlockState(net.classicremastered.nbt.CompoundTag root) {
        cactusAges.clear();
        var cacti = root.getList("CactusAges");
        for (int i = 0; i < cacti.size(); i++) {
            var tag = (net.classicremastered.nbt.CompoundTag) cacti.get(i);
            byte age = tag.getByte("Age");
            if (age > 0 && age <= 15) cactusAges.put(tag.getLong("Key"), age);
        }
        if (root.hasKey("FlowLevels")) {
            flowLevels.clear();
            var flows = root.getList("FlowLevels");
            for (int i = 0; i < flows.size(); i++) {
                var tag = (net.classicremastered.nbt.CompoundTag) flows.get(i);
                long key = tag.getLong("Key");
                int x = (int) (key >> 38), y = (int) (key & 4095), z = (int) (key << 26 >> 38);
                int id = getTile(x, y, z);
                if (id == Block.WATER.id
                        || id == Block.STATIONARY_WATER.id
                        || id == Block.LAVA.id
                        || id == Block.STATIONARY_LAVA.id) {
                    flowLevels.put(key, tag.getByte("Depth"));
                }
            }
        }
        if (root.hasKey("ScheduledTicks")) {
            tickList.clear();
            var ticks = root.getList("ScheduledTicks");
            for (int i = 0; i < ticks.size(); i++) {
                var tag = (net.classicremastered.nbt.CompoundTag) ticks.get(i);
                int x = tag.getInt("X"), y = tag.getInt("Y"), z = tag.getInt("Z");
                int id = tag.getInt("Block");
                if (id > 0
                        && id < Block.blocks.length
                        && Block.blocks[id] != null
                        && isInBounds(x, y, z)
                        && getTile(x, y, z) == id) {
                    tickList.put(
                            new NextTickListEntry(x, y, z, id),
                            tickCount + Math.max(0, tag.getInt("Delay")));
                }
            }
        }
    }

    public void addToTickNextTick(int x, int y, int z, int id) {
        if (!isNetworkMode() && id > 0 && Block.blocks[id] != null && isInBounds(x, y, z)) {
            tickList.putIfAbsent(
                    new NextTickListEntry(x, y, z, id),
                    tickCount + Math.max(1, Block.blocks[id].getTickDelay()));
        }
    }

    protected void tickScheduledBlocks(int budget) {
        redstone().tick();
        int checks = Math.min(budget, tickList.size());
        for (int i = 0; i < checks; i++) {
            var iterator = tickList.entrySet().iterator();
            var pending = iterator.next();
            NextTickListEntry entry = pending.getKey();
            int due = pending.getValue();
            iterator.remove();
            if (due - tickCount > 0) {
                tickList.put(entry, due);
            } else if (getTile(entry.x(), entry.y(), entry.z()) == entry.block()) {
                Block.blocks[entry.block()].update(this, entry.x(), entry.y(), entry.z(), random);
            }
        }
    }

    protected boolean shouldRandomTick(int id, int x, int y, int z) {
        return Block.physics[id]
                && (id != Block.FIRE.id && id != Block.CACTUS.id
                        || !tickList.containsKey(new NextTickListEntry(x, y, z, id)));
    }

    public boolean isFree(AABB var1) {
        return this.blockMap.getEntities((Entity) null, var1).size() == 0;
    }

    public List findEntities(Entity var1, AABB var2) {
        return this.blockMap.getEntities(var1, var2);
    }

    public boolean isSolid(float var1, float var2, float var3, float var4) {
        return this.isSolid(var1 - var4, var2 - var4, var3 - var4)
                ? true
                : (this.isSolid(var1 - var4, var2 - var4, var3 + var4)
                        ? true
                        : (this.isSolid(var1 - var4, var2 + var4, var3 - var4)
                                ? true
                                : (this.isSolid(var1 - var4, var2 + var4, var3 + var4)
                                        ? true
                                        : (this.isSolid(var1 + var4, var2 - var4, var3 - var4)
                                                ? true
                                                : (this.isSolid(
                                                                var1 + var4,
                                                                var2 - var4,
                                                                var3 + var4)
                                                        ? true
                                                        : (this.isSolid(
                                                                        var1 + var4,
                                                                        var2 + var4,
                                                                        var3 - var4)
                                                                ? true
                                                                : this.isSolid(
                                                                        var1 + var4,
                                                                        var2 + var4,
                                                                        var3 + var4)))))));
    }

    private boolean isSolid(float var1, float var2, float var3) {
        int var4;
        return (var4 = this.getTile((int) var1, (int) var2, (int) var3)) > 0
                && Block.blocks[var4].isSolid();
    }

    public int getHighestTile(int var1, int var2) {
        int var3;
        for (var3 = this.depth;
                (this.getTile(var1, var3 - 1, var2) == 0
                                || Block.blocks[this.getTile(var1, var3 - 1, var2)].getLiquidType()
                                        != LiquidType.NOT_LIQUID)
                        && var3 > 0;
                --var3) {
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

    // Level.java
    public void tickTime() {
        lastTimeOfDay = timeOfDay;
        lastDayTimeQuarter = dayTimeQuarter;
        if (doDayNightCycle) {
            dayTimeQuarter += DAY_TIME_QUARTERS_PER_TICK;
            timeOfDay = (timeOfDay + dayTimeQuarter / 4) % dayLength;
            dayTimeQuarter %= 4;
        }
    }

    /** Shared by finite and infinite world tick implementations. */
    public void tickDayNightCycle() {
        if (this instanceof LevelInfiniteTerrain terrain) terrain.chunks().publishResidents();
        else if (this instanceof LevelInfiniteFlat flat) flat.chunks().publishResidents();
        tickTime();
        updateDayNightColorsSmooth();
        if (lightEngine.shouldRefreshChunks()) notifyLightingChanged();
        if (villagerDoorManager != null) villagerDoorManager.tick();
    }

    private void notifyLightingChanged() {
        for (Object listener : listeners) {
            if (listener instanceof LevelRenderer renderer) renderer.onLightingChanged();
        }
    }

    public void setRenderPartial(float partial) {
        // 0..1 from the game’s timer.delta (fraction of the current tick)
        this.renderPartial = partial < 0 ? 0 : (partial > 1 ? 1 : partial);
    }

    private float lerpWrapped(float a, float b, float t, int mod) {
        float d = b - a;
        if (d < -mod / 2) d += mod;
        if (d > mod / 2) d -= mod;
        return ((a + d * t) % mod + mod) % mod;
    }

    public float getCelestialAngleSmooth() { // 0..1
        float t =
                lerpWrapped(
                        lastTimeOfDay + lastDayTimeQuarter * .25f,
                        timeOfDay + dayTimeQuarter * .25f,
                        renderPartial,
                        dayLength);
        return celestialAngleAt(t);
    }

    public float getDayFactorSmooth() { // 0.25 at midnight, 1.0 at noon
        float a = getCelestialAngleSmooth() * (float) Math.PI * 2f;
        float c = MathHelper.cos(a) * 0.5f + 0.5f; // 0..1 (noon=1)
        return 0.25f + 0.75f * c;
    }

    public boolean hasRainbowSky() {
        return forceRainbowSky
                && player != null
                && Math.max(Math.abs(player.x), Math.abs(player.z)) > 30_000_000;
    }

    public float twilightStrength() {
        if (hasRainbowSky()
                || player != null && Math.max(Math.abs(player.x), Math.abs(player.z)) >= 25_000_000)
            return 0;
        float angle = getCelestialAngleSmooth();
        if (angle > .5f) angle = 1 - angle;
        if (angle < .19f || angle > .33f) return 0;
        float progress = angle < .25f ? (angle - .19f) / .06f : (.33f - angle) / .08f;
        return progress * progress * (3 - 2 * progress);
    }

    public void updateDayNightColorsSmooth() {
        if (hasRainbowSky()) {
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
        if (dl < 0f) dl = 0f;
        if (dl > 1f) dl = 1f;

        this.skyColor = lerpColor(SKY_NIGHT, SKY_DAY, dl);
        this.fogColor = lerpColor(FOG_NIGHT, FOG_DAY, dl);
        this.cloudColor = lerpColor(CLOUD_NGT, CLOUD_DAY, dl);
        float sunset = twilightStrength();
        if (sunset > 0) {
            this.fogColor = lerpColor(this.fogColor, 0xE69569, sunset * .22f);
            this.cloudColor = lerpColor(this.cloudColor, 0xFFAF82, sunset * .46f);
        }
    }

    /** True during the day. If you prefer a softer cutoff, gate on getDaylightBrightness(). */
    public boolean isDaytime() {
        // Sun above horizon => day. Cos(angle) > 0 means between sunrise and sunset.
        float a = getCelestialAngle(0f) * (float) Math.PI * 2f;
        return MathHelper.cos(a) > 0f;
    }

    /** Returns true if the position is directly exposed to the sky (no opaque blocks above). */
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

    /** Single source of truth for whether undead (zombies/skeletons) should burn here. */
    public boolean shouldUndeadBurnAt(Entity e) {
        if (e == null) return false;

        // 1) Must be daytime AND actually bright enough (guards dawn/dusk)
        if (!isDaytime()) return false;
        if (getDaylightBrightness() < 0.55f) return false; // extra safety against dim light

        // 2) Must see the sky at head level
        if (!isSunlitAtHead(e)) return false;

        // 3) Not in water (optional but classic)
        int bx = (int) Math.floor(e.x);
        int by = (int) Math.floor(e.y);
        int bz = (int) Math.floor(e.z);
        if (isWater(bx, by, bz)) return false;
        if (isWater(bx, by + 1, bz)) return false;

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
            if (light_insideOpaque) c = 1f; // force max darkness when inside a block
            if (c < 0f) c = 0f;
            else if (c > 1f) c = 1f;
            light_caveFactor = c;
        }
    }

    public int[] emissiveBlocks = null;
    public int emissiveCount = 0;
    private transient int[][] emissiveColumns;
    private transient int[] emissiveColumnCounts;
    private transient int emissiveColumnsX;
    private transient long emissiveRevision;

    public long getEmissiveRevision() {
        if (emissiveColumns == null) updateEmissiveList();
        return emissiveRevision;
    }

    /** Entries are indices into emissiveBlocks, avoiding boxed objects per light source. */
    public int[] getEmissiveColumn(int cx, int cz) {
        if (emissiveColumns == null) updateEmissiveList();
        if (cx < 0 || cz < 0 || cx >= emissiveColumnsX || cz >= (height + 15) / 16) return null;
        return emissiveColumns[cz * emissiveColumnsX + cx];
    }

    public int getEmissiveColumnCount(int cx, int cz) {
        if (emissiveColumns == null) updateEmissiveList();
        if (cx < 0 || cz < 0 || cx >= emissiveColumnsX || cz >= (height + 15) / 16) return 0;
        return emissiveColumnCounts[cz * emissiveColumnsX + cx];
    }

    private void indexEmissiveColumns() {
        emissiveColumnsX = (width + 15) / 16;
        int columns = emissiveColumnsX * ((height + 15) / 16);
        emissiveColumns = new int[columns][];
        emissiveColumnCounts = new int[columns];
        for (int i = 0; i < emissiveCount; i++) {
            int packed = emissiveBlocks[i];
            int column =
                    (((packed >>> 10) & 1023) >> 4) * emissiveColumnsX + ((packed & 1023) >> 4);
            emissiveColumnCounts[column]++;
        }
        for (int i = 0; i < columns; i++)
            if (emissiveColumnCounts[i] > 0) emissiveColumns[i] = new int[emissiveColumnCounts[i]];
        Arrays.fill(emissiveColumnCounts, 0);
        for (int i = 0; i < emissiveCount; i++) {
            int packed = emissiveBlocks[i];
            int column =
                    (((packed >>> 10) & 1023) >> 4) * emissiveColumnsX + ((packed & 1023) >> 4);
            emissiveColumns[column][emissiveColumnCounts[column]++] = i;
        }
        emissiveRevision++;
    }

    private void updateEmissiveSource(int x, int y, int z, int oldId, int newId) {
        int oldLight =
                oldId > 0 && Block.blocks[oldId] != null ? Block.blocks[oldId].getLightValue() : 0;
        int newLight =
                newId > 0 && Block.blocks[newId] != null ? Block.blocks[newId].getLightValue() : 0;
        // Flowing and stationary lava emit the same light. No index work is needed.
        if (oldLight == newLight) return;
        if (emissiveColumns == null) {
            updateEmissiveList();
            return;
        }
        int column = (z >> 4) * emissiveColumnsX + (x >> 4);
        int[] indices = emissiveColumns[column];
        int count = emissiveColumnCounts[column];
        int position = (x & 1023) | ((z & 1023) << 10) | ((y & 255) << 20);
        if (oldLight > 0) {
            for (int i = 0; i < count; i++) {
                int index = indices[i];
                if ((emissiveBlocks[index] & 0x0FFFFFFF) != position) continue;
                if (newLight > 0) {
                    emissiveBlocks[index] = position | (newLight << 28);
                } else {
                    indices[i] = indices[count - 1];
                    emissiveColumnCounts[column] = count - 1;
                    int lastIndex = --emissiveCount;
                    if (index != lastIndex) {
                        int moved = emissiveBlocks[lastIndex];
                        emissiveBlocks[index] = moved;
                        int movedColumn =
                                (((moved >>> 10) & 1023) >> 4) * emissiveColumnsX
                                        + ((moved & 1023) >> 4);
                        int[] movedIndices = emissiveColumns[movedColumn];
                        for (int j = 0; j < emissiveColumnCounts[movedColumn]; j++) {
                            if (movedIndices[j] == lastIndex) {
                                movedIndices[j] = index;
                                break;
                            }
                        }
                    }
                    emissiveBlocks[lastIndex] = 0;
                }
                emissiveRevision++;
                return;
            }
        }
        if (newLight > 0) {
            if (emissiveBlocks == null) emissiveBlocks = new int[128];
            else if (emissiveCount == emissiveBlocks.length)
                emissiveBlocks = Arrays.copyOf(emissiveBlocks, Math.max(128, emissiveCount * 2));
            if (indices == null) emissiveColumns[column] = indices = new int[16];
            else if (count == indices.length)
                emissiveColumns[column] = indices = Arrays.copyOf(indices, Math.max(16, count * 2));
            indices[count] = emissiveCount;
            emissiveColumnCounts[column] = count + 1;
            emissiveBlocks[emissiveCount++] = position | (newLight << 28);
            emissiveRevision++;
        }
    }

    public synchronized void updateEmissiveList() {
        if (blocks == null) {
            emissiveBlocks = null;
            emissiveCount = 0;
            emissiveColumns = null;
            emissiveColumnCounts = null;
            emissiveRevision++;
            return;
        }
        int count = 0;
        for (int i = 0; i < blocks.length; i++) {
            int id = blocks[i] & 0xFF;
            if (id > 0) {
                Block b = Block.blocks[id];
                if (b != null && b.getLightValue() > 0) {
                    count++;
                }
            }
        }
        if (count == 0) {
            emissiveBlocks = null;
            emissiveCount = 0;
            indexEmissiveColumns();
            return;
        }
        int[] list = new int[count];
        int listIdx = 0;
        for (int y = 0; y < depth; y++) {
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    int id = getTile(x, y, z);
                    if (id > 0) {
                        Block b = Block.blocks[id];
                        if (b != null) {
                            int lv = b.getLightValue();
                            if (lv > 0 && listIdx < count) {
                                list[listIdx++] =
                                        (x & 0x3FF)
                                                | ((z & 0x3FF) << 10)
                                                | ((y & 0xFF) << 20)
                                                | (lv << 28);
                            }
                        }
                    }
                }
            }
        }
        emissiveBlocks = list;
        emissiveCount = listIdx;
        indexEmissiveColumns();
    }

    public float getBrightness(int x, int y, int z) {
        return lightEngine.getBrightness(x, y, z);
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
                    for (var13 =
                                    (float)
                                            (Math.atan2(
                                                            (double)
                                                                    (var12 =
                                                                            (float) var10
                                                                                    + 0.5F
                                                                                    - var3),
                                                            (double) var11)
                                                    - (double) (var4 * 3.1415927F / 180.0F)
                                                    + 1.5707963705062866D);
                            var13 < -3.1415927F;
                            var13 += 6.2831855F) {
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
        if (player == null) return false;
        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y + 0.12F); // eye/head area like your fog check
        int pz = (int) Math.floor(player.z);
        return isLightBlocker(px, py, pz);
    }

    /**
     * 0..1 : 0 = outdoors, 1 = deep cave. Uses existing caveness logic, biases by sky visibility at
     * player.
     */
    private float playerCaveFactor() {
        if (player == null) return 0f;
        // If the player is sky-lit, treat as outdoors quickly
        int px = (int) Math.floor(player.x);
        int py = (int) Math.floor(player.y);
        int pz = (int) Math.floor(player.z);
        boolean skyLitHere = isLit(px, py, pz);
        if (skyLitHere) return 0f;

        // Use your existing caveness sampler for a smooth value
        float c = getCaveness(player);
        // c is 0..1; make it a bit stronger underground
        c = Math.min(1f, c * 1.15f);
        // Hard override if head is inside an opaque block
        if (isPlayerInsideOpaque()) c = 1f;
        return c;
    }

    /** Smoothstep helper: 0..1 input curved to ease in/out. */
    private static float smoothstep01(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
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
        return (var4 = this.getTile(var1, var2, var3)) == 0
                ? LiquidType.NOT_LIQUID
                : Block.blocks[var4].getLiquidType();
    }

    public LiquidType getLiquidAt(float x, float y, float z) {
        int blockX = MathHelper.floor(x);
        int blockY = MathHelper.floor(y);
        int blockZ = MathHelper.floor(z);
        LiquidType liquid = getLiquid(blockX, blockY, blockZ);
        if (liquid == LiquidType.WATER
                && y
                        >= LiquidBlock.waterSurfaceAt(
                                this, blockX, blockY, blockZ, x - blockX, z - blockZ)) {
            return LiquidType.NOT_LIQUID;
        }
        return liquid;
    }

    public boolean isWater(int var1, int var2, int var3) {
        int var4;
        return (var4 = this.getTile(var1, var2, var3)) > 0
                && Block.blocks[var4].getLiquidType() == LiquidType.WATER;
    }

    public void setNetworkMode(boolean var1) {
        this.networkMode = var1;
    }

    /** Expose networkMode to subclasses/other systems (safe accessor). */
    public boolean isNetworkMode() {
        return this.networkMode;
    }

    public MovingObjectPosition clip(Vec3D var1, Vec3D var2) {
        return this.clip(var1, var2, false);
    }

    public MovingObjectPosition clip(Vec3D var1, Vec3D var2, boolean checkSolidOnly) {
        return this.clip(var1, var2, checkSolidOnly, false);
    }

    public MovingObjectPosition clip(
            Vec3D var1, Vec3D var2, boolean checkSolidOnly, boolean checkLiquids) {
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
                    var6 =
                            (int)
                                    ((var20 = new Vec3D(var1.x, var1.y, var1.z)).x =
                                            (float) Math.floor((double) var1.x));
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
                    if (var22 > 0
                            && (var21.getLiquidType() == LiquidType.NOT_LIQUID || checkLiquids)) {
                        if (checkSolidOnly && var21.getCollisionBox(var6, var7, var8) == null) {
                            continue;
                        }
                        MovingObjectPosition hit = var21.clip(this, var6, var7, var8, var1, var2);
                        if (hit != null) return hit;
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

    /** Returns the nearest non-spectator player within maxDist, or null if none. */
    public Player getNearestPlayer(float x, float y, float z, float maxDist) {
        Player nearest = null;
        float bestD2 = (maxDist <= 0 ? Float.MAX_VALUE : maxDist * maxDist);

        // Prefer the cached singleplayer field when available and in range
        if (this.player instanceof Player p && !p.isSpectator()) {
            float dx = p.x - x;
            float dy = p.y - y;
            float dz = p.z - z;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= bestD2) {
                nearest = p;
                bestD2 = d2;
            }
        }

        // Scan all entities (works for MP / multiple players too)
        if (this.blockMap != null && this.blockMap.all != null) {
            for (int i = 0; i < this.blockMap.all.size(); i++) {
                Object e = this.blockMap.all.get(i);
                if (e instanceof Player p && !p.isSpectator()) {
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
        if (mc == null || mc.settings == null || !mc.settings.sound) return;
        if (mc.soundPC == null) return;

        // Classic keys use slash or dot; the manager normalizes them.
        mc.soundPC.playSoundAt(
                key,
                src == null ? 0f : src.x,
                src == null ? 0f : (src.y + 0.12f),
                src == null ? 0f : src.z,
                volume,
                pitch,
                src instanceof Mob && !(src instanceof Player));
    }

    public void playSound(String key, float x, float y, float z, float volume, float pitch) {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.settings == null || !mc.settings.sound) return;
        if (mc.soundPC == null) return;

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

        SavedEntity() {}

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
        public transient net.classicremastered.nbt.CompoundTag extraData;

        public SavedMob() {}

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
                    if (!(e instanceof net.classicremastered.minecraft.mob.Mob)) continue;
                    if (e instanceof net.classicremastered.minecraft.player.Player) continue;
                    if (e instanceof net.classicremastered.minecraft.net.NetworkPlayer) continue;

                    net.classicremastered.minecraft.mob.Mob m =
                            (net.classicremastered.minecraft.mob.Mob) e;
                    if (m.removed || m.health <= 0) continue;

                    // optional ‘persistent’ flag on mobs
                    try {
                        java.lang.reflect.Field f = m.getClass().getField("persistent");
                        if (f.getType() == boolean.class && !f.getBoolean(m)) continue;
                    } catch (Throwable ignored) {
                    }

                    short id = net.classicremastered.minecraft.mob.MobRegistry.idOf(m);
                    if (id < 0) continue; // SAFETY: skip unregistered

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

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (generatedHouses == null)
            generatedHouses = java.util.concurrent.ConcurrentHashMap.newKeySet();
        if (cobblestoneTowers == null) {
            cobblestoneTowers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        }
        if (gameRulesVersion == 0) {
            doMobSpawning = doMobLoot = true;
            keepInventory = false;
            gameRulesVersion = 1;
        }

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
                    if (var8 >= 0
                            && var6 >= 0
                            && var9 >= 0
                            && var8 < this.width
                            && var6 < this.depth
                            && var9 < this.height) {
                        if ((this.blocks[(var6 * this.height + var9) * this.width + var8] & 255)
                                != 0) {
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
        } else if ((this.blocks[((var2 - 1) * this.height + var3) * this.width + var1] & 255)
                        == Block.GRASS.id
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
                        if (Math.abs(var12) != var9
                                || Math.abs(var11) != var9
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
        if (this.blockMap == null) return;

        if (var1 instanceof net.classicremastered.minecraft.mob.Mob m)
            net.classicremastered.minecraft.CombatRules.roll(m);
        this.blockMap.insert(var1);
        var1.setLevel(this);
    }

    public void removeEntity(Entity var1) {
        this.blockMap.remove(var1);
    }

    public void playExplosionEffects(float x, float y, float z, float power) {
        playExplosionEffects(x, y, z, power, 1f, 1f);
    }

    public void playExplosionEffects(
            float x, float y, float z, float power, float volume, float pitch) {
        playSound("random/explode", x, y, z, volume, pitch);
        if (particleEngine == null) return;

        particleEngine.spawnParticle(new ExplosionParticle(this, x, y + .35f, z, power));
        Random smokeRandom = ThreadLocalRandom.current();
        for (int i = 0; i < 32; i++) {
            float dx = (smokeRandom.nextFloat() - .5f) * power * .8f;
            float dy = .2f + smokeRandom.nextFloat() * power * .45f;
            float dz = (smokeRandom.nextFloat() - .5f) * power * .8f;
            particleEngine.spawnParticle(
                    new SmokeParticle(this, x + dx, y + dy, z + dz, .06f).scale(3f));
        }
    }

    protected static boolean canBlastDestroy(Block block, Entity source) {
        return block.canExplode()
                || (source instanceof Creeper
                        && (block == Block.STONE || block == Block.COBBLESTONE));
    }

    public void explode(Entity var1, float var2, float var3, float var4, float var5) {
        explode(var1, var2, var3, var4, var5, true);
    }

    public void explode(
            Entity var1, float var2, float var3, float var4, float var5, boolean dropBlocks) {
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
                    if (var12 >= 0
                            && var13 >= 0
                            && var14 >= 0
                            && var12 < this.width
                            && var13 < this.depth
                            && var14 < this.height
                            && var15 * var15 + var16 * var16 + var17 * var17 < var5 * var5
                            && (var19 = this.getTile(var12, var13, var14)) > 0
                            && canBlastDestroy(Block.blocks[var19], var1)) {
                        if (dropBlocks)
                            Block.blocks[var19].dropItems(this, var12, var13, var14, 0.3F);
                        this.setTile(var12, var13, var14, 0);
                        Block.blocks[var19].explode(this, var12, var13, var14);
                    }
                }
            }
        }

        List var18 =
                this.blockMap.getEntities(
                        var1,
                        (float) var6,
                        (float) var8,
                        (float) var10,
                        (float) var7,
                        (float) var9,
                        (float) var11);

        for (var13 = 0; var13 < var18.size(); ++var13) {
            Entity var20;
            if ((var15 = (var20 = (Entity) var18.get(var13)).distanceTo(var2, var3, var4) / var5)
                    <= 1.0F) {
                var16 = 1.0F - var15;
                if (var20 instanceof net.classicremastered.minecraft.mob.Mob victim)
                    victim.hurtExplosion(var1, (int) (var16 * 15f + 1f));
                else var20.hurt(var1, (int) (var16 * 15f + 1f));
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
        this.timeOfDay = Math.floorMod(time, dayLength);
        this.dayTimeQuarter = this.lastDayTimeQuarter = 0;
        // A time jump is immediate, rather than an interpolation across the old day.
        this.lastTimeOfDay = this.timeOfDay;
        updateDayNightColorsSmooth();
        lightEngine.shouldRefreshChunks();
        notifyLightingChanged();
    }
}
