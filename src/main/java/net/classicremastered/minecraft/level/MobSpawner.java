// File: src/com/mojang/minecraft/level/MobSpawner.java
package net.classicremastered.minecraft.level;

import java.util.List;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.ProgressBarDisplay;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.mob.*;

/**
 * Mob spawner tuned so monsters spawn more often on the surface at night
 * (rather than mostly in caves). Keeps the safer placement checks.
 */
public final class MobSpawner {
    public Level level;

    public MobSpawner(Level level) {
        this.level = level;
    }

    private boolean isBrightDay() {
        return level != null && level.getDaylightBrightness() >= 0.60f;
    }

    private int countNearby(Class<?> cls, float cx, float cy, float cz, float r, int limit) {
        if (level == null || level.blockMap == null) return 0;
        float r2 = r * r;
        int n = 0;
        List all = level.blockMap.all;
        for (int i = 0; i < all.size(); i++) {
            Object o = all.get(i);
            if (!(o instanceof Entity)) continue;
            if (!cls.isAssignableFrom(o.getClass())) continue;
            Entity e = (Entity) o;
            float dx = e.x - cx, dy = e.y - cy, dz = e.z - cz;
            if (dx * dx + dy * dy + dz * dz <= r2 && ++n >= limit) break;
        }
        return n;
    }

    private boolean allowThisCallToSpawn(boolean brightDay) {
        int denom = brightDay ? 4 : 2;
        return level.random.nextInt(denom) == 0;
    }

    private boolean isFeetOk(int x, int y, int z) {
        if (y <= 0) return false;
        return y > 0 && y < level.depth - 1
            && !level.isSolidTile(x, y, z)
            && level.isSolidTile(x, y - 1, z)
            && level.getLiquid(x, y, z) == LiquidType.NOT_LIQUID
            && level.getLiquid(x, y - 1, z) == LiquidType.NOT_LIQUID;
    }

    private int findSafeFeetY(int x, int guessY, int z) {
        if (isFeetOk(x, guessY, z)) return guessY;
        for (int d = 1; d <= 8; d++) {
            int y1 = guessY - d; if (isFeetOk(x, y1, z)) return y1;
            int y2 = guessY + d; if (isFeetOk(x, y2, z)) return y2;
        }
        return -1;
    }

    // ==== API ================================================================

    public final int spawn(int batches, Entity near, ProgressBarDisplay bar) {
        return spawn(batches, near, bar, true);
    }

    public final int spawn(int batches, Entity near, ProgressBarDisplay bar, boolean allowHostiles) {
        if (this.level == null) return 0;
        try { MobRegistry.bootstrapDefaults(); } catch (Throwable ignored) {}

        int spawned = 0;

        // Herobrine far away
        double dist = Math.max(Math.abs(near != null ? near.x : level.xSpawn),
                               Math.abs(near != null ? near.z : level.zSpawn));
        if (dist >= 6_000_000) {
            if (level.random.nextInt(200) == 0) {
                float px = (near != null ? near.x : level.xSpawn) + (level.random.nextInt(32) - 16);
                float pz = (near != null ? near.z : level.zSpawn) + (level.random.nextInt(32) - 16);
                int py = level.getHighestTile((int)px, (int)pz);

                FakeHerobrine h = new FakeHerobrine(level);
                h.setPos(px + 0.5f, py, pz + 0.5f);

                if (!level.isFree(h.bb)) {
                    h.y += 1.0f;
                    h.setPos(h.x, h.y, h.z);
                }

                level.addEntity(h);
                spawned++;
            }
            return spawned;
        }

        final boolean brightDay = isBrightDay();
        if (!allowThisCallToSpawn(brightDay)) return 0;

        int area;
        final boolean isInfinite = (level instanceof LevelInfiniteTerrain);
        if (isInfinite) {
            final int SIM_RADIUS = 128;
            area = Math.max(1, ((SIM_RADIUS * 2) / 16) * ((SIM_RADIUS * 2) / 16));
        } else {
            area = Math.max(1, (this.level.width * this.level.height * this.level.depth) / 64 / 64 / 64);
        }

        // spawn caps
        final int GLOBAL_CAP    = area * 60;
        final int ZOMBIE_CAP    = area * 20;
        final int SKELETON_CAP  = area * 20;
        final int CREEPER_CAP   = area * 15;
        final int SPIDER_CAP    = area * 18;
        final int PIG_CAP       = area * 35;
        final int SHEEP_CAP     = area * 35;
        final int CHICKEN_CAP   = area * 35;
        final int BABYZ_CAP     = Math.max(2, area * 6);

        final short ENDERMAN_ID = MobRegistry.idOfName("Enderman");
        final int ENDERMAN_CAP  = area * 6;

        final int IRON_GOLEM_CAP = area * 4; // NEW: rare cap

        int countAll      = this.level.countInstanceOf(Mob.class);
        int countZombie   = this.level.countInstanceOf(Zombie.class);
        int countSkel     = this.level.countInstanceOf(Skeleton.class);
        int countCreeper  = this.level.countInstanceOf(Creeper.class);
        int countSpider   = this.level.countInstanceOf(Spider.class);
        int countPig      = this.level.countInstanceOf(Pig.class);
        int countSheep    = this.level.countInstanceOf(Sheep.class);
        int countChicken  = this.level.countInstanceOf(Chicken.class);
        int countBabyZ    = this.level.countInstanceOf(BabyZombie.class);
        int countEnderman = this.level.countInstanceOf(Enderman.class);
        int countIronGolem= this.level.countInstanceOf(IronGolem.class); // NEW

        final int w = this.level.width, h = this.level.height, d = this.level.depth;
        final int SIM_RADIUS = 128;

        for (int b = 0; b < batches; ++b) {
            if (bar != null && batches > 1) bar.setProgress(b * 100 / (batches - 1));
            if (countAll >= GLOBAL_CAP) break;

            int x0, z0;
            if (isInfinite) {
                int px = (near != null) ? (int) near.x : level.xSpawn;
                int pz = (near != null) ? (int) near.z : level.zSpawn;
                x0 = px + level.random.nextInt(SIM_RADIUS * 2) - SIM_RADIUS;
                z0 = pz + level.random.nextInt(SIM_RADIUS * 2) - SIM_RADIUS;
                ((LevelInfiniteTerrain) level).chunks().getOrCreate(x0 >> 4, z0 >> 4);
            } else {
                x0 = this.level.random.nextInt(w);
                z0 = this.level.random.nextInt(h);
            }

            int ySurface = this.level.getHighestTile(x0, z0);
            int yCave = (int)(Math.min(this.level.random.nextFloat(), this.level.random.nextFloat()) * d);
            boolean surfaceLit = this.level.isLit(x0, ySurface, z0);

            for (int cluster = 0; cluster < 2 && countAll < GLOBAL_CAP; ++cluster) {
                int x = x0 + this.level.random.nextInt(6) - this.level.random.nextInt(6);
                int z = z0 + this.level.random.nextInt(6) - this.level.random.nextInt(6);

                if (!isInfinite) {
                    if (x < 0 || z < 0 || x >= w || z >= h) continue;
                } else {
                    ((LevelInfiniteTerrain) level).chunks().getOrCreate(x >> 4, z >> 4);
                }

                boolean tryCave;
                if (brightDay) {
                    tryCave = (!surfaceLit);
                } else {
                    tryCave = (!surfaceLit) && (this.level.random.nextInt(6) == 0);
                }

                int guessY = tryCave ? yCave : ySurface;
                int feetY = findSafeFeetY(x, guessY, z);
                if (feetY < 1 || feetY >= d - 2) continue;

                float cx = x + 0.5f, cy = feetY + 1.0f, cz = z + 0.5f;
                float dx, dy, dz;
                if (near != null) { dx = cx - near.x; dy = cy - near.y; dz = cz - near.z; }
                else { dx = cx - this.level.xSpawn; dy = cy - this.level.ySpawn; dz = cz - this.level.zSpawn; }
                if (dx*dx + dy*dy + dz*dz < (24f * 24f)) {
                    continue;
                }

                short mobId = pickMobId(tryCave, brightDay, this.level.isLit(x, feetY, z), allowHostiles, ENDERMAN_ID);
                if (mobId < 0) continue;

                if ((mobId == 1  && countZombie   >= ZOMBIE_CAP)   ||
                    (mobId == 2  && countSkel     >= SKELETON_CAP) ||
                    (mobId == 3  && countCreeper  >= CREEPER_CAP)  ||
                    (mobId == 23 && countSpider   >= SPIDER_CAP)   ||
                    (mobId == 21 && countPig      >= PIG_CAP)      ||
                    (mobId == 22 && countSheep    >= SHEEP_CAP)    ||
                    (mobId == 25 && countChicken  >= CHICKEN_CAP)  ||
                    (mobId == 40 && countBabyZ    >= BABYZ_CAP)    ||
                    (mobId == 27 && countIronGolem>= IRON_GOLEM_CAP) || // NEW
                    (ENDERMAN_ID >= 0 && mobId == ENDERMAN_ID && countEnderman >= ENDERMAN_CAP))  continue;

                final float D_RAD = 32f;
                final int D_MAX = (mobId == 21 || mobId == 22 || mobId == 25) ? 6 : (mobId == 23) ? 6 : 8;
                Class<?> cls = (mobId == 1)  ? Zombie.class
                                : (mobId == 2)  ? Skeleton.class
                                : (mobId == 3)  ? Creeper.class
                                : (mobId == 23) ? Spider.class
                                : (mobId == 21) ? Pig.class
                                : (mobId == 22) ? Sheep.class
                                : (mobId == 25) ? Chicken.class
                                : (mobId == 40) ? BabyZombie.class
                                : (mobId == 27) ? IronGolem.class  // NEW
                                : Mob.class;

                if (countNearby(cls, cx, cy, cz, D_RAD, D_MAX) >= D_MAX) continue;

                int packMin, packMax;
                boolean isHostile = (mobId == 1 || mobId == 2 || mobId == 3 || mobId == 23 || mobId == 40 || mobId == 27);
                if (!tryCave && !brightDay && isHostile) {
                    packMin = 1;
                    packMax = 2;
                } else {
                    switch (mobId) {
                        case 21: case 22: case 25: packMin = 2; packMax = 3; break; // animals
                        case 23: case 40: case 27: packMin = 1; packMax = 2; break; // spiders, baby z, iron golems
                        default:          packMin = 1; packMax = 2; break;
                    }
                }

                boolean spotLit = this.level.isLit(x, feetY, z);
                boolean daytimeSurfacePassive = (brightDay && spotLit && !tryCave && (mobId == 21 || mobId == 22 || mobId == 25));
                if (!daytimeSurfacePassive) {
                    if (brightDay) {
                        if (this.level.random.nextInt(3) != 0) continue;
                    }
                }

                int pack = packMin + this.level.random.nextInt(Math.max(1, packMax - packMin + 1));
                for (int i = 0; i < pack && countAll < GLOBAL_CAP; ++i) {
                    int sx = x + this.level.random.nextInt(3) - 1;
                    int sz = z + this.level.random.nextInt(3) - 1;

                    if (!isInfinite) {
                        if (sx < 0 || sz < 0 || sx >= w || sz >= h) continue;
                    } else {
                        ((LevelInfiniteTerrain) level).chunks().getOrCreate(sx >> 4, sz >> 4);
                    }

                    int syFeet = findSafeFeetY(sx, feetY, sz);
                    if (syFeet < 1 || syFeet >= d - 2) continue;
                    if (!this.level.isSolidTile(sx, syFeet - 1, sz)) continue;
                    if (this.level.isSolidTile(sx, syFeet, sz))     continue;
                    if (this.level.isSolidTile(sx, syFeet + 1, sz)) continue;
                    if (this.level.getLiquid(sx, syFeet, sz) != LiquidType.NOT_LIQUID) continue;

                    float px = sx + 0.5F, py = syFeet + 0.05F, pz = sz + 0.5F;
                    if (brightDay && !tryCave && (mobId == 1 || mobId == 2 || mobId == 3 || mobId == 40 || mobId == 27)) continue;

                    Mob m = MobRegistry.create(mobId, this.level, px, py, pz);
                    if (m == null || m.getRegistryId() < 0) continue;

                    m.setPos(px, py, pz);

                    final int MAX_LIFT_STEPS = 32;
                    final float LIFT_STEP = 0.0625f;
                    boolean placed = false;

                    for (int lift = 0; lift <= MAX_LIFT_STEPS; lift++) {
                        float tryY = py + lift * LIFT_STEP;
                        if (tryY < 1f) tryY = 1f;
                        m.setPos(px, tryY, pz);
                        if (this.level.isFree(m.bb) && this.level.getCubes(m.bb).size() == 0) {
                            placed = true;
                            break;
                        }
                    }

                    if (!placed) {
                        for (int jitter = 0; jitter < 8 && !placed; jitter++) {
                            float jx = px + (this.level.random.nextFloat() - 0.5f);
                            float jz = pz + (this.level.random.nextFloat() - 0.5f);
                            for (int lift = 0; lift <= MAX_LIFT_STEPS; lift++) {
                                float tryY = py + lift * LIFT_STEP;
                                if (tryY < 1f) tryY = 1f;
                                m.setPos(jx, tryY, jz);
                                if (this.level.isFree(m.bb) && this.level.getCubes(m.bb).size() == 0) {
                                    px = jx; pz = jz;
                                    placed = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!placed) continue;

                    if (m.y < 1f) {
                        m.y = 1f;
                        m.setPos(m.x, m.y, m.z);
                        if (!this.level.isFree(m.bb) || this.level.getCubes(m.bb).size() > 0) continue;
                    }

                    this.level.addEntity(m);
                    spawned++;
                    countAll++;

                    if (mobId == 1)      countZombie++;
                    else if (mobId == 2) countSkel++;
                    else if (mobId == 3) countCreeper++;
                    else if (mobId == 23)countSpider++;
                    else if (mobId == 21)countPig++;
                    else if (mobId == 22)countSheep++;
                    else if (mobId == 25)countChicken++;
                    else if (mobId == 40)countBabyZ++;
                    else if (mobId == 27)countIronGolem++; // NEW
                    else if (ENDERMAN_ID >= 0 && mobId == ENDERMAN_ID) countEnderman++;

                    m.xd += (this.level.random.nextFloat() - 0.5f) * 0.03f;
                    m.zd += (this.level.random.nextFloat() - 0.5f) * 0.03f;
                }
            }
        }

        return spawned;
    }

    private short pickMobId(boolean tryCave, boolean brightDay, boolean spotLit, boolean allowHostiles, short ENDERMAN_ID) {
        if (!tryCave) {
            if (brightDay && spotLit) {
                int r = this.level.random.nextInt(100);
                if (r < 34) return 21;   // Pig
                else if (r < 67) return 22;  // Sheep
                else return 25;          // Chicken
            } else {
                if (!allowHostiles) return -1;
                int r = this.level.random.nextInt(100);
                if (r < 4 && ENDERMAN_ID >= 0) return ENDERMAN_ID;
                else if (r < 47) return 1;   // Zombie
                else if (r < 77) return 2;   // Skeleton
                else if (r < 90) return 23;  // Spider
                else if (r < 95) return 3;   // Creeper
                else if (r < 97) return 40;  // Baby Zombie
                else return 27;              // Iron Golem (~3%)
            }
        } else {
            if (!allowHostiles) return -1;
            int r = this.level.random.nextInt(100);
            if (r < 4 && ENDERMAN_ID >= 0) return ENDERMAN_ID;
            else if (r < 52) return 1;   // Zombie
            else if (r < 82) return 2;   // Skeleton
            else if (r < 95) return 23;  // Spider
            else if (r < 97) return 3;   // Creeper
            else if (r < 99) return 40;  // Baby Zombie
            else return 27;              // Iron Golem (~2% in caves)
        }
    }
}
