package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;

import java.util.Random;

public class LiquidBlock extends Block {

    private static final int FALLING_WATER = 8;
    private static final int NO_WATER = 9;
    private static final int SLOPE_RANGE = 4;
    private static final int[] STEP_X = { -1, 1, 0, 0 };
    private static final int[] STEP_Z = { 0, 0, -1, 1 };

    protected LiquidType type;
    protected int stillId;
    protected int movingId;

    protected LiquidBlock(int var1, LiquidType var2) {
        super(var1, "Liquid");
        this.type = var2;
        this.textureId = 14;
        if (var2 == LiquidType.LAVA) {
            this.textureId = 30;
        }

        Block.liquid[var1] = true;
        this.movingId = var1;
        this.stillId = var1 + 1;
        float var4 = 0.01F;
        float var3 = 0.1F;
        this.setBounds(var4 + 0.0F, 0.0F - var3 + var4, var4 + 0.0F, var4 + 1.0F, 1.0F - var3 + var4, var4 + 1.0F);
        if (var2 == LiquidType.WATER) {
            this.setBounds(0, 0, 0, 1, 8.0F / 9.0F, 1);
        }
        this.setPhysics(true);

    }

    public final boolean isCube() {
        return false;
    }

    @Override
    public void onAdded(Level level, int x, int y, int z) {
        if (this.id == this.movingId || this.type == LiquidType.LAVA
                || this.type == LiquidType.WATER && usesLevelledFlow(level)) {
            level.addToTickNextTick(x, y, z, this.id);
        }
    }

    @Override
    public final void onPlace(Level level, int x, int y, int z) {
        onAdded(level, x, y, z);
    }

    @Override
    public void update(Level level, int x, int y, int z, Random random) {
        if (level.isNetworkMode()) {
            return;
        }
        if (this.type == LiquidType.LAVA) {
            tryIgniteNearLava(level, x, y, z, random);
        }

        if (!usesLevelledFlow(level)) {
            updateClassic(level, x, y, z);
        } else if (this.type == LiquidType.WATER) {
            updateWater(level, x, y, z);
        } else {
            updateFinite(level, x, y, z);
        }
    }

    public static boolean usesLevelledFlow(Level level) {
        return level.minecraft == null || level.minecraft.settings == null || level.minecraft.settings.finiteWater;
    }

    protected final void tryIgniteNearLava(Level level, int x, int y, int z, Random random) {
        // Fuel directly above lava can catch on its top face even when its sides are enclosed.
        if (tryIgniteFuelTop(level, x, y + 1, z) || tryIgniteFuelTop(level, x - 1, y, z)
                || tryIgniteFuelTop(level, x + 1, y, z) || tryIgniteFuelTop(level, x, y, z - 1)
                || tryIgniteFuelTop(level, x, y, z + 1)) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int rx = x + random.nextInt(3) - 1;
            int ry = y + random.nextInt(3) - 1;
            int rz = z + random.nextInt(3) - 1;
            if (level.isInBounds(rx, ry, rz) && level.getTile(rx, ry, rz) == 0
                    && FireBlock.hasFuel(level, rx, ry, rz)) {
                level.setTile(rx, ry, rz, Block.FIRE.id);
            }
        }
    }

    private static boolean tryIgniteFuelTop(Level level, int x, int y, int z) {
        return level.isInBounds(x, y + 1, z) && FireBlock.isFlammable(level.getTile(x, y, z))
                && level.getTile(x, y + 1, z) == 0 && level.setTile(x, y + 1, z, Block.FIRE.id);
    }

    private void updateClassic(Level level, int x, int y, int z) {
        boolean spread = spreadTo(level, x, y - 1, z, 0);
        if (!spread) {
            spread = spreadTo(level, x - 1, y, z, 0) | spreadTo(level, x + 1, y, z, 0) | spreadTo(level, x, y, z - 1, 0)
                    | spreadTo(level, x, y, z + 1, 0);
        }
        if (!spread && level.getTile(x, y, z) == this.movingId) {
            level.setTileNoUpdate(x, y, z, this.stillId);
        }
    }

    protected final void updateWater(Level level, int x, int y, int z) {
        int current = level.getFlowLevel(x, y, z);
        int target = current == 0 ? 0 : waterFromNeighbors(level, x, y, z);
        if (target == NO_WATER) {
            level.setTile(x, y, z, 0);
            return;
        }
        if (target != current) {
            setWaterFlow(level, x, y, z, target);
        }

        boolean waterBelow = level.getLiquid(x, y - 1, z) == LiquidType.WATER;
        boolean flowedDown = trySpreadWater(level, x, y - 1, z, FALLING_WATER);
        if (flowedDown ? countWaterSources(level, x, y, z) >= 3 : target == 0 || !waterBelow) {
            int next = target == FALLING_WATER ? 1 : target + 1;
            if (next < FALLING_WATER) {
                spreadWaterSides(level, x, y, z, next);
            }
        }

        if (target == 0 && level.getTile(x, y, z) == this.movingId) {
            level.setTileNoUpdate(x, y, z, this.stillId);
        }
    }

    private int waterFromNeighbors(Level level, int x, int y, int z) {
        if ((level.isSolidTile(x, y - 1, z) || getNeighborLevel(level, x, y - 1, z) == 0)
                && countWaterSources(level, x, y, z) >= 2) {
            return 0;
        }
        if (level.getLiquid(x, y + 1, z) == LiquidType.WATER) {
            return FALLING_WATER;
        }

        int best = FALLING_WATER;
        for (int i = 0; i < STEP_X.length; i++) {
            int neighbor = getNeighborLevel(level, x + STEP_X[i], y, z + STEP_Z[i]);
            best = Math.min(best, neighbor == FALLING_WATER ? 0 : neighbor);
        }
        return best < FALLING_WATER - 1 ? best + 1 : NO_WATER;
    }

    private int countWaterSources(Level level, int x, int y, int z) {
        int count = 0;
        for (int i = 0; i < STEP_X.length; i++) {
            if (getNeighborLevel(level, x + STEP_X[i], y, z + STEP_Z[i]) == 0) {
                count++;
            }
        }
        return count;
    }

    private void spreadWaterSides(Level level, int x, int y, int z, int flow) {
        int best = SLOPE_RANGE + 1;
        int directions = 0;
        for (int i = 0; i < STEP_X.length; i++) {
            int nx = x + STEP_X[i];
            int nz = z + STEP_Z[i];
            if (!canTraverseWater(level, nx, y, nz)) {
                continue;
            }
            int distance = findWaterDrop(level, nx, y, nz, i ^ 1, 0);
            if (distance < best) {
                best = distance;
                directions = 0;
            }
            if (distance == best) {
                directions |= 1 << i;
            }
        }
        for (int i = 0; i < STEP_X.length; i++) {
            if ((directions & (1 << i)) != 0) {
                trySpreadWater(level, x + STEP_X[i], y, z + STEP_Z[i], flow);
            }
        }
    }

    private int findWaterDrop(Level level, int x, int y, int z, int from, int distance) {
        if (canFlowInto(level, x, y - 1, z) || level.getLiquid(x, y - 1, z) == LiquidType.WATER) {
            return distance;
        }
        int best = SLOPE_RANGE + 1;
        if (distance < SLOPE_RANGE) {
            for (int i = 0; i < STEP_X.length; i++) {
                int nx = x + STEP_X[i];
                int nz = z + STEP_Z[i];
                if (i != from && canTraverseWater(level, nx, y, nz)) {
                    best = Math.min(best, findWaterDrop(level, nx, y, nz, i ^ 1, distance + 1));
                }
            }
        }
        return best;
    }

    private boolean canTraverseWater(Level level, int x, int y, int z) {
        int flow = getNeighborLevel(level, x, y, z);
        return flow == 99 ? canFlowInto(level, x, y, z) : flow > 0;
    }

    private boolean trySpreadWater(Level level, int x, int y, int z, int flow) {
        int existing = getNeighborLevel(level, x, y, z);
        if (existing != 99) {
            if (existing == 0 || existing == FALLING_WATER || existing == flow
                    || (flow != FALLING_WATER && existing < flow)) {
                return false;
            }
        } else if (!level.isInBounds(x, y, z) || !canFlowInto(level, x, y, z) || !level.setTile(x, y, z, this.movingId)
                || level.getTile(x, y, z) != this.movingId) {
            return false;
        }
        setWaterFlow(level, x, y, z, flow);
        level.addToTickNextTick(x, y, z, this.movingId);
        return true;
    }

    private void setWaterFlow(Level level, int x, int y, int z, int flow) {
        level.setWaterFlowLevel(x, y, z, flow);
        level.updateNeighborsAt(x, y, z, this.movingId);
    }

    private void updateFinite(Level level, int x, int y, int z) {
        int currentLevel = level.getFlowLevel(x, y, z);
        int maxLevel = 4;
        int targetLevel = currentLevel == 0 ? 0 : calculateTargetFlowLevel(level, x, y, z);
        if (targetLevel >= maxLevel) {
            level.setTile(x, y, z, 0);
            return;
        }

        if (targetLevel != currentLevel) {
            level.flowLevels.put(Level.getCoordKey(x, y, z), (byte) targetLevel);
            level.updateNeighborsAt(x, y, z, this.movingId);
        }

        boolean flowedDown = spreadTo(level, x, y - 1, z, 1);
        if (!flowedDown && level.isInBounds(x, y - 1, z)) {
            int downLevel = getNeighborLevel(level, x, y - 1, z);
            if (downLevel > 1 && downLevel < maxLevel) {
                level.flowLevels.put(Level.getCoordKey(x, y - 1, z), (byte) 1);
                level.addToTickNextTick(x, y - 1, z, this.movingId);
                flowedDown = true;
            } else if (downLevel == 1 && targetLevel > 0) {
                flowedDown = true;
            }
        }

        if (!flowedDown && targetLevel + 1 < maxLevel) {
            int nextLevel = targetLevel + 1;
            spreadTo(level, x - 1, y, z, nextLevel);
            spreadTo(level, x + 1, y, z, nextLevel);
            spreadTo(level, x, y, z - 1, nextLevel);
            spreadTo(level, x, y, z + 1, nextLevel);
        }

        if (targetLevel == 0 && level.getTile(x, y, z) == this.movingId) {
            level.setTileNoUpdate(x, y, z, this.stillId);
        }
    }

    private int calculateTargetFlowLevel(Level level, int x, int y, int z) {
        if (getNeighborLevel(level, x, y + 1, z) < 99) {
            return 1;
        }

        int minLevel = Math.min(getNeighborLevel(level, x - 1, y, z), getNeighborLevel(level, x + 1, y, z));
        minLevel = Math.min(minLevel, getNeighborLevel(level, x, y, z - 1));
        minLevel = Math.min(minLevel, getNeighborLevel(level, x, y, z + 1));
        return minLevel + 1;
    }

    private int getNeighborLevel(Level level, int x, int y, int z) {
        if (level.getLiquid(x, y, z) != this.type) {
            return 99;
        }
        return level.getFlowLevel(x, y, z);
    }

    private boolean spreadTo(Level level, int x, int y, int z, int flowLevel) {
        if (!canFlowInto(level, x, y, z) || !level.setTile(x, y, z, this.movingId)
                || level.getTile(x, y, z) != this.movingId) {
            return false;
        }
        if (flowLevel > 0) {
            level.flowLevels.put(Level.getCoordKey(x, y, z), (byte) flowLevel);
        }
        return true;
    }

    protected final boolean canFlowInto(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z)) {
            return false;
        }
        int tile = level.getTile(x, y, z);
        if (tile != 0
                && (this.type != LiquidType.WATER
                        || (tile != Block.FIRE.id
                                && !(Block.blocks[tile] instanceof FlowerBlock plant
                                        && plant.canBeWashedAway())))) {
            return false;
        }
        if (this.type == LiquidType.WATER) {
            for (int sx = x - 2; sx <= x + 2; sx++) {
                for (int sy = y - 2; sy <= y + 2; sy++) {
                    for (int sz = z - 2; sz <= z + 2; sz++) {
                        if (level.getTile(sx, sy, sz) == Block.SPONGE.id) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public int getLightValue() {
        return this.type == LiquidType.LAVA ? 15 : 0; // max brightness for lava
    }

    @Override
    public final boolean canRenderSide(Level level, int x, int y, int z, int side) {
        // Use proper infinite bounds check
        if (!level.isInBounds(x, y, z)) {
            return false;
        }

        int id = level.getTile(x, y, z);
        if (id == this.movingId || id == this.stillId) {
            return false; // skip self-adjacent liquids
        }

        return super.canRenderSide(level, x, y, z, side);
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        if (this.type != LiquidType.WATER) {
            return super.render(level, x, y, z, renderer);
        }

        // A solid ceiling can only hide a water surface that reaches its underside.
        boolean top = level.isInBounds(x, y + 1, z) && !level.isWater(x, y + 1, z);
        boolean north = canRenderSide(level, x, y, z - 1, 2);
        boolean south = canRenderSide(level, x, y, z + 1, 3);
        boolean west = canRenderSide(level, x - 1, y, z, 4);
        boolean east = canRenderSide(level, x + 1, y, z, 5);
        boolean bottom = canRenderSide(level, x, y - 1, z, 0);
        if (bottom) {
            colorWater(renderer, level, x, y - 1, z, 0.5F);
            super.renderInside(renderer, x, y, z, 0);
        }
        if (!top && !north && !south && !west && !east) {
            return bottom;
        }

        float nw = cornerWaterHeight(level, x, y, z, -1, -1);
        float ne = cornerWaterHeight(level, x, y, z, 1, -1);
        float sw = cornerWaterHeight(level, x, y, z, -1, 1);
        float se = cornerWaterHeight(level, x, y, z, 1, 1);
        float u0 = (textureId % 16) / 16.0F;
        float v0 = (textureId / 16) / 16.0F;
        float uvSize = 15.99F / 256.0F;
        boolean renderTop =
                top
                        && (Math.min(Math.min(nw, ne), Math.min(sw, se)) < 1.0F
                                || canRenderSide(level, x, y + 1, z, 1));
        if (renderTop) {
            colorWater(renderer, level, x, y + 1, z, 1.0F);
            renderer.vertexUV(x + 1, y + se, z + 1, u0 + uvSize, v0 + uvSize);
            renderer.vertexUV(x + 1, y + ne, z, u0 + uvSize, v0);
            renderer.vertexUV(x, y + nw, z, u0, v0);
            renderer.vertexUV(x, y + nw, z, u0, v0);
            renderer.vertexUV(x + 1, y + se, z + 1, u0 + uvSize, v0 + uvSize);
            renderer.vertexUV(x, y + nw, z, u0, v0);
            renderer.vertexUV(x, y + sw, z + 1, u0, v0 + uvSize);
            renderer.vertexUV(x, y + sw, z + 1, u0, v0 + uvSize);
        }
        if (north) {
            colorWater(renderer, level, x, y, z - 1, 0.8F);
            renderWaterWall(renderer, x, z, nw, x + 1, z, ne, y, u0, v0, uvSize);
        }
        if (south) {
            colorWater(renderer, level, x, y, z + 1, 0.8F);
            renderWaterWall(renderer, x + 1, z + 1, se, x, z + 1, sw, y, u0, v0, uvSize);
        }
        if (west) {
            colorWater(renderer, level, x - 1, y, z, 0.6F);
            renderWaterWall(renderer, x, z + 1, sw, x, z, nw, y, u0, v0, uvSize);
        }
        if (east) {
            colorWater(renderer, level, x + 1, y, z, 0.6F);
            renderWaterWall(renderer, x + 1, z, ne, x + 1, z + 1, se, y, u0, v0, uvSize);
        }
        return renderTop || bottom || north || south || west || east;
    }

    public static float waterSurface(Level level, int x, int y, int z) {
        if (level.getLiquid(x, y, z) != LiquidType.WATER) return Float.NEGATIVE_INFINITY;
        return y
                + (cornerWaterHeight(level, x, y, z, -1, -1)
                                + cornerWaterHeight(level, x, y, z, 1, -1)
                                + cornerWaterHeight(level, x, y, z, -1, 1)
                                + cornerWaterHeight(level, x, y, z, 1, 1))
                        * .25f;
    }

    /** Surface at the local horizontal coordinates of a water block. */
    public static float waterSurfaceAt(
            Level level, int x, int y, int z, float localX, float localZ) {
        float nw = cornerWaterHeight(level, x, y, z, -1, -1);
        float se = cornerWaterHeight(level, x, y, z, 1, 1);
        // The top face runs from southeast to northwest across its diagonal.
        if (localX >= localZ) {
            float ne = cornerWaterHeight(level, x, y, z, 1, -1);
            return y + nw + (ne - nw) * localX + (se - ne) * localZ;
        }
        float sw = cornerWaterHeight(level, x, y, z, -1, 1);
        return y + nw + (sw - nw) * localZ + (se - sw) * localX;
    }

    private static float cornerWaterHeight(Level level, int x, int y, int z, int dx, int dz) {
        float total = 0;
        int samples = 0;
        boolean levelled = usesLevelledFlow(level);
        boolean connected = level.isWater(x + dx, y, z) || level.isWater(x, y, z + dz);
        for (int ox = 0; ox <= 1; ox++) {
            int nx = x + ox * dx;
            for (int oz = 0; oz <= 1; oz++) {
                if (ox == 1 && oz == 1 && !connected) continue;
                int nz = z + oz * dz;
                if (level.getLiquid(nx, y, nz) == LiquidType.WATER) {
                    if (level.getLiquid(nx, y + 1, nz) == LiquidType.WATER) {
                        return 1.0F;
                    }
                    int flow = levelled ? level.getFlowLevel(nx, y, nz) : 0;
                    int amount = flow == FALLING_WATER ? 8 : 8 - flow;
                    int weight = amount == 8 ? 10 : 1;
                    total += amount * weight;
                    samples += weight;
                } else if (!level.isSolidTile(nx, y, nz)) {
                    samples++;
                }
            }
        }
        return samples == 0 ? 0 : total / (samples * 9.0F);
    }

    private static void colorWater(
            ShapeRenderer renderer, Level level, int x, int y, int z, float shade) {
        float light = level.getRenderBrightness(x, y, z) * shade;
        renderer.color(light, light, light);
    }

    private static void renderWaterWall(ShapeRenderer renderer, int x0, int z0, float h0, int x1, int z1, float h1,
            int y, float u, float v, float size) {
        renderer.vertexUV(x0, y + h0, z0, u, v + (1 - h0) * size);
        renderer.vertexUV(x1, y + h1, z1, u + size, v + (1 - h1) * size);
        renderer.vertexUV(x1, y, z1, u + size, v + size);
        renderer.vertexUV(x0, y, z0, u, v + size);
    }

    public final void renderInside(ShapeRenderer shapeRenderer, int x, int y, int z, int side) {
        super.renderInside(shapeRenderer, x, y, z, side);
    }

    public final boolean isOpaque() {
        return true;
    }

    @Override
    protected final float getBrightness(Level level, int x, int y, int z) {
        return level.getRenderBrightness(x, y, z);
    }

    public final boolean isSolid() {
        return false;
    }

    public final LiquidType getLiquidType() {
        return this.type;
    }

    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        if (reactWithOtherLiquid(level, x, y, z, changedId)) {
            return;
        }
        level.addToTickNextTick(x, y, z, this.movingId);
    }

    protected final boolean reactWithOtherLiquid(Level level, int x, int y, int z, int changedId) {
        Block changed = Block.blocks[changedId];
        if (changed != null) {
            LiquidType other = changed.getLiquidType();
            if ((this.type == LiquidType.WATER && other == LiquidType.LAVA)
                    || (this.type == LiquidType.LAVA && other == LiquidType.WATER)) {
                return level.setTile(x, y, z, Block.STONE.id);
            }
        }

        return false;
    }

    @Override
    public final int getTickDelay() {
        return 5;
    }

    public final void dropItems(Level var1, int var2, int var3, int var4, float var5) {
    }

    public final void onBreak(Level var1, int var2, int var3, int var4) {
    }

    public final int getDropCount() {
        return 0;
    }

    public final int getRenderPass() {
        return this.type == LiquidType.WATER ? 1 : 0;
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return null;
    }
}
