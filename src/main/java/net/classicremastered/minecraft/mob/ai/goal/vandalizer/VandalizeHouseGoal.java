package net.classicremastered.minecraft.mob.ai.goal.vandalizer;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.HouseVandalizer;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.vandalizer.HouseVandalizerAI;

public final class VandalizeHouseGoal extends MobGoal<HouseVandalizerAI> {

    public VandalizeHouseGoal(HouseVandalizerAI ai) {
        super(ai);
    }

    @Override
    public void tick() {
        if (CombatRules.difficulty(ai.level) == Difficulty.PEACEFUL) {
            ai.attackTarget = null;
            ai.running = false;
            return;
        }
        if (tearCooldown > 0)
            tearCooldown--;
        if (ai.hasRetaliationTarget()) {
            ai.defaultUpdate();
            return;
        }
        if (--searchTicks <= 0) {
            searchTicks = 80;
            home = null;
            float best = 32 * 32;
            for (Object object : ai.level.findEntities(ai.mob, ai.mob.bb.grow(32, 16, 32)))
                if (object instanceof Villager v && v.hasHome && !v.removed && v.health > 0) {
                    float dx = v.homeX - ai.mob.x, dz = v.homeZ - ai.mob.z, d = dx * dx + dz * dz;
                    if (d < best) {
                        home = v;
                        best = d;
                    }
                }
        }
        if (home == null || home.removed || !home.hasHome) {
            ai.defaultUpdate();
            return;
        }
        ai.attackTarget = null;
        ai.running = true;
        ai.runMultiplier = 1.05f;
        if (--approachTicks <= 0 || !hasApproach)
            chooseApproach();
        if (hasApproach)
            ai.navigateTo(approachX, approachY, approachZ, ai.runSpeed);
        else {
            ai.defaultUpdate();
            return;
        }
        if (tearCooldown == 0) {
            if (throwNearby(Block.GLASS) || throwNearby(Block.WOOD)) {
                tearCooldown = 60;
                approachTicks = 0;
            }
        }
    }

    private boolean throwNearby(Block material) {
        int px = (int) Math.floor(ai.mob.x), py = (int) Math.floor(ai.mob.bb.y0 + 1),
                pz = (int) Math.floor(ai.mob.z);
        for (int y = py - 1; y <= py + 2; y++)
            for (int z = pz - 2; z <= pz + 2; z++)
                for (int x = px - 2; x <= px + 2; x++) {
                    if (Math.abs(x - home.homeX) > home.homeRadius + 2
                            || Math.abs(z - home.homeZ) > home.homeRadius + 2
                            || ai.level.getTile(x, y, z) != material.id)
                        continue;
                    if (((HouseVandalizer) ai.mob).tearHouseBlock(x, y, z, material))
                        return true;
                }
        return false;
    }

    private int searchTicks, tearCooldown;
    private Villager home;
    private int approachTicks;
    private float approachX, approachY, approachZ;
    private boolean hasApproach;
    private void chooseApproach() {
        approachTicks = 40;
        hasApproach = false;
        float best = Float.MAX_VALUE;
        int hx = (int) Math.floor(home.homeX), hz = (int) Math.floor(home.homeZ), feet = (int) Math.floor(home.homeY);
        int radius = Math.min(8, (int) Math.ceil(home.homeRadius) + 2);
        for (int dz = -radius; dz <= radius; dz++)
            for (int dx = -radius; dx <= radius; dx++) {
                int x = hx + dx, z = hz + dz;
                boolean wood = false;
                for (int y = feet; y <= feet + 2; y++)
                    if (ai.level.getTile(x, y, z) == Block.WOOD.id
                            || ai.level.getTile(x, y, z) == Block.GLASS.id) {
                        wood = true;
                        break;
                    }
                if (!wood)
                    continue;
                for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ax = x + side[0], az = z + side[1];
                    float score = (ax + .5f - ai.mob.x) * (ax + .5f - ai.mob.x)
                            + (az + .5f - ai.mob.z) * (az + .5f - ai.mob.z);
                    if (score >= best)
                        continue;
                    for (int dy : new int[]{0, -1, 1}) {
                        var path = net.classicremastered.minecraft.path.Pathfinder.findPath(ai.level, ai.mob, ax,
                                feet + dy,
                                az, 512);
                        if (path != null && !path.isEmpty()) {
                            var last = path.get(path.size() - 1);
                            if (last.x != ax || last.z != az)
                                continue;
                            approachX = ax + .5f;
                            approachY = feet + dy;
                            approachZ = az + .5f;
                            best = score;
                            hasApproach = true;
                            break;
                        }
                    }
                }
            }
    }
}
