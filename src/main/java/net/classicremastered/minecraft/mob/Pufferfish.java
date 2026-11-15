package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;

public final class Pufferfish extends Fish {
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
            if (!level.creativeMode) {
                for (Object entity : level.findEntities(this, alertRange)) {
                    if (entity instanceof Player player && canSting(player)) {
                        threatened = true;
                        break;
                    }
                }
            }
            updatePuffState(threatened);
            if (puffState > 0
                    && threatened
                    && CombatRules.difficulty(level) != Difficulty.PEACEFUL) {
                for (Object entity : level.findEntities(this, bb.grow(.15f, .15f, .15f))) {
                    if (entity instanceof Player player && canSting(player)) {
                        int previousHealth = player.health;
                        player.hurt(this, 1 + puffState);
                        if (player.health > 0 && player.health < previousHealth)
                            player.applyPoison(60 * puffState, 25);
                    }
                }
            }
        }
        super.aiStep();
    }

    private static boolean canSting(Player player) {
        return !player.removed && player.health > 0 && !player.isSpectator();
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
        modelName =
                switch (state) {
                    case 1 -> "pufferfish_mid";
                    case 2 -> "pufferfish_full";
                    default -> "pufferfish";
                };
        float size = sizeForState(state);
        resizeFish(size, size);
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
