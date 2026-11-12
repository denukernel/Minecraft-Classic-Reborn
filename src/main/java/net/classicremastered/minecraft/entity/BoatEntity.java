package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.BoatItem;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.LiquidBlock;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Spider;
import net.classicremastered.minecraft.model.BoatModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;

import java.util.ArrayList;

public final class BoatEntity extends Entity {
    private static final float WIDTH = 1.375f;
    private static final float HEIGHT = .5625f;
    private static final float MAX_PASSENGER_WIDTH = 1.4f;
    private static final float PADDLE_STEP = (float) Math.PI / 8;
    private static BoatModel model;

    private Entity secondPassenger;
    private int submergedTicks;
    private float turnSpeed;
    private final float[] paddle = new float[2];
    private final float[] previousPaddle = new float[2];
    private float damage;
    private int hurtTicks;
    private int hurtDirection = 1;

    public BoatEntity(Level level, float x, float feetY, float z, float yaw) {
        super(level);
        setSize(WIDTH, HEIGHT);
        heightOffset = HEIGHT * .5f;
        makeStepSound = false;
        setPos(x, feetY + heightOffset, z);
        yRot = yaw;
        resetInterpolation();
    }

    @Override
    public boolean isPickable() {
        return !removed;
    }

    @Override
    public boolean isPushable() {
        return !removed;
    }

    @Override
    public boolean isShootable() {
        return !removed;
    }

    public Entity secondPassenger() {
        return secondPassenger;
    }

    public boolean board(Player player) {
        if (player.sneaking || !canAcceptPassenger(player)) return false;
        player.mount(this);
        if (player.riding != this) return false;
        player.isFlying = false;
        return true;
    }

    @Override
    protected boolean canAcceptPassenger(Entity passenger) {
        return !level.isNetworkMode()
                && !removed
                && secondPassenger == null
                && submergedTicks < 60
                && passenger.riding == null
                && passenger.rider == null
                && !passenger.noPhysics
                && passenger instanceof Mob mob
                && mob.health > 0
                && passenger.bbWidth <= MAX_PASSENGER_WIDTH
                && !(passenger instanceof CrossbowTurret || passenger instanceof Spider)
                && !level.containsLiquid(bb.grow(0, -.24f, 0), LiquidType.LAVA);
    }

    @Override
    protected boolean hasPassenger(Entity passenger) {
        return rider == passenger || secondPassenger == passenger;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        Entity previous = rider;
        if (previous == null) {
            rider = passenger;
        } else {
            if (takesDriverSeat(passenger)) {
                rider = passenger;
                secondPassenger = previous;
            } else {
                secondPassenger = passenger;
            }
            previous.syncRidingPosition();
        }
    }

    private boolean takesDriverSeat(Entity passenger) {
        return passenger instanceof Player && !(rider instanceof Player);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (rider == passenger) {
            rider = secondPassenger;
            secondPassenger = null;
        } else if (secondPassenger == passenger) {
            secondPassenger = null;
        }
        if (rider != null) rider.syncRidingPosition();
    }

    @Override
    protected Vec3D passengerPosition(Entity passenger) {
        return seat(passenger == rider, secondPassenger != null);
    }

    private Vec3D seat(boolean first, boolean twoPassengers) {
        float offset = !twoPassengers ? 0 : first ? .2f : -.6f;
        double angle = Math.toRadians(yRot);
        return new Vec3D(
                x - (float) Math.sin(angle) * offset,
                bb.y0 + .14f,
                z + (float) Math.cos(angle) * offset);
    }

    @Override
    protected AABB ridingBounds(Entity passenger) {
        boolean addingSecond = rider != null && passenger != rider && passenger != secondPassenger;
        boolean twoPassengers = addingSecond || secondPassenger != null;
        boolean promote = addingSecond && takesDriverSeat(passenger);
        Entity first = rider == null || promote ? passenger : rider;
        Entity second = promote ? rider : addingSecond ? passenger : secondPassenger;
        Vec3D front = seat(true, twoPassengers);
        float halfX = Math.max(bbWidth * .5f, Math.abs(front.x - x) + first.bbWidth * .5f);
        float halfZ = Math.max(bbWidth * .5f, Math.abs(front.z - z) + first.bbWidth * .5f);
        float top = Math.max(bb.y1, front.y + first.bbHeight);
        if (second != null) {
            Vec3D back = seat(false, true);
            halfX = Math.max(halfX, Math.abs(back.x - x) + second.bbWidth * .5f);
            halfZ = Math.max(halfZ, Math.abs(back.z - z) + second.bbWidth * .5f);
            top = Math.max(top, back.y + second.bbHeight);
        }
        return new AABB(x - halfX, bb.y0, z - halfZ, x + halfX, top, z + halfZ);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isNetworkMode() || removed) return;
        if (level.containsLiquid(bb, LiquidType.LAVA)) {
            remove();
            return;
        }
        if (hurtTicks > 0) hurtTicks--;
        damage = Math.max(0, damage - 1);

        float surface = waterSurface();
        boolean wet = surface >= bb.y0 - .04f;
        boolean underwater = surface > bb.y1;
        submergedTicks = underwater ? Math.min(60, submergedTicks + 1) : 0;
        if (submergedTicks >= 60) ejectPassengers();

        float drag = underwater ? .45f : wet || !onGround ? .9f : .5f;
        xd *= drag;
        zd *= drag;
        turnSpeed *= drag;
        if (wet) {
            // Let the floor ride just below the surface and damp vertical oscillation.
            float lift = surface - .18f - bb.y0;
            yd = Math.max(-.35f, Math.min(.20f, yd * .65f + lift * .25f));
        } else {
            yd = Math.max(-.6f, yd - .04f);
        }

        steer();
        move(xd, yd, zd);
        collectPassengers();
    }

    private float waterSurface() {
        float surface = Float.NEGATIVE_INFINITY;
        int bottom = (int) Math.floor(bb.y0 - .08f);
        int top = (int) Math.floor(bb.y1 + .02f);
        for (int x = (int) Math.floor(bb.x0); x < bb.x1; x++) {
            for (int z = (int) Math.floor(bb.z0); z < bb.z1; z++) {
                for (int y = top; y >= bottom; y--) {
                    if (level.getLiquid(x, y, z) != LiquidType.WATER) continue;
                    surface = Math.max(surface, LiquidBlock.waterSurface(level, x, y, z));
                    break;
                }
            }
        }
        return surface;
    }

    private void steer() {
        float forward = 0, turn = 0;
        if (rider instanceof Player player
                && player.input != null
                && (player.minecraft == null || player.minecraft.currentScreen == null)) {
            forward = player.input.yya;
            turn = player.input.xxa;
        }
        turnSpeed += turn;
        float oldYaw = yRot;
        yRot += turnSpeed;
        if (rider != null && yRot != oldYaw && !hasRiderClearance(rider)) {
            yRot = oldYaw;
            turnSpeed = 0;
        }
        float thrust = forward > 0 ? .04f : forward < 0 ? -.005f : turn != 0 ? .005f : 0;
        double angle = Math.toRadians(yRot);
        xd -= (float) Math.sin(angle) * thrust;
        zd += (float) Math.cos(angle) * thrust;
        animatePaddle(0, forward > 0 || turn > 0);
        animatePaddle(1, forward > 0 || turn < 0);
    }

    private void animatePaddle(int side, boolean rowing) {
        previousPaddle[side] = paddle[side];
        if (rowing) {
            paddle[side] += PADDLE_STEP;
            if (paddle[side] >= Math.PI * 2) {
                paddle[side] -= (float) (Math.PI * 2);
                previousPaddle[side] -= (float) (Math.PI * 2);
            }
        } else {
            paddle[side] = 0;
        }
    }

    private void collectPassengers() {
        if (blockMap == null) return;
        for (Entity entity :
                blockMap.getEntities(this, bb.grow(.18f, 0, .18f), new ArrayList<>())) {
            if (!entity.isPushable() || entity.removed || hasPassenger(entity)) continue;
            if (!(rider instanceof Player)
                    && !(entity instanceof Player)
                    && canAcceptPassenger(entity)
                    && entity.bb.y0 <= bb.y1) {
                entity.mount(this);
                if (entity.riding == this) continue;
            }
            if (entity.riding != this && !(entity instanceof BoatEntity)) push(entity);
        }
    }

    @Override
    public void hurt(Entity attacker, int amount) {
        if (removed || level.isNetworkMode() || amount <= 0) return;
        hurtTicks = 10;
        hurtDirection = -hurtDirection;
        damage += amount * 10;
        boolean instant = attacker instanceof Player && level.creativeMode;
        if (instant || damage > 40) {
            remove();
            if (!instant) level.addEntity(new DroppedItem(level, x, y, z, BoatItem.ID));
        }
    }

    @Override
    public void render(TextureManager textures, float partial) {
        if (model == null) model = new BoatModel();
        float wobble = Math.max(0, hurtTicks - partial);
        model.render(
                textures,
                xo + (x - xo) * partial,
                yo + (y - yo) * partial - heightOffset,
                zo + (z - zo) * partial,
                renderYaw(partial),
                (float) Math.sin(wobble) * wobble * damage * hurtDirection / 40,
                getRenderBrightness(partial),
                previousPaddle[0] + (paddle[0] - previousPaddle[0]) * partial,
                previousPaddle[1] + (paddle[1] - previousPaddle[1]) * partial);
    }

    public float renderYaw(float partial) {
        return yRotO + (float) Math.IEEEremainder(yRot - yRotO, 360) * partial;
    }
}
