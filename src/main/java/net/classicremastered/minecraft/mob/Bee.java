package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;

public final class Bee extends Mob {

    private static final float WIDTH = 14.0F / 16.0F;
    private static final float HEIGHT = 8.0F / 16.0F;
    private static final float FLIGHT_SPEED = 0.10F;

    private transient int flightTicks;
    private transient float targetX;
    private transient float targetY;
    private transient float targetZ;

    public Bee(Level level, float x, float y, float z) {
        super(level);
        this.setSize(WIDTH, HEIGHT);
        this.heightOffset = HEIGHT * 0.5F;
        this.setPos(x, y, z);
        this.modelName = "bee";
        this.textureName = "/mob/bee.png";
        this.ai = null;
        this.footSize = 0.0F;
        this.makeStepSound = false;
        this.bobStrength = 0.0F;
        this.health = 10;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (ai != null) return;

        // Older saves stored the bee's feet as its Y position.
        if (!level.getCubes(bb).isEmpty() && isFree(0.0F, heightOffset, 0.0F)) {
            setPos(x, y + heightOffset, z);
            resetInterpolation();
        }

        if (isInWater()) {
            move(xd * 0.8F, 0.08F, zd * 0.8F);
            flightTicks = 0;
            return;
        }

        if (--flightTicks <= 0 || collision) chooseFlightTarget();
        float dx = targetX - x;
        float dy = targetY - y;
        float dz = targetZ - z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float scale = FLIGHT_SPEED / Math.max(distance, FLIGHT_SPEED);
        xd = xd * 0.65F + dx * scale * 0.35F;
        yd = yd * 0.65F + dy * scale * 0.35F;
        zd = zd * 0.65F + dz * scale * 0.35F;
        if (dx * dx + dz * dz > 0.01F) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float turn = (float) Math.IEEEremainder(yaw - yRot, 360.0F);
            yRot += Math.max(-20.0F, Math.min(20.0F, turn));
        }
        move(xd, yd, zd);
        fallDistance = 0.0F;
    }

    private void chooseFlightTarget() {
        // Take off from nearby ground and tend back down when there is no support nearby.
        boolean nearGround = !level.getCubes(bb.cloneMove(0.0F, -0.12F, 0.0F)).isEmpty();
        boolean aboveGround = !level.getCubes(bb.expand(0.0F, -3.0F, 0.0F)).isEmpty();
        for (int attempt = 0; attempt < 8; attempt++) {
            float dx = (level.random.nextFloat() - 0.5F) * 4.0F;
            float dz = (level.random.nextFloat() - 0.5F) * 4.0F;
            float dy = level.random.nextFloat() - (aboveGround ? 0.5F : 1.5F);
            if (nearGround) dy = 0.6F + level.random.nextFloat();
            AABB route = bb.expand(dx, dy, dz);
            if (route.y0 < 0.0F
                    || route.y1 > level.depth
                    || !level.getCubes(route).isEmpty()
                    || level.containsAnyLiquid(route)) continue;

            targetX = x + dx;
            targetY = y + dy;
            targetZ = z + dz;
            flightTicks = 28 + level.random.nextInt(20);
            return;
        }

        targetX = x;
        targetY = y;
        targetZ = z;
        flightTicks = 8;
    }
}
