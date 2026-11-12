package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.path.PathNavigator;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.nbt.CompoundTag;

final class RoverTurretDrive {
    private static final float PATROL_RADIUS = 4f;
    private static final float LEASH = 12f;
    private static final int STUCK_TICKS = 60;
    private static final float WHEEL_RADIUS = 2.5f / 16f;

    private final PathNavigator navigator;
    private float postX, postY, postZ;
    private Mob target;
    private Mob unreachableTarget;
    private int retryDelay;
    private boolean returning;
    private int patrolDelay = 20;
    private int patrolTicks;
    private float patrolX, patrolZ;
    private int stillTicks;
    private float progressX, progressY, progressZ;
    private float yaw, previousYaw, wheel, previousWheel;

    RoverTurretDrive(Level level, float x, float feetY, float z) {
        navigator = new PathNavigator(level);
        postX = x;
        postY = feetY;
        postZ = z;
        progressX = x;
        progressY = feetY;
        progressZ = z;
    }

    TurretShot tick(CrossbowTurret turret) {
        previousYaw = yaw;
        previousWheel = wheel;
        if (retryDelay > 0 && --retryDelay == 0)
            unreachableTarget = null;
        if (!turret.enabled || !turret.onGround) {
            turret.fall(0f, 0f);
            return null;
        }

        float distanceFromPost = horizontalDistanceSquared(turret.x, turret.z, postX, postZ);
        float heightFromPost = Math.abs(turret.bb.y0 - postY);
        if (target != null && (turret.ammo <= 0 || !turret.canTarget(target) || !withinLeash(target))) {
            beginReturn(turret);
        }
        if (distanceFromPost > LEASH * LEASH)
            beginReturn(turret);

        if (!returning && turret.ammo > 0) {
            TurretShot shot = turret.findShot();
            if (shot != null) {
                resetProgress(turret);
                turret.fall(0f, 0f);
                return turret.onGround ? shot : null;
            }
        }

        if (!returning && target == null) {
            target = turret.ammo > 0 ? findTarget(turret) : null;
            if (target != null) {
                navigator.clearGoal();
                patrolTicks = 0;
                resetProgress(turret);
            } else if (distanceFromPost > PATROL_RADIUS * PATROL_RADIUS || heightFromPost > 1f
                    || distanceFromPost < .16f && heightFromPost >= .25f) {
                beginReturn(turret);
            }
        }

        if (returning) {
            if (distanceFromPost < .4f * .4f && heightFromPost < .25f) {
                finishReturn(turret);
            } else {
                navigator.setGoal(postX, postY, postZ);
            }
        } else if (target != null) {
            navigator.setGoal(target.x, target.bb.y0, target.z);
        } else {
            patrol(turret);
        }

        PathNavigator.Result steering = navigator.steer(turret);
        move(turret, steering);
        if ((target != null || returning) && stalled(turret)) {
            if (returning) {
                postX = turret.x;
                postY = turret.bb.y0;
                postZ = turret.z;
                finishReturn(turret);
            } else {
                unreachableTarget = target;
                retryDelay = 200;
                beginReturn(turret);
            }
        }
        return null;
    }

    private void patrol(CrossbowTurret turret) {
        if (patrolTicks > 0) {
            if (--patrolTicks > 0
                    && horizontalDistanceSquared(turret.x, turret.z, patrolX, patrolZ) >= .16f) {
                return;
            }
            patrolTicks = 0;
            navigator.clearGoal();
        }
        if (patrolDelay > 0) {
            patrolDelay--;
            return;
        }
        patrolDelay = 20 + turret.level.random.nextInt(41);
        float offsetX = turret.level.random.nextInt(9) - 4;
        float offsetZ = turret.level.random.nextInt(9) - 4;
        if (offsetX * offsetX + offsetZ * offsetZ > PATROL_RADIUS * PATROL_RADIUS)
            return;
        patrolX = postX + offsetX;
        patrolZ = postZ + offsetZ;
        if (Pathfinder.canWalkStraight(turret.level, turret, patrolX, postY, patrolZ)) {
            navigator.setGoal(patrolX, postY, patrolZ);
            patrolTicks = 80;
        }
    }

    private Mob findTarget(CrossbowTurret turret) {
        Mob nearest = null;
        float best = LEASH * LEASH;
        for (Object entity : turret.level.findEntities(turret, turret.bb.grow(LEASH, 4f, LEASH))) {
            if (entity instanceof Mob candidate && candidate != unreachableTarget
                    && turret.canTarget(candidate) && withinLeash(candidate)) {
                float distance = turret.distanceToSqr(candidate);
                if (distance <= best) {
                    nearest = candidate;
                    best = distance;
                }
            }
        }
        return nearest;
    }

    private boolean withinLeash(Mob mob) {
        return horizontalDistanceSquared(mob.x, mob.z, postX, postZ) <= LEASH * LEASH;
    }

    private void move(CrossbowTurret turret, PathNavigator.Result steering) {
        float startX = turret.x;
        float startZ = turret.z;
        float dx = 0f;
        float dz = 0f;
        if (steering.forward) {
            yaw += Math.IEEEremainder(steering.targetYaw - yaw, 360f);
            float speed = (target == null ? .08f : .13f) * steering.speedFactor;
            double angle = Math.toRadians(yaw);
            dx = (float) -Math.sin(angle) * speed;
            dz = (float) Math.cos(angle) * speed;
            float current = horizontalDistanceSquared(startX, startZ, postX, postZ);
            float next = horizontalDistanceSquared(startX + dx, startZ + dz, postX, postZ);
            if (next > LEASH * LEASH && next > current) {
                dx = 0f;
                dz = 0f;
            }
        }
        turret.footSize = 1f;
        turret.fall(dx, dz);
        if (turret.onGround) {
            wheel += (float) Math.sqrt(horizontalDistanceSquared(turret.x, turret.z, startX, startZ))
                    / WHEEL_RADIUS;
            if (wheel > 2f * Math.PI) {
                wheel -= 2f * Math.PI;
                previousWheel -= 2f * Math.PI;
            }
        }
    }

    private boolean stalled(CrossbowTurret turret) {
        if (horizontalDistanceSquared(turret.x, turret.z, progressX, progressZ) >= .04f
                || Math.abs(turret.bb.y0 - progressY) >= .25f) {
            resetProgress(turret);
        }
        return ++stillTicks >= STUCK_TICKS;
    }

    private void resetProgress(CrossbowTurret turret) {
        stillTicks = 0;
        progressX = turret.x;
        progressY = turret.bb.y0;
        progressZ = turret.z;
    }

    private void beginReturn(CrossbowTurret turret) {
        if (returning)
            return;
        target = null;
        returning = true;
        patrolTicks = 0;
        navigator.clearGoal();
        resetProgress(turret);
    }

    private void finishReturn(CrossbowTurret turret) {
        returning = false;
        patrolDelay = 20;
        navigator.clearGoal();
        resetProgress(turret);
    }

    private static float horizontalDistanceSquared(float x, float z, float otherX, float otherZ) {
        float dx = x - otherX;
        float dz = z - otherZ;
        return dx * dx + dz * dz;
    }

    float chassisYaw(float partial) {
        return previousYaw + (yaw - previousYaw) * partial;
    }

    float wheelPitch(float partial) {
        return previousWheel + (wheel - previousWheel) * partial;
    }

    void save(CompoundTag tag) {
        tag.putFloat("RoverPostX", postX);
        tag.putFloat("RoverPostY", postY);
        tag.putFloat("RoverPostZ", postZ);
    }
}
