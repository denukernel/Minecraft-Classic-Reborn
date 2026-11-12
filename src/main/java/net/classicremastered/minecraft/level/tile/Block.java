package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

import java.util.Random;

public class Block {
    public final String name; // readable name

    protected Block(int id, String name) {
        explodes = true;
        blocks[id] = this;
        this.id = id;
        this.name = name;
        setBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

        opaque[id] = isSolid();
        cube[id] = isCube();
        liquid[id] = false;
    }

    // Block.java
    public String textureFile =
            net.classicremastered.minecraft.render.BlockTextureAtlas
                    .RESOURCE; // default atlas unless overridden

    protected Block(int id, int textureID, String name) {
        this(id, name);
        this.textureId = textureID;
    }

    protected static Random random = new Random();
    public static final Block[] blocks = new Block[256];
    public static final boolean[] physics = new boolean[256];
    private static boolean[] opaque = new boolean[256];
    private static boolean[] cube = new boolean[256];
    public static final boolean[] liquid = new boolean[256];
    private static int[] tickDelay = new int[256];

    public static final Block STONE;
    public static final Block GRASS;
    // --- New blocks ---
    public static final Block DIAMOND_ORE_NEW;
    public static final Block DIAMOND_BLOCK_NEW;
    public static final Block BEACON;
    public static final Block FURNACE, TORCH;
    public static final Block PUMPKIN;
    public static final Block CACTUS;
    public static final Block PISTON;
    public static final Block REDSTONE_WIRE,
            REDSTONE_TORCH,
            LEVER,
            STONE_BUTTON,
            STONE_PRESSURE_PLATE;
    public static final Block REPEATER,
            COMPARATOR,
            REDSTONE_BLOCK,
            REDSTONE_LAMP,
            LIT_REDSTONE_LAMP;
    public static final Block STICKY_PISTON, PISTON_HEAD;
    public static final Block SANDSTONE, CUT_SANDSTONE, CHISELED_SANDSTONE, SMOOTH_SANDSTONE;
    public static final Block SANDSTONE_SLAB,
            SANDSTONE_STAIRS,
            OAK_STAIRS,
            COBBLESTONE_STAIRS,
            STONE_BRICK_STAIRS,
            STONE_BRICKS,
            ORANGE_TERRACOTTA,
            BLUE_TERRACOTTA,
            CHEST,
            REDSTONE_ORE;
    public static final Block WORKBENCH;
    public static final Block DIRT;
    public static final Block COBBLESTONE;
    public static final Block LADDER;
    public static final Block LADDER_NORTH, LADDER_SOUTH, LADDER_WEST;
    public static final Block TOWER_LADDER;
    public static final Block SNOWY_GRASS, SNOWY_LEAVES, SNOW_LAYER, SNOW_BLOCK;
    public static final Block SHORT_GRASS;
    public static final Block WOOD;
    public static final Block SAPLING;
    public static final Block BEDROCK;
    public static final Block WATER;
    public static final Block STATIONARY_WATER;
    public static final Block LAVA;
    public static final Block STATIONARY_LAVA;
    public static final Block SAND;
    public static final Block GRAVEL;
    public static final Block GOLD_ORE;
    public static final Block IRON_ORE;
    public static final Block COAL_ORE;
    public static final Block LOG;
    public static final Block LEAVES;
    public static final Block SPONGE;
    public static final Block GLASS;
    public static final Block STRUCTURE_ALPHA;
    public static final Block STRUCTURE_BETA;
    public static final Block STRUCTURE_GAMMA;
    public static final Block STRUCTURE_DELTA;
    public static final Block RED_WOOL;
    public static final Block ORANGE_WOOL;
    public static final Block YELLOW_WOOL;
    public static final Block LIME_WOOL;
    public static final Block GREEN_WOOL;
    public static final Block AQUA_GREEN_WOOL;
    public static final Block CYAN_WOOL;
    public static final Block BLUE_WOOL;
    public static final Block PURPLE_WOOL;
    public static final Block INDIGO_WOOL;
    public static final Block VIOLET_WOOL;
    public static final Block MAGENTA_WOOL;
    public static final Block PINK_WOOL;
    public static final Block BLACK_WOOL;
    public static final Block GRAY_WOOL;
    public static final Block WHITE_WOOL;
    public static final Block DANDELION;
    public static final Block ROSE;
    public static final Block BROWN_MUSHROOM;
    public static final Block RED_MUSHROOM;
    public static final Block GOLD_BLOCK;
    public static final Block IRON_BLOCK;
    public static final Block DOUBLE_SLAB;
    public static final Block SLAB;
    public static final Block BRICK;
    public static final Block TNT;
    public static final Block BOOKSHELF;
    public static final Block MOSSY_COBBLESTONE;
    public static final Block OBSIDIAN;
    public static final Block FIRE;
    public static final Block JUKEBOX;
    public static final Block PORTAL;
    public static final Block LUCKY_BLOCK;
    public int textureId;
    public final int id;
    public Tile$SoundType stepsound = Tile$SoundType.none;
    private int hardness; // scaled by *20 in setData
    protected boolean explodes;
    public float x1, y1, z1, x2, y2, z2;
    public float particleGravity;

    public boolean isCube() {
        return true;
    }

    protected Block setData(
            Tile$SoundType soundType, float var2, float particleGravity, float hardness) {
        this.particleGravity = particleGravity;
        this.stepsound = soundType;
        this.hardness = (int) (hardness * 20.0F);

        if (this instanceof FlowerBlock) {
            stepsound = Tile$SoundType.grass;
        }
        return this;
    }

    /** 0..15; override for glowing blocks (fire, lava, etc.). */
    public int getLightValue() {
        return 0;
    }

    protected void setPhysics(boolean physics) {
        this.physics[id] = physics;
    }

    protected void setBounds(float x1, float y1, float z1, float x2, float y2, float z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }

    public void setTickDelay(int tickDelay) {
        this.tickDelay[id] = tickDelay;
    }

    // Block.java
    public void bindTexture(TextureManager texMgr) {
        if (net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE.equals(
                this.textureFile)) {
            // vanilla atlas
            GL11.glBindTexture(
                    GL11.GL_TEXTURE_2D,
                    texMgr.load(net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
        } else {
            // standalone custom texture (already loaded as GL texture handle)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
        }
    }

    public void renderFullbright(ShapeRenderer shapeRenderer) {
        float red = 0.5F, green = 0.8F, blue = 0.6F;
        shapeRenderer.color(red, red, red);
        renderInside(shapeRenderer, -2, 0, 0, 0);
        shapeRenderer.color(1.0F, 1.0F, 1.0F);
        renderInside(shapeRenderer, -2, 0, 0, 1);
        shapeRenderer.color(green, green, green);
        renderInside(shapeRenderer, -2, 0, 0, 2);
        shapeRenderer.color(green, green, green);
        renderInside(shapeRenderer, -2, 0, 0, 3);
        shapeRenderer.color(blue, blue, blue);
        renderInside(shapeRenderer, -2, 0, 0, 4);
        shapeRenderer.color(blue, blue, blue);
        renderInside(shapeRenderer, -2, 0, 0, 5);
    }

    protected float getBrightness(Level level, int x, int y, int z) {
        return level.getRenderBrightness(x, y, z);
    }

    public boolean canRenderSide(Level level, int x, int y, int z, int side) {
        Block neighbor = blocks[level.getTile(x, y, z)];
        return neighbor == null || !neighbor.occludesFace(level, x, y, z, side ^ 1);
    }

    public boolean occludesFace(Level level, int x, int y, int z, int face) {
        return isSolid();
    }

    protected void renderInside(
            Level level, ShapeRenderer renderer, int x, int y, int z, int face) {
        renderInside(renderer, x, y, z, face);
    }

    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        return true;
    }

    public MovingObjectPosition clip(Level level, int x, int y, int z, Vec3D start, Vec3D end) {
        return clip(x, y, z, start, end);
    }

    public AABB getSelectionBox(Level level, int x, int y, int z) {
        return getSelectionBox(x, y, z);
    }

    protected int getTextureId(int side) {
        return this.textureId; // already an OpenGL texture ID now
    }

    public void renderInside(ShapeRenderer shapeRenderer, int x, int y, int z, int side) {
        int textureID1 = getTextureId(side);
        renderSide(shapeRenderer, x, y, z, side, textureID1);
    }

    // Public accessor so GUIs can get a block’s texture safely
    public int getInventoryTextureId(int side) {
        return this.getTextureId(side);
    }

    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        return false; // default: not handled
    }

    // Overload used by renderInside: explicit textureID
    public void renderSide(ShapeRenderer sr, int x, int y, int z, int side) {
        this.renderSide(sr, x, y, z, side, this.getTextureId(side));
    }

    public void renderSide(ShapeRenderer sr, int x, int y, int z, int side, int textureID) {
        float u1, u2, v1, v2;

        if (net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE.equals(
                this.textureFile)) {
            // Classic atlas math
            int u0 = (textureID % 16) << 4;
            int v0 = (textureID / 16) << 4;
            u1 = u0 / 256.0F;
            u2 = (u0 + 15.99F) / 256.0F;
            v1 = v0 / 256.0F;
            v2 = (v0 + 15.99F) / 256.0F;
        } else {
            // Standalone textures (structure blocks, custom PNGs)
            u1 = 0.0F;
            u2 = 1.0F;
            v1 = 0.0F;
            v2 = 1.0F;
        }

        float x1 = (float) x + this.x1;
        float x2 = (float) x + this.x2;
        float y1 = (float) y + this.y1;
        float y2 = (float) y + this.y2;
        float z1 = (float) z + this.z1;
        float z2 = (float) z + this.z2;

        if (side == 0) { // bottom
            sr.vertexUV(x1, y1, z2, u1, v2);
            sr.vertexUV(x1, y1, z1, u1, v1);
            sr.vertexUV(x2, y1, z1, u2, v1);
            sr.vertexUV(x2, y1, z2, u2, v2);
        } else if (side == 1) { // top
            sr.vertexUV(x2, y2, z2, u2, v2);
            sr.vertexUV(x2, y2, z1, u2, v1);
            sr.vertexUV(x1, y2, z1, u1, v1);
            sr.vertexUV(x1, y2, z2, u1, v2);
        } else if (side == 2) { // north
            sr.vertexUV(x1, y2, z1, u2, v1);
            sr.vertexUV(x2, y2, z1, u1, v1);
            sr.vertexUV(x2, y1, z1, u1, v2);
            sr.vertexUV(x1, y1, z1, u2, v2);
        } else if (side == 3) { // south
            sr.vertexUV(x1, y2, z2, u1, v1);
            sr.vertexUV(x1, y1, z2, u1, v2);
            sr.vertexUV(x2, y1, z2, u2, v2);
            sr.vertexUV(x2, y2, z2, u2, v1);
        } else if (side == 4) { // west
            sr.vertexUV(x1, y2, z2, u2, v1);
            sr.vertexUV(x1, y2, z1, u1, v1);
            sr.vertexUV(x1, y1, z1, u1, v2);
            sr.vertexUV(x1, y1, z2, u2, v2);
        } else if (side == 5) { // east
            sr.vertexUV(x2, y1, z2, u1, v2);
            sr.vertexUV(x2, y1, z1, u2, v2);
            sr.vertexUV(x2, y2, z1, u2, v1);
            sr.vertexUV(x2, y2, z2, u1, v1);
        }
    }

    public MovingObjectPosition clip(int x, int y, int z, Vec3D start, Vec3D end) {
        // translate ray into block-local coords
        Vec3D s = start.add(-x, -y, -z);
        Vec3D e = end.add(-x, -y, -z);

        Vec3D ix1 = s.getXIntersection(e, this.x1);
        Vec3D ix2 = s.getXIntersection(e, this.x2);
        Vec3D iy1 = s.getYIntersection(e, this.y1);
        Vec3D iy2 = s.getYIntersection(e, this.y2);
        Vec3D iz1 = s.getZIntersection(e, this.z1);
        Vec3D iz2 = s.getZIntersection(e, this.z2);

        if (!xIntersects(ix1)) ix1 = null;
        if (!xIntersects(ix2)) ix2 = null;
        if (!yIntersects(iy1)) iy1 = null;
        if (!yIntersects(iy2)) iy2 = null;
        if (!zIntersects(iz1)) iz1 = null;
        if (!zIntersects(iz2)) iz2 = null;

        Vec3D hit = null;
        if (ix1 != null) hit = ix1;
        if (ix2 != null && (hit == null || s.distance(ix2) < s.distance(hit))) hit = ix2;
        if (iy1 != null && (hit == null || s.distance(iy1) < s.distance(hit))) hit = iy1;
        if (iy2 != null && (hit == null || s.distance(iy2) < s.distance(hit))) hit = iy2;
        if (iz1 != null && (hit == null || s.distance(iz1) < s.distance(hit))) hit = iz1;
        if (iz2 != null && (hit == null || s.distance(iz2) < s.distance(hit))) hit = iz2;

        if (hit == null) return null;

        byte side = -1;
        if (hit == ix1) side = 4; // west (min X)
        if (hit == ix2) side = 5; // east (max X)
        if (hit == iy1) side = 0; // bottom(min Y)
        if (hit == iy2) side = 1; // top (max Y)
        if (hit == iz1) side = 2; // north (min Z)
        if (hit == iz2) side = 3; // south (max Z)

        // translate back to world coords
        Vec3D wh = hit.add(x, y, z);
        return new MovingObjectPosition(x, y, z, side, wh);
    }

    // helpers must exist exactly like this
    private boolean xIntersects(Vec3D v) {
        return v != null && v.y >= this.y1 && v.y <= this.y2 && v.z >= this.z1 && v.z <= this.z2;
    }

    private boolean yIntersects(Vec3D v) {
        return v != null && v.x >= this.x1 && v.x <= this.x2 && v.z >= this.z1 && v.z <= this.z2;
    }

    private boolean zIntersects(Vec3D v) {
        return v != null && v.x >= this.x1 && v.x <= this.x2 && v.y >= this.y1 && v.y <= this.y2;
    }

    // (render methods unchanged)...

    public AABB getSelectionBox(int x, int y, int z) {
        return new AABB(
                (float) x + x1,
                (float) y + y1,
                (float) z + z1,
                (float) x + x2,
                (float) y + y2,
                (float) z + z2);
    }

    public AABB getCollisionBox(int x, int y, int z) {
        return new AABB(
                (float) x + x1,
                (float) y + y1,
                (float) z + z1,
                (float) x + x2,
                (float) y + y2,
                (float) z + z2);
    }

    public void addCollisionBoxes(
            Level level, int x, int y, int z, AABB query, java.util.List<AABB> boxes) {
        AABB box = getCollisionBox(x, y, z);
        if (box != null && query.intersectsInner(box)) boxes.add(box);
    }

    public void onPlacedBy(Level level, int x, int y, int z, Player player, int face) {
        onPlace(level, x, y, z);
    }

    public boolean isOpaque() {
        return true;
    }

    public boolean isSolid() {
        return true;
    }

    public void update(Level level, int x, int y, int z, Random rand) {}

    public void spawnBreakParticles(
            Level level, int x, int y, int z, ParticleManager particleManager) {
        float width = x2 - x1, height = y2 - y1, depth = z2 - z1;
        int columns = Math.max(2, (int) Math.ceil(width * 4));
        int rows = Math.max(2, (int) Math.ceil(height * 4));
        int layers = Math.max(2, (int) Math.ceil(depth * 4));
        for (int a = 0; a < columns; ++a)
            for (int b = 0; b < rows; ++b)
                for (int c = 0; c < layers; ++c) {
                    float dx = (a + 0.5F) / columns;
                    float dy = (b + 0.5F) / rows;
                    float dz = (c + 0.5F) / layers;
                    particleManager.spawnParticle(
                            new TerrainParticle(
                                            level,
                                            x + x1 + dx * width,
                                            y + y1 + dy * height,
                                            z + z1 + dz * depth,
                                            dx - 0.5F,
                                            dy - 0.5F,
                                            dz - 0.5F,
                                            this)
                                    .scale(0.65F));
                }
    }

    public final void spawnBlockParticles(
            Level level, int x, int y, int z, int side, ParticleManager pm) {
        float s = 0.1F;
        float px = x + particleCoordinate(x1, x2, s);
        float py = y + particleCoordinate(y1, y2, s);
        float pz = z + particleCoordinate(z1, z2, s);
        if (side == 0) py = (float) y + this.y1 - s;
        if (side == 1) py = (float) y + this.y2 + s;
        if (side == 2) pz = (float) z + this.z1 - s;
        if (side == 3) pz = (float) z + this.z2 + s;
        if (side == 4) px = (float) x + this.x1 - s;
        if (side == 5) px = (float) x + this.x2 + s;
        pm.spawnParticle(
                (new TerrainParticle(level, px, py, pz, 0.0F, 0.0F, 0.0F, this))
                        .setPower(0.2F)
                        .scale(0.6F));
    }

    private static float particleCoordinate(float min, float max, float margin) {
        float inset = Math.min(margin, (max - min) * 0.5F);
        return min + inset + random.nextFloat() * (max - min - inset * 2);
    }

    public LiquidType getLiquidType() {
        return LiquidType.NOT_LIQUID;
    }

    public void onNeighborChange(Level v1, int v2, int v3, int v4, int v5) {}

    public void onPlace(Level level, int x, int y, int z) {}

    public int getTickDelay() {
        return tickDelay[this.id];
    }

    public void onAdded(Level level, int x, int y, int z) {}

    public void onRemoved(Level v1, int v2, int v3, int v4) {}

    public int getDropCount() {
        return 1;
    }

    public int getDrop() {
        return this.id;
    }

    public final int getHardness() {
        return this.hardness;
    }

    public void onBreak(Level level, int x, int y, int z) {
        this.dropItems(level, x, y, z, 1.0F);
    }

    public void dropItems(Level level, int x, int y, int z, float chance) {
        if (!level.creativeMode) {
            int cnt = this.getDropCount();
            for (int i = 0; i < cnt; ++i) {
                if (random.nextFloat() <= chance) {
                    float off = 0.7F;
                    float ox = random.nextFloat() * off + (1.0F - off) * 0.5F;
                    float oy = random.nextFloat() * off + (1.0F - off) * 0.5F;
                    float oz = random.nextFloat() * off + (1.0F - off) * 0.5F;
                    int drop = getDrop();
                    if (drop >= 256) {
                        level.addEntity(
                                new net.classicremastered.minecraft.entity.DroppedItem(
                                        level, x + ox, y + oy, z + oz, drop - 256, 1));
                    } else {
                        level.addEntity(new DroppedBlock(level, x + ox, y + oy, z + oz, drop));
                    }
                }
            }
        }
    }

    public void renderPreview(ShapeRenderer sr) {
        sr.begin();
        for (int s = 0; s < 6; ++s) {
            if (s == 0) sr.normal(0.0F, 1.0F, 0.0F);
            if (s == 1) sr.normal(0.0F, -1.0F, 0.0F);
            if (s == 2) sr.normal(0.0F, 0.0F, 1.0F);
            if (s == 3) sr.normal(0.0F, 0.0F, -1.0F);
            if (s == 4) sr.normal(1.0F, 0.0F, 0.0F);
            if (s == 5) sr.normal(-1.0F, 0.0F, 0.0F);
            this.renderInside(sr, 0, 0, 0, s);
        }
        sr.end();
    }

    public final boolean canExplode() {
        return this.explodes;
    }

    // (clip and renderSide methods unchanged for brevity)

    public void explode(Level v1, int v2, int v3, int v4) {}

    public boolean render(Level v1, int v2, int v3, int v4, ShapeRenderer v5) {
        boolean any = false;
        float a = 0.5F, b = 0.8F, c = 0.6F, br;
        if (this.canRenderSide(v1, v2, v3 - 1, v4, 0)) {
            br = this.getBrightness(v1, v2, v3 - 1, v4);
            v5.color(a * br, a * br, a * br);
            this.renderInside(v1, v5, v2, v3, v4, 0);
            any = true;
        }
        if (this.canRenderSide(v1, v2, v3 + 1, v4, 1)) {
            br = this.getBrightness(v1, v2, v3 + 1, v4);
            v5.color(br, br, br);
            this.renderInside(v1, v5, v2, v3, v4, 1);
            any = true;
        }
        if (this.canRenderSide(v1, v2, v3, v4 - 1, 2)) {
            br = this.getBrightness(v1, v2, v3, v4 - 1);
            v5.color(b * br, b * br, b * br);
            this.renderInside(v1, v5, v2, v3, v4, 2);
            any = true;
        }
        if (this.canRenderSide(v1, v2, v3, v4 + 1, 3)) {
            br = this.getBrightness(v1, v2, v3, v4 + 1);
            v5.color(b * br, b * br, b * br);
            this.renderInside(v1, v5, v2, v3, v4, 3);
            any = true;
        }
        if (this.canRenderSide(v1, v2 - 1, v3, v4, 4)) {
            br = this.getBrightness(v1, v2 - 1, v3, v4);
            v5.color(c * br, c * br, c * br);
            this.renderInside(v1, v5, v2, v3, v4, 4);
            any = true;
        }
        if (this.canRenderSide(v1, v2 + 1, v3, v4, 5)) {
            br = this.getBrightness(v1, v2 + 1, v3, v4);
            v5.color(c * br, c * br, c * br);
            this.renderInside(v1, v5, v2, v3, v4, 5);
            any = true;
        }
        return any;
    }

    public int getRenderPass() {
        return 0;
    }

    // -------- Tool preference helpers (for Survival tool speeds) --------

    /** Pickaxe is best for these. */
    public boolean prefersPickaxe() {
        return (this instanceof StoneBlock)
                || (this instanceof OreBlock)
                || this == COBBLESTONE
                || this == MOSSY_COBBLESTONE
                || this == BRICK
                || this == SLAB
                || this == DOUBLE_SLAB
                || this == OBSIDIAN
                || this == GOLD_BLOCK
                || this == IRON_BLOCK
                || this == DIAMOND_BLOCK_NEW
                || this == REDSTONE_BLOCK
                || this == FURNACE
                || this == STONE_BRICKS
                || this == SANDSTONE
                || this == CUT_SANDSTONE
                || this == CHISELED_SANDSTONE
                || this == SMOOTH_SANDSTONE
                || this == SANDSTONE_SLAB
                || this instanceof StairBlock && getDrop() != OAK_STAIRS.id;
    }

    /** Shovel is best for these. */
    public boolean prefersShovel() {
        return (this instanceof DirtBlock)
                || this == DIRT
                || this == GRASS
                || this == SAND
                || this == GRAVEL
                || this == SNOWY_GRASS
                || this == SNOW_LAYER
                || this == SNOW_BLOCK
                || this instanceof LeavesBlock;
    }

    /** Axe is best for these. */
    public boolean prefersAxe() {
        return this == LOG
                || this == WOOD
                || this == BOOKSHELF
                || this instanceof LeavesBlock
                || this == CHEST
                || this == WORKBENCH
                || this instanceof StairBlock && getDrop() == OAK_STAIRS.id
                || this instanceof LadderBlock;
    }

    // -------- Static init --------
    static {
        Block var10000 = (new StoneBlock(1, 1)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 1.0F);
        boolean var0 = false;
        Block var1 = var10000;
        var10000.explodes = false;
        STONE = var1;
        GRASS = (new GrassBlock(2)).setData(Tile$SoundType.grass, 0.9F, 1.0F, 0.6F);
        DIRT = (new DirtBlock(3, 2)).setData(Tile$SoundType.grass, 0.8F, 1.0F, 0.5F);
        var10000 = (new Block(4, 16, "Stone")).setData(Tile$SoundType.stone, 1.0F, 1.0F, 1.5F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        COBBLESTONE = var1;
        LADDER = new TowerLadderBlock(91);
        TOWER_LADDER = LADDER;
        LADDER_NORTH = new LadderBlock(97, 2);
        LADDER_SOUTH = new LadderBlock(98, 3);
        LADDER_WEST = new LadderBlock(99, 4);
        SNOWY_GRASS = new SnowBlock(92, "Snowy Grass Block", SnowBlock.Kind.GRASS);
        SNOWY_LEAVES = new LeavesBlock(93, 24).setData(Tile$SoundType.grass, 1, 1, .2f);
        SNOW_LAYER = new SnowBlock(SnowBlock.LAYER_ID, "Snow Layer", SnowBlock.Kind.LAYER);
        SNOW_BLOCK = new SnowBlock(95, "Snow Block", SnowBlock.Kind.FULL);
        SHORT_GRASS = new ShortGrassBlock(96, 26);
        WOOD = (new Block(5, 4, "Wood")).setData(Tile$SoundType.wood, 1.0F, 1.0F, 1.5F);
        SAPLING = (new SaplingBlock(6, 15)).setData(Tile$SoundType.none, 0.7F, 1.0F, 0.0F);
        var10000 =
                (new Block(7, 17, "CobbleStone")).setData(Tile$SoundType.stone, 1.0F, 1.0F, 999.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        BEDROCK = var1;
        WATER =
                (new LiquidBlock(8, LiquidType.WATER))
                        .setData(Tile$SoundType.none, 1.0F, 1.0F, 100.0F);
        STATIONARY_WATER =
                (new StillLiquidBlock(9, LiquidType.WATER))
                        .setData(Tile$SoundType.none, 1.0F, 1.0F, 100.0F);
        LAVA =
                (new LiquidBlock(10, LiquidType.LAVA))
                        .setData(Tile$SoundType.none, 1.0F, 1.0F, 100.0F);
        STATIONARY_LAVA =
                (new StillLiquidBlock(11, LiquidType.LAVA))
                        .setData(Tile$SoundType.none, 1.0F, 1.0F, 100.0F);
        SAND = (new SandBlock(12, 18)).setData(Tile$SoundType.gravel, 0.8F, 1.0F, 0.5F);
        GRAVEL = (new SandBlock(13, 19)).setData(Tile$SoundType.gravel, 0.8F, 1.0F, 0.6F);
        var10000 = (new OreBlock(14, 32)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        GOLD_ORE = var1;
        var10000 = (new OreBlock(15, 33)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        IRON_ORE = var1;
        var10000 = (new OreBlock(16, 34)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 3.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        COAL_ORE = var1;
        FIRE = (new FireBlock(50, 31)).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.0F);
        LOG = (new WoodBlock(17)).setData(Tile$SoundType.wood, 1.0F, 1.0F, 2.5F);
        LEAVES = (new LeavesBlock(18, 22)).setData(Tile$SoundType.grass, 1.0F, 0.4F, 0.2F);
        SPONGE = (new SpongeBlock(19)).setData(Tile$SoundType.cloth, 1.0F, 0.9F, 0.6F);
        GLASS = (new GlassBlock(20, 49, false)).setData(Tile$SoundType.metal, 1.0F, 1.0F, 0.3F);
        RED_WOOL = (new Block(21, 64, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        ORANGE_WOOL = (new Block(22, 65, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        YELLOW_WOOL = (new Block(23, 66, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        LIME_WOOL = (new Block(24, 67, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        GREEN_WOOL = (new Block(25, 68, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        AQUA_GREEN_WOOL =
                (new Block(26, 69, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        CYAN_WOOL = (new Block(27, 70, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        // --- New block registrations ---
        DIAMOND_ORE_NEW = new DiamondOreBlock(220); // uses OreBlock subclass
        DIAMOND_BLOCK_NEW = new DiamondBlock(221);
        BEACON = new BeaconBlock(222);
        FURNACE = new FurnaceBlock(223);
        PUMPKIN = new PumpkinBlock(224);
        CACTUS = new CactusBlock(225);
        PISTON = new PistonBlock(226);
        REDSTONE_WIRE = new CircuitBlock(64, "Redstone Dust", CircuitBlock.Kind.WIRE, 138);
        REDSTONE_TORCH = new CircuitBlock(65, "Redstone Torch", CircuitBlock.Kind.TORCH, 140);
        LEVER = new CircuitBlock(66, "Lever", CircuitBlock.Kind.LEVER, 139);
        STONE_BUTTON = new CircuitBlock(67, "Stone Button", CircuitBlock.Kind.BUTTON, 1);
        STONE_PRESSURE_PLATE =
                new CircuitBlock(68, "Stone Pressure Plate", CircuitBlock.Kind.PLATE, 1);
        REPEATER = new CircuitBlock(69, "Redstone Repeater", CircuitBlock.Kind.REPEATER, 143);
        COMPARATOR = new CircuitBlock(70, "Redstone Comparator", CircuitBlock.Kind.COMPARATOR, 145);
        REDSTONE_BLOCK = new CircuitBlock(71, "Block of Redstone", CircuitBlock.Kind.SOURCE, 135);
        REDSTONE_LAMP = new CircuitBlock(72, "Redstone Lamp", CircuitBlock.Kind.LAMP, 136);
        LIT_REDSTONE_LAMP = new CircuitBlock(73, "Redstone Lamp", CircuitBlock.Kind.LIT_LAMP, 137);
        STICKY_PISTON = new CircuitBlock(74, "Sticky Piston", CircuitBlock.Kind.STICKY_PISTON, 150);
        PISTON_HEAD = new CircuitBlock(75, "Piston Head", CircuitBlock.Kind.HEAD, 148);
        TORCH = new CircuitBlock(76, "Torch", CircuitBlock.Kind.NORMAL_TORCH, 200);
        SANDSTONE = new PyramidBlock(80, "Sandstone", 128, PyramidBlock.Shape.CUBE);
        CUT_SANDSTONE = new PyramidBlock(81, "Cut Sandstone", 131, PyramidBlock.Shape.CUBE);
        CHISELED_SANDSTONE =
                new PyramidBlock(82, "Chiseled Sandstone", 132, PyramidBlock.Shape.CUBE);
        SMOOTH_SANDSTONE = new PyramidBlock(83, "Smooth Sandstone", 129, PyramidBlock.Shape.CUBE);
        SANDSTONE_SLAB = new PyramidBlock(84, "Sandstone Slab", 129, PyramidBlock.Shape.SLAB);
        STONE_BRICKS =
                new Block(159, 158, "Stone Bricks").setData(Tile$SoundType.stone, 1f, 1f, 1.5f);
        Block[] stairs = StairBlock.register(SANDSTONE, WOOD, COBBLESTONE, STONE_BRICKS);
        SANDSTONE_STAIRS = stairs[0];
        OAK_STAIRS = stairs[1];
        COBBLESTONE_STAIRS = stairs[2];
        STONE_BRICK_STAIRS = stairs[3];
        ORANGE_TERRACOTTA = new PyramidBlock(86, "Orange Terracotta", 133, PyramidBlock.Shape.CUBE);
        BLUE_TERRACOTTA = new PyramidBlock(87, "Blue Terracotta", 134, PyramidBlock.Shape.CUBE);
        CHEST = new PyramidBlock(88, "Chest", 152, PyramidBlock.Shape.CHEST);
        REDSTONE_ORE = new PyramidBlock(89, "Redstone Ore", 155, PyramidBlock.Shape.ORE);
        PORTAL = new BlockPortal(227); // pick free ID
        LUCKY_BLOCK = new LuckyBlock(229);

        BLUE_WOOL = (new Block(28, 71, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        PURPLE_WOOL = (new Block(29, 72, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        INDIGO_WOOL = (new Block(30, 73, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        VIOLET_WOOL = (new Block(31, 74, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        MAGENTA_WOOL = (new Block(32, 75, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        PINK_WOOL = (new Block(33, 76, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        BLACK_WOOL = (new Block(34, 77, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        GRAY_WOOL = (new Block(35, 78, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        WHITE_WOOL = (new Block(36, 79, "Wool")).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.8F);
        DANDELION = (new FlowerBlock(37, 13)).setData(Tile$SoundType.none, 0.7F, 1.0F, 0.0F);
        ROSE = (new FlowerBlock(38, 12)).setData(Tile$SoundType.none, 0.7F, 1.0F, 0.0F);
        BROWN_MUSHROOM = (new MushroomBlock(39, 29)).setData(Tile$SoundType.none, 0.7F, 1.0F, 0.0F);
        RED_MUSHROOM = (new MushroomBlock(40, 28)).setData(Tile$SoundType.none, 0.7F, 1.0F, 0.0F);
        var10000 = (new MetalBlock(41, 41, 40, 42)).setData(Tile$SoundType.metal, 0.7F, 1.0F, 3.0F);
        var0 = false;
        // Block.java (static init)
        STRUCTURE_ALPHA = new StructureBlock(60, 208, "Structure Alpha", StructureBlock.Type.ALPHA);
        STRUCTURE_BETA = new StructureBlock(61, 209, "Structure Beta", StructureBlock.Type.BETA);
        STRUCTURE_GAMMA = new StructureBlock(62, 210, "Structure Gamma", StructureBlock.Type.GAMMA);
        STRUCTURE_DELTA = new StructureBlock(63, 211, "Structure Delta", StructureBlock.Type.DELTA);
        JUKEBOX = (new Block(212, 212, "Jukebox")).setData(Tile$SoundType.wood, 1.0F, 1.0F, 2.0F);
        WORKBENCH = new WorkbenchBlock(228);
        var1 = var10000;
        var10000.explodes = false;
        GOLD_BLOCK = var1;
        var10000 = (new MetalBlock(42, 44, 43, 39)).setData(Tile$SoundType.metal, 0.7F, 1.0F, 5.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        IRON_BLOCK = var1;
        var10000 = (new SlabBlock(43, true)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 2.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        DOUBLE_SLAB = var1;
        var10000 = (new SlabBlock(44, false)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 2.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        SLAB = var1;
        var10000 = (new Block(45, 7, "Slab")).setData(Tile$SoundType.stone, 1.0F, 1.0F, 2.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        BRICK = var1;
        TNT = (new TNTBlock(46, 8)).setData(Tile$SoundType.cloth, 1.0F, 1.0F, 0.0F);
        BOOKSHELF = (new BookshelfBlock(47, 35)).setData(Tile$SoundType.wood, 1.0F, 1.0F, 1.5F);
        var10000 = (new Block(48, 36, "Bricks")).setData(Tile$SoundType.stone, 1.0F, 1.0F, 1.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        MOSSY_COBBLESTONE = var1;
        var10000 = (new StoneBlock(49, 37)).setData(Tile$SoundType.stone, 1.0F, 1.0F, 10.0F);
        var0 = false;
        var1 = var10000;
        var10000.explodes = false;
        OBSIDIAN = var1;
    }
}
