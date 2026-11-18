package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.path.Pathfinder;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import java.nio.FloatBuffer;
import net.classicremastered.minecraft.render.ShapeRenderer;
import org.lwjgl.BufferUtils;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class ThrownEnderPearl extends Entity {
    private static final long serialVersionUID = -1856424994218433223L;
    private static final FloatBuffer CAMERA_MATRIX = BufferUtils.createFloatBuffer(16);
    private final Entity owner;
    private int time;
    private transient Vec3D lastSafeLanding;
    private transient Vec3D defenderLanding;
    private static final float AIR_DRAG = .99f;
    private static final float GRAVITY = .03f;
    private static final float SIZE = .25f;

    public ThrownEnderPearl(Level level, Player owner, float speed) {
        this(level, owner, owner.x, owner.y, owner.z, owner.aimDirection(1), speed);
    }

    public ThrownEnderPearl(
            Level level,
            Entity owner,
            float x,
            float y,
            float z,
            float yaw,
            float pitch,
            float speed) {
        this(level, owner, x, y, z, launchDirection(yaw, pitch), speed);
    }

    private ThrownEnderPearl(
            Level level, Entity owner, float x, float y, float z, Vec3D direction, float speed) {
        super(level);
        this.owner = owner;
        setSize(SIZE, SIZE);
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;

        xd = direction.x * speed;
        yd = direction.y * speed;
        zd = direction.z * speed;
    }

    private static Vec3D launchDirection(float yaw, float pitch) {
        float azimuth = -yaw * 0.017453292F - (float) Math.PI;
        float elevation = -pitch * 0.017453292F;
        float horizontal = MathHelper.cos(elevation);
        return new Vec3D(
                MathHelper.sin(azimuth) * horizontal,
                MathHelper.sin(elevation),
                MathHelper.cos(azimuth) * horizontal);
    }

    public static boolean prepareForDefender(Defender defender, Vec3D feet) {
        Level level = defender.level;
        Vec3D start = defenderLaunchPoint(defender);
        for (int ticks = 6; ticks <= 46; ticks += 4) {
            // Solve the same drag and gravity used by tick(), then trace the shot.
            float travel = (float) ((1 - Math.pow(AIR_DRAG, ticks)) / (1 - AIR_DRAG));
            float gravity = GRAVITY * (ticks - travel) / (1 - AIR_DRAG);
            Vec3D velocity =
                    new Vec3D(
                            (feet.x - start.x) / travel,
                            (feet.y - .15f - start.y + gravity) / travel,
                            (feet.z - start.z) / travel);
            if (velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z
                            > 1.5f * 1.5f
                    || !canReachLanding(level, start, velocity, feet, ticks)) continue;
            defender.beginPearlThrow(feet, velocity, ticks);
            return true;
        }
        return false;
    }

    public static boolean releaseForDefender(
            Defender defender, Vec3D feet, Vec3D velocity, int ticks) {
        Level level = defender.level;
        Vec3D start = defenderLaunchPoint(defender);
        if (!canReachLanding(level, start, velocity, feet, ticks)) return false;
        ThrownEnderPearl pearl =
                new ThrownEnderPearl(level, defender, start.x, start.y, start.z, velocity, 1);
        pearl.defenderLanding = feet;
        level.addEntity(pearl);
        level.playSound("item/ender_pearl/throw", defender, .5f, 1f);
        return true;
    }

    private static Vec3D defenderLaunchPoint(Defender defender) {
        return new Vec3D(defender.x, defender.bb.y0 + defender.getEyeHeight(1), defender.z);
    }

    private static boolean canReachLanding(
            Level level, Vec3D start, Vec3D velocity, Vec3D feet, int ticks) {
        float half = SIZE / 2;
        AABB bounds =
                new AABB(
                        start.x - half,
                        start.y - half,
                        start.z - half,
                        start.x + half,
                        start.y + half,
                        start.z + half);
        for (int i = 0; i < ticks; i++) {
            Vec3D end = start.add(velocity.x, velocity.y, velocity.z);
            float hit =
                    firstBlockHit(
                            level, bounds.expand(velocity.x, velocity.y, velocity.z), start, end);
            if (Float.isFinite(hit)) {
                float fraction = hitFraction(start, end, hit);
                return nearLanding(
                        feet,
                        start.add(
                                velocity.x * fraction,
                                velocity.y * fraction,
                                velocity.z * fraction));
            }
            bounds.move(velocity.x, velocity.y, velocity.z);
            float drag = level.containsLiquid(bounds, LiquidType.WATER) ? .8f : AIR_DRAG;
            start = end;
            velocity = new Vec3D(velocity.x * drag, velocity.y * drag - GRAVITY, velocity.z * drag);
        }
        return false;
    }

    private static boolean nearLanding(Vec3D feet, Vec3D impact) {
        return Math.abs(feet.y - impact.y) < .25f && feet.distanceSquared(impact) <= .85f * .85f;
    }

    @Override
    public void tick() {
        if (++time > 100 || owner == null || owner.removed || owner.level != level) {
            remove();
            return;
        }

        xo = x;
        yo = y;
        zo = z;
        Vec3D start = new Vec3D(x, y, z);
        Vec3D end = start.add(xd, yd, zd);
        AABB sweep = bb.expand(xd, yd, zd);
        if (tracksLanding()
                && owner instanceof Player player
                && level.getCubes(landingBox(player, start)).isEmpty()) {
            lastSafeLanding = start;
        }

        float nearest = firstBlockHit(level, sweep, start, end);
        Mob victim = null;
        for (Entity target : level.blockMap.getEntities(this, sweep.grow(.1f, .1f, .1f))) {
            if (target != owner
                    && !(owner.riding instanceof BoatEntity boat && boat.carries(target))
                    && target instanceof Mob mob
                    && !(mob instanceof Player)
                    && mob.isPickable()
                    && mob.health > 0) {
                float distance = distanceToHit(mob.bb.grow(.1f, .1f, .1f), start, end);
                if (distance < nearest) {
                    nearest = distance;
                    victim = mob;
                }
            }
        }
        if (Float.isFinite(nearest)) {
            float fraction = hitFraction(start, end, nearest);
            onImpact(victim, start.add(xd * fraction, yd * fraction, zd * fraction));
            return;
        }

        setPos(end.x, end.y, end.z);
        float drag = level.containsLiquid(bb, LiquidType.WATER) ? 0.8F : AIR_DRAG;
        xd *= drag;
        yd = yd * drag - GRAVITY;
        zd *= drag;
    }

    protected boolean tracksLanding() {
        return true;
    }

    protected void onImpact(Mob victim, Vec3D impact) {
        if (owner instanceof Defender defender) {
            remove();
            Vec3D feet = defenderLanding;
            if (feet != null
                    && victim == null
                    && !level.isNetworkMode()
                    && defender.health > 0
                    && nearLanding(feet, impact)
                    && Pathfinder.canStandAt(level, defender, feet.x, feet.y, feet.z)
                    && level.findEntities(defender, landingBox(defender, feet)).stream()
                            .noneMatch(
                                    e -> e instanceof Mob mob && mob.health > 0 && !mob.removed)) {
                moveOwner(defender, feet);
                if (defender.ai instanceof net.classicremastered.minecraft.mob.DefenderAI ai)
                    ai.clearNavigation();
            }
            return;
        }
        if (victim != null && !level.isNetworkMode()) {
            victim.hurt(owner, 1);
        }
        teleport();
    }

    protected Item projectileItem() {
        return Item.ENDER_PEARL;
    }

    private static float distanceToHit(AABB box, Vec3D start, Vec3D end) {
        if (box.contains(start)) return 0;
        var hit = box.clip(start, end);
        return hit == null ? Float.POSITIVE_INFINITY : start.distanceSquared(hit.vec);
    }

    private static float firstBlockHit(Level level, AABB sweep, Vec3D start, Vec3D end) {
        float nearest = Float.POSITIVE_INFINITY;
        for (Object cube : level.getCubes(sweep)) {
            nearest = Math.min(nearest, distanceToHit((AABB) cube, start, end));
        }
        return nearest;
    }

    private static float hitFraction(Vec3D start, Vec3D end, float distanceSquared) {
        return distanceSquared == 0
                ? 0
                : (float) Math.sqrt(distanceSquared / start.distanceSquared(end));
    }

    private static AABB landingBox(Mob mob, Vec3D feet) {
        float halfWidth = mob.bbWidth * 0.5F;
        return new AABB(
                feet.x - halfWidth,
                feet.y,
                feet.z - halfWidth,
                feet.x + halfWidth,
                feet.y + mob.bbHeight,
                feet.z + halfWidth);
    }

    private void teleport() {
        remove();
        if (level.isNetworkMode()
                || !(owner instanceof Player player)
                || player.health <= 0
                || lastSafeLanding == null) {
            return;
        }

        // Use the last clear point on the incoming path, with room for the player's full hitbox.
        AABB landing = landingBox(player, lastSafeLanding);
        if (!level.getCubes(landing).isEmpty()) {
            return;
        }

        moveOwner(player, lastSafeLanding);
        if (player.yd < 0) player.yd = 0;
    }

    private void moveOwner(Mob mob, Vec3D feet) {
        spawnTeleportParticles(mob.x, mob.y, mob.z);
        mob.unmount();
        mob.ySlideOffset = 0;
        mob.setPos(feet.x, feet.y + mob.heightOffset, feet.z);
        mob.bb = landingBox(mob, feet);
        mob.xo = mob.x;
        mob.yo = mob.y;
        mob.zo = mob.z;
        mob.fallDistance = 0;
        mob.onGround = false;
        if (mob instanceof Defender) mob.xd = mob.yd = mob.zd = 0;
        if (mob.blockMap != null) mob.blockMap.moved(mob);
        spawnTeleportParticles(mob.x, mob.y, mob.z);
        level.playSound("item/ender_pearl/teleport", mob, 1, 1);
    }

    private void spawnTeleportParticles(float px, float py, float pz) {
        if (level.particleEngine != null) {
            for (int i = 0; i < 15; i++) {
                float rx = (level.random.nextFloat() - 0.5F) * 1.5F;
                float ry = (level.random.nextFloat() - 0.5F) * 1.5F;
                float rz = (level.random.nextFloat() - 0.5F) * 1.5F;
                level.particleEngine.spawnParticle(
                        new SmokeParticle(level, px + rx, py + ry, pz + rz));
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        Item item = projectileItem();
        if (item == null || item.getTexture() == null) return;

        CAMERA_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, CAMERA_MATRIX);
        float rightX = CAMERA_MATRIX.get(0) * .25f;
        float rightY = CAMERA_MATRIX.get(4) * .25f;
        float rightZ = CAMERA_MATRIX.get(8) * .25f;
        float upX = CAMERA_MATRIX.get(1) * .25f;
        float upY = CAMERA_MATRIX.get(5) * .25f;
        float upZ = CAMERA_MATRIX.get(9) * .25f;
        float px = xo + (x - xo) * partial;
        float py = yo + (y - yo) * partial;
        float pz = zo + (z - zo) * partial;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_TEXTURE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load(item.getTexture()));
            ShapeRenderer shape = ShapeRenderer.instance;
            shape.begin();
            shape.vertexUV(px - rightX - upX, py - rightY - upY, pz - rightZ - upZ, 0, 1);
            shape.vertexUV(px + rightX - upX, py + rightY - upY, pz + rightZ - upZ, 1, 1);
            shape.vertexUV(px + rightX + upX, py + rightY + upY, pz + rightZ + upZ, 1, 0);
            shape.vertexUV(px - rightX + upX, py - rightY + upY, pz - rightZ + upZ, 0, 0);
            shape.end();
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
