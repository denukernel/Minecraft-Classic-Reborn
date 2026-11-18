package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;
import java.util.ArrayList;

public final class Pufferfish extends Fish {
    private static final long serialVersionUID = 2852893256850357582L;
    public static final short REGISTRY_ID = 90;
    private int puffState;
    private int inflateTicks;
    private int deflateTicks;

    public Pufferfish(Level level, float x, float y, float z) {
        super(level, x, y, z, "pufferfish", .25f, .25f);
        setPuffState(0);
    }

    @Override
    public void aiStep() {
        if (!level.isNetworkMode()) {
            AABB alertRange = bb.grow(2, 2, 2);
            boolean threatened = false;
            for (Object entity : level.findEntities(this, alertRange)) {
                if (canSting(entity)) {
                    threatened = true;
                    break;
                }
            }
            updatePuffState(threatened);
            if (puffState > 0
                    && threatened
                    && CombatRules.difficulty(level) != Difficulty.PEACEFUL) {
                for (Entity entity :
                        level.blockMap.getEntities(
                                this, bb.grow(.15f, .15f, .15f), new ArrayList<>())) {
                    if (canSting(entity) && entity instanceof Mob victim) {
                        int previousHealth = victim.health;
                        victim.hurt(this, 1 + puffState);
                        if (victim.health > 0 && victim.health < previousHealth)
                            victim.applyPoison(60 * puffState, 25);
                    }
                }
            }
        }
        super.aiStep();
    }

    private boolean canSting(Object entity) {
        return entity instanceof Mob mob
                && !mob.removed
                && mob.health > 0
                && !(mob instanceof Fish)
                && (!(mob instanceof Player player)
                        || !level.creativeMode && !player.isSpectator());
    }

    private void updatePuffState(boolean threatened) {
        if (threatened) {
            deflateTicks = 0;
            if (puffState == 0 && canInflateTo(1)) setPuffState(1);
            if (puffState == 1 && ++inflateTicks > 40) {
                if (canInflateTo(2)) setPuffState(2);
                inflateTicks = 40;
            }
        } else {
            inflateTicks = 0;
            if (puffState > 0) {
                deflateTicks++;
                if (puffState == 2 && deflateTicks > 60) setPuffState(1);
                if (puffState == 1 && deflateTicks > 100) setPuffState(0);
            }
        }
    }

    private boolean canInflateTo(int state) {
        float half = sizeForState(state) * .5f;
        AABB inflated = new AABB(x - half, y - half, z - half, x + half, y + half, z + half);
        return level.getCubes(inflated).isEmpty();
    }

    private void setPuffState(int state) {
        puffState = state;
        float size = sizeForState(state);
        resizeFish(size, size);
    }

    public int puffState() {
        return puffState;
    }

    private static float sizeForState(int state) {
        return switch (state) {
            case 1 -> .4f;
            case 2 -> .62f;
            default -> .25f;
        };
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putInt("PuffState", puffState);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        setPuffState(Math.max(0, Math.min(2, tag.getInt("PuffState"))));
    }
}
