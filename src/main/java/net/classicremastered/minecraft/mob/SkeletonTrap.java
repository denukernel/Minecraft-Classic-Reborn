package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.SkeletonTrapBone;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.nbt.CompoundTag;

public final class SkeletonTrap extends Skeleton {
    private static final long serialVersionUID = 0L;
    public static final short REGISTRY_ID = 81;
    private static final int FALL_TICKS = 10;
    private static final float TRIGGER_REACH = 1.5f;
    private static final SkeletonTrapBone[] BONES = SkeletonTrapBone.values();
    private static final int ALL_BONES = (1 << BONES.length) - 1;
    private int collapseTicks;
    private boolean pitOpened;
    private int brokenBones;

    public SkeletonTrap(Level level, float x, float y, float z) {
        super(level, x, y, z);
        modelName = "skeleton_trap";
        textureName = "/mob/skeleton_trap.png";
        soundHurt = soundDeath;
    }

    @Override
    public void tick() {
        if (pitOpened && health > 0 && !removed && !level.isNetworkMode()) {
            Player player = level.getNearestPlayer(x, y, z, 4f);
            if (holds(player)) player.pinToSkeletonTrap(this);
        }
        if (health > 0
                && !removed
                && !level.isNetworkMode()
                && CombatRules.difficulty(level) == Difficulty.EXTREME) {
            Player player = level.getNearestPlayer(x, bb.y0 + 1.62f, z, 5);
            if (canLure(player)) {
                if (!isFeigning() && onGround && riding == null && rider == null && sees(player)) {
                    collapseTicks = 1;
                    bobStrength = 0;
                    cancelBowAttack();
                    soundIdle = null;
                    level.playSound(soundDeath, this, 1f, 1f);
                } else if (isScattered()
                        && !pitOpened
                        && player.onGround
                        && withinTrapFootprint(player)
                        && Math.abs(player.bb.y0 - bb.y0) <= .6f
                        && sees(player)) {
                    springTrap(player);
                }
            }
        }
        if (collapseTicks > 0 && collapseTicks < FALL_TICKS && ++collapseTicks == FALL_TICKS) {
            float feet = bb.y0;
            setSize(1.65f, .3f);
            setPos(x, feet + bbHeight * .5f, z);
            resetInterpolation();
        }
        super.tick();
    }

    private boolean canLure(Player player) {
        return player != null
                && player.health > 0
                && !player.removed
                && !player.isFlying
                && player.riding == null
                && !CreativeModeHelper.shouldIgnoreTarget(player);
    }

    private boolean sees(Player player) {
        return level.clip(
                        new Vec3D(x, bb.y0 + getEyeHeight(1), z),
                        new Vec3D(player.x, player.bb.y0 + player.getEyeHeight(1), player.z),
                        true)
                == null;
    }

    private void springTrap(Player player) {
        openPit(player);
        // The bones can still grab the player when the ground cannot be excavated.
        pitOpened = true;
        persistent = true;
        player.pinToSkeletonTrap(this);
    }

    private void openPit(Player player) {
        int px = (int) Math.floor(player.x), pz = (int) Math.floor(player.z);
        int surface = (int) Math.floor(player.bb.y0 - .01f);
        if (isGroundCover(level.getTile(px, surface, pz))) surface--;
        for (int x = px - 1; x <= px + 1; x++) {
            for (int z = pz - 1; z <= pz + 1; z++) {
                if (!level.isInBounds(x, surface - 2, z)
                        || !level.isInBounds(x, surface + 1, z)
                        || !isGroundCover(level.getTile(x, surface + 1, z))
                        || !level.isSolidTile(x, surface - 2, z)
                        || !diggable(level.getTile(x, surface, z))
                        || !diggable(level.getTile(x, surface - 1, z))) return;
            }
        }
        for (int x = px - 1; x <= px + 1; x++) {
            for (int z = pz - 1; z <= pz + 1; z++) {
                if (level.getTile(x, surface + 1, z) != 0) level.setTile(x, surface + 1, z, 0);
                level.setTile(x, surface, z, 0);
                level.setTile(x, surface - 1, z, 0);
            }
        }
        setPos(px + .5f, surface - 1 + bbHeight * .5f, pz + .5f);
        resetInterpolation();
    }

    public boolean holds(Player player) {
        return pitOpened
                && health > 0
                && !removed
                && canLure(player)
                && player.level == level
                && withinTrapFootprint(player)
                && player.bb.y0 >= bb.y0 - .5f
                && player.bb.y0 <= bb.y0 + 2.1f;
    }

    private boolean withinTrapFootprint(Player player) {
        return Math.abs(player.x - x) < TRIGGER_REACH && Math.abs(player.z - z) < TRIGGER_REACH;
    }

    private static boolean isGroundCover(int id) {
        return id == 0 || id == Block.SNOW_LAYER.id || id == Block.SHORT_GRASS.id;
    }

    private static boolean diggable(int id) {
        return id == Block.DIRT.id
                || id == Block.GRASS.id
                || id == Block.SNOWY_GRASS.id
                || id == Block.SAND.id
                || id == Block.SNOW_BLOCK.id
                || id == Block.STONE.id;
    }

    public boolean isFeigning() {
        return collapseTicks > 0;
    }

    public float collapseProgress(float partial) {
        return isFeigning() ? Math.min(1f, (collapseTicks - 1 + partial) / (FALL_TICKS - 1)) : 0f;
    }

    public boolean isScattered() {
        return collapseTicks == FALL_TICKS;
    }

    public boolean isBoneBroken(SkeletonTrapBone bone) {
        return (brokenBones & bone.mask()) != 0;
    }

    public MovingObjectPosition pickBone(Vec3D start, Vec3D end, float partial) {
        Vec3D localStart = toBoneSpace(start, partial);
        Vec3D localEnd = toBoneSpace(end, partial);
        float closest = Float.POSITIVE_INFINITY;
        SkeletonTrapBone selected = null;
        Vec3D point = null;
        for (SkeletonTrapBone bone : BONES) {
            if (isBoneBroken(bone)) continue;
            Vec3D hit = bone.intersect(localStart, localEnd);
            if (hit == null) continue;
            float distance = localStart.distanceSquared(hit);
            if (distance < closest) {
                closest = distance;
                selected = bone;
                point = hit;
            }
        }
        if (selected == null) return null;
        float fraction = localStart.distance(point) / localStart.distance(localEnd);
        return new MovingObjectPosition(
                this,
                selected,
                start.add(
                        (end.x - start.x) * fraction,
                        (end.y - start.y) * fraction,
                        (end.z - start.z) * fraction));
    }

    private Vec3D toBoneSpace(Vec3D point, float partial) {
        float dx = point.x - (xo + (x - xo) * partial);
        float dz = point.z - (zo + (z - zo) * partial);
        double angle = Math.toRadians(180f - renderBodyYaw(partial) + rotOffs);
        float localX = (float) (Math.sin(angle) * dz - Math.cos(angle) * dx);
        float localZ = (float) (Math.sin(angle) * dx + Math.cos(angle) * dz);
        float height = point.y - renderFeetY(partial) - renderOffset;
        return new Vec3D(
                localX / SkeletonTrapBone.SCALE,
                (localZ / SkeletonTrapBone.SCALE - SkeletonTrapBone.FALL_OFFSET_Z)
                        / SkeletonTrapBone.FALL_DEPTH,
                height / SkeletonTrapBone.SCALE
                        + SkeletonTrapBone.FALL_OFFSET_Y
                        - SkeletonTrapBone.GROUND_OFFSET);
    }

    public void breakBone(Player player, SkeletonTrapBone bone) {
        if (bone == null
                || !isScattered()
                || removed
                || health <= 0
                || player.level != level
                || level.isNetworkMode()
                || isBoneBroken(bone)) return;
        brokenBones |= bone.mask();
        persistent = true;
        if (brokenBones == ALL_BONES) {
            health = 0;
            die(player);
        } else {
            level.playSound(soundDeath, this, 1f, 1f);
        }
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (!isScattered()) super.hurt(source, damage);
    }

    @Override
    public void aiStep() {
        if (!isFeigning()) {
            super.aiStep();
            return;
        }
        xd = zd = 0;
        travel(0, 0);
    }

    @Override
    public boolean isPushable() {
        return !isFeigning() && super.isPushable();
    }

    @Override
    public void knockback(Entity source, int amount, float dx, float dz) {
        if (!isFeigning()) super.knockback(source, amount, dx, dz);
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putInt("TrapCollapse", collapseTicks);
        tag.putBoolean("TrapOpened", pitOpened);
        tag.putInt("TrapBrokenBones", brokenBones);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        collapseTicks = Math.max(0, Math.min(FALL_TICKS, tag.getInt("TrapCollapse")));
        pitOpened = tag.getBoolean("TrapOpened");
        brokenBones = tag.getInt("TrapBrokenBones") & ALL_BONES;
        if (isFeigning()) {
            soundIdle = null;
            bobStrength = 0;
        }
        if (collapseTicks == FALL_TICKS) {
            setSize(1.65f, .3f);
            setPos(x, y, z);
        }
    }
}
