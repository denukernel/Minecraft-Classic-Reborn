package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.phys.AABB;

public abstract class Fish extends Mob {
    private float swimX;
    private float swimY;
    private float swimZ;
    private int swimTicks;
    private int dryTicks;
    private transient Fish schoolLeader;

    protected Fish(
            Level level, float x, float y, float z, String model, float width, float height) {
        super(level);
        modelName = model;
        textureName = "/mob/fish/" + model + ".png";
        health = 3;
        ai = null;
        footSize = 0;
        makeStepSound = false;
        bobStrength = 0;
        resizeFish(width, height);
        setPos(x, y, z);
    }

    protected final void resizeFish(float width, float height) {
        setSize(width, height);
        heightOffset = height * .5f;
        setPos(x, y, z);
    }

    public final void followSchool(Fish leader) {
        schoolLeader = leader;
    }

    @Override
    protected final boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public final boolean isInWater() {
        return level != null && level.containsLiquid(bb, LiquidType.WATER);
    }

    @Override
    public final boolean isInLava() {
        return level != null && level.containsLiquid(bb, LiquidType.LAVA);
    }

    @Override
    public final float getRenderBrightness(float partial) {
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        float ambient = level.getRenderBrightness(blockX, blockY, blockZ);
        if (!level.isWater(blockX, blockY, blockZ)) return ambient;

        // Water blocks count as opaque for the usual skylight sample.
        int surfaceY = blockY + 1;
        int limit = Math.min(level.depth, blockY + 16);
        while (surfaceY < limit && level.isWater(blockX, surfaceY, blockZ)) surfaceY++;
        if (surfaceY == limit
                || level.isLightBlocker(blockX, surfaceY, blockZ)
                || !level.isLit(blockX, surfaceY, blockZ)) return ambient;
        float skylight = level.getRenderBrightness(blockX, surfaceY, blockZ);
        float transmission = .96f - (surfaceY - y) * .043f;
        return Math.max(ambient, skylight * transmission);
    }

    @Override
    protected final void causeFallDamage(float distance) {}

    @Override
    public void aiStep() {
        if (isInWater()) {
            dryTicks = 0;
            swim();
        } else {
            flop();
        }
    }

    private void swim() {
        if (schoolLeader == null && !(this instanceof Pufferfish) && tickCount % 40 == 0) {
            for (Object entity : level.findEntities(this, bb.grow(3, 2, 3))) {
                if (entity instanceof Fish fish
                        && fish.getClass() == getClass()
                        && fish.health > 0
                        && fish.schoolLeader == null
                        && fish.timeOffs < timeOffs
                        && fish.isInWater()) {
                    followSchool(fish);
                    break;
                }
            }
        }
        if (--swimTicks <= 0 || !canSwim(swimX, swimY, swimZ, .45f)) chooseSwimDirection();
        float pace = .065f;
        xd = xd * .75f + swimX * pace * .25f;
        yd = yd * .75f + swimY * pace * .25f;
        zd = zd * .75f + swimZ * pace * .25f;
        if (!canSwim(xd, yd, zd, 1f)) {
            xd = yd = zd = 0;
            swimTicks = 0;
        }
        move(xd, yd, zd);
        if (xd * xd + zd * zd > .0001f) {
            float targetYaw = (float) Math.toDegrees(Math.atan2(-xd, zd));
            yRot += (float) Math.IEEEremainder(targetYaw - yRot, 360) * .25f;
        }
        float targetPitch = (float) Math.toDegrees(Math.atan2(yd, Math.hypot(xd, zd)));
        xRot += (targetPitch - xRot) * .2f;
    }

    private void chooseSwimDirection() {
        swimTicks = 16 + level.random.nextInt(25);
        Fish leader = schoolLeader;
        if (leader != null
                && !leader.removed
                && leader.health > 0
                && distanceTo(leader) < 8
                && leader.isInWater()) {
            float dx = leader.x - x, dy = leader.y - y, dz = leader.z - z;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > .65f) {
                if (aim(dx / distance, dy / distance, dz / distance)) return;
            } else if (aim(leader.swimX, leader.swimY, leader.swimZ)) {
                return;
            }
        } else {
            schoolLeader = null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            float vertical = level.random.nextFloat() * .9f - .45f;
            if (aim((float) Math.sin(angle), vertical, (float) Math.cos(angle))) return;
        }
        swimX = swimY = swimZ = 0;
    }

    private boolean aim(float dx, float dy, float dz) {
        if (!canSwim(dx, dy, dz, .65f)) return false;
        swimX = dx;
        swimY = dy;
        swimZ = dz;
        return true;
    }

    private boolean canSwim(float dx, float dy, float dz, float distance) {
        float nextX = x + dx * distance;
        float nextY = (bb.y0 + bb.y1) * .5f + dy * distance;
        float nextZ = z + dz * distance;
        if (level.getLiquidAt(nextX, nextY, nextZ) != LiquidType.WATER) return false;
        AABB next = bb.cloneMove(dx * distance, dy * distance, dz * distance);
        return level.getLiquidAt(nextX, next.y1 - .01f, nextZ) == LiquidType.WATER
                && level.getCubes(next).isEmpty();
    }

    private void flop() {
        if (++dryTicks >= 100 && dryTicks % 20 == 0) hurt(null, 1, "drying out");
        if (onGround && tickCount % 10 == 0) {
            xd += (level.random.nextFloat() - .5f) * .16f;
            zd += (level.random.nextFloat() - .5f) * .16f;
            yd = .25f;
            yRot = level.random.nextFloat() * 360;
        }
        xRot *= .8f;
        yd -= .06f;
        move(xd, yd, zd);
        xd *= .8f;
        yd *= .98f;
        zd *= .8f;
    }
}
