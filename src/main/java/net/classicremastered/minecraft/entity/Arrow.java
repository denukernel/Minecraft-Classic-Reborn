package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Arrow extends Entity {
    public static final float FLIGHT_DRAG = .998f;
    public static final float TURRET_DROP = .008f;
    public static final float HALF_WIDTH = .15f;
    public static final float HALF_HEIGHT = .25f;

    public Arrow(Level level1, Entity owner, float x, float y, float z, float unknown0, float unknown1,
            float unknown2) {
        this(level1, owner, x, y, z, unknown0, unknown1, unknown2, false, 0, false);
    }

    public static Arrow stolenHeadRound(Level level, Entity owner, float x, float y, float z, float yaw,
            float pitch, float speed, int damage, boolean bullet) {
        return new Arrow(level, owner, x, y, z, yaw, pitch, speed, true, damage, bullet);
    }

    private Arrow(Level level1, Entity owner, float x, float y, float z, float unknown0, float unknown1,
            float unknown2, boolean stolen, int stolenDamage, boolean stolenBullet) {
        super(level1);

        this.owner = owner;
        this.stolen = stolen;

        setSize(2 * HALF_WIDTH, 2 * HALF_HEIGHT);

        heightOffset = bbHeight / 2.0F;
        damage = 3;

        if (!(owner instanceof Player)) {
            type = 1;
        } else {
            damage = 7;
        }

        if (owner instanceof net.classicremastered.minecraft.mob.CrossbowTurret turret) {
            damage = turret.projectileDamage();
            bullet = turret.usesBullets();
        }
        if (stolen) {
            damage = stolenDamage;
            bullet = stolenBullet;
        }
        heightOffset = 0.25F;

        float unknown3 = MathHelper.cos(-unknown0 * 0.017453292F - 3.1415927F);
        float unknown4 = MathHelper.sin(-unknown0 * 0.017453292F - 3.1415927F);

        unknown0 = MathHelper.cos(-unknown1 * 0.017453292F);
        unknown1 = MathHelper.sin(-unknown1 * 0.017453292F);

        slide = false;

        if (!isTurretProjectile()) {
            xo -= unknown3 * 0.2F;
            zo += unknown4 * 0.2F;
            x -= unknown3 * 0.2F;
            z += unknown4 * 0.2F;
        }

        xd = unknown4 * unknown0 * unknown2;
        yd = unknown1 * unknown2;
        zd = unknown3 * unknown0 * unknown2;

        setPos(x, y, z);

        unknown3 = MathHelper.sqrt(xd * xd + zd * zd);

        yRotO = yRot = (float) (Math.atan2((double) xd, (double) zd) * 180.0D / 3.1415927410125732D);
        xRotO = xRot = (float) (Math.atan2((double) yd, (double) unknown3) * 180.0D / 3.1415927410125732D);

        makeStepSound = false;
    }

    // --- Dodge tuning ---
    private static final int DODGE_PREDICT_TICKS = 12; // how many ticks ahead to check for impact
    private static final int DODGE_SEARCH_RADIUS = 32; // search radius (blocks) for safe teleport
    private static final int DODGE_ATTEMPTS_PER_ENTITY = 24; // tries to find a safe spot
    private static final int DODGE_ENTITY_SCAN_RADIUS = 16; // find Endermen within this grow radius

    @Override
    public void tick() {
        time++;
        if (time > 200) {
            remove();
            return;
        }

        xRotO = xRot;
        yRotO = yRot;

        xo = x;
        yo = y;
        zo = z;

        if (stuckPlayer != null) {
            if (stuckPlayer.health <= 0 || stuckPlayer.removed || stuckPlayer.level != level) {
                remove();
                return;
            }
            syncAttachmentPosition();
            if (blockMap != null) blockMap.moved(this);
            if (remainingFireTicks > 0) remainingFireTicks--;
            updateFire(bb);
            return;
        }
        if (remainingFireTicks > 0) remainingFireTicks--;
        updateFire(bb);

        if (!hasHit && level != null && bb != null) {
            for (Object object : new ArrayList(
                    level.findEntities(this, bb.expand(xd * 6, yd * 6, zd * 6).grow(1, 1, 1))))
                if (object instanceof net.classicremastered.minecraft.mob.StickFigure stick && stick.health > 0
                        && !stick.removed) {
                    net.classicremastered.minecraft.model.Vec3D start = new net.classicremastered.minecraft.model.Vec3D(
                            x, y, z);
                    var end = start.add(xd * 6, yd * 6, zd * 6);
                    if (stick.bb.grow(.2f, .2f, .2f).clip(start, end) != null)
                        stick.evadeArrow(this);
                }
            tryInstantEndermanDodge();
        }

        if (hasHit) {
            stickTime++;

            if (type == 0) {
                if (stickTime >= 300 && Math.random() < 0.009999999776482582D) {
                    remove();
                }
            } else if (type == 1 && stickTime >= 20) {
                remove();
            }
        } else {
            xd *= FLIGHT_DRAG;
            yd *= FLIGHT_DRAG;
            zd *= FLIGHT_DRAG;

            yd -= isTurretProjectile() ? TURRET_DROP
                    : .02f;

            int unknown0 = (int) (MathHelper.sqrt(xd * xd + yd * yd + zd * zd) / 0.2F + 1.0F);
            float x0 = xd / (float) unknown0;
            float y0 = yd / (float) unknown0;
            float z0 = zd / (float) unknown0;

            for (int unknown4 = 0; unknown4 < unknown0 && !collision; unknown4++) {
                AABB unknown5 = bb.expand(x0, y0, z0);
                updateFire(unknown5);

                if (level.getCubes(unknown5).size() > 0) {
                    collision = true;
                    break;
                }

                List blockMapEntitiesList = level.blockMap.getEntities(this, unknown5);
                for (int currentEntity = 0; currentEntity < blockMapEntitiesList.size(); currentEntity++) {
                    Entity entity = (Entity) blockMapEntitiesList.get(currentEntity);
                    if (owner != null && entity == owner.riding && time <= 5)
                        continue;
                    if (owner instanceof net.classicremastered.minecraft.mob.CrossbowTurret
                            && entity instanceof net.classicremastered.minecraft.mob.Mob mob
                            && mob.health <= 0) {
                        continue;
                    }
                    if (!entity.removed
                            && entity.isShootable()
                            && (entity != owner || time > 5 && !isTurretProjectile())) {
                        int previousHealth = entity instanceof Mob mob ? mob.health : 0;
                        if (owner
                                instanceof
                                net.classicremastered.minecraft.mob.CrossbowTurret turret) {
                            turret.hitWithArrow(this, entity, damage);
                        } else {
                            entity.hurt(this, damage);
                        }
                        playImpact();
                        collision = true;
                        if (!isBullet()
                                && entity instanceof Mob mob
                                && mob.health < previousHealth) {
                            if (remainingFireTicks > 0) mob.ignite(100);
                            if (mob instanceof Player player && player.health > 0) {
                                attachTo(player);
                                return;
                            }
                        }
                        remove();
                        return;
                    }
                }

                if (!collision) {
                    bb.move(x0, y0, z0);
                    x += x0;
                    y += y0;
                    z += z0;
                    blockMap.moved(this);
                }
            }

            if (collision) {
                playImpact();
                if (isBullet()) {
                    remove();
                    return;
                }
                hasHit = true;
                xd = yd = zd = 0.0F;
            } else if (isBullet()) {
                emitSmoke(1);
            }

            if (!hasHit) {
                float unknown6 = MathHelper.sqrt(xd * xd + zd * zd);
                yRot = (float) (Math.atan2((double) xd, (double) zd) * 180.0D / 3.1415927410125732D);

                for (xRot = (float) (Math.atan2((double) yd, (double) unknown6) * 180.0D / 3.1415927410125732D); xRot
                        - xRotO < -180.0F; xRotO -= 360.0F) {
                }

                while (xRot - xRotO >= 180.0F)
                    xRotO += 360.0F;
                while (yRot - yRotO < -180.0F)
                    yRotO -= 360.0F;
                while (yRot - yRotO >= 180.0F)
                    yRotO += 360.0F;
            }
        }
    }

    private void updateFire(AABB bounds) {
        if (isBullet()) return;
        if (level.containsLiquid(bounds, LiquidType.WATER)) {
            extinguishFire();
        } else if (level.containsLiquid(bounds, LiquidType.LAVA) || overlapsFire(bounds)) {
            remainingFireTicks = 300;
        }
    }

    private void attachTo(Player player) {
        attachTo(player, level.random.nextLong());
    }

    private void attachTo(Player player, long seed) {
        stuckPlayer = player;
        attachmentSeed = seed;
        hasHit = true;
        time = 0;
        xd = yd = zd = 0;
        syncAttachmentPosition();
        if (blockMap != null) blockMap.moved(this);
        xo = x;
        yo = y;
        zo = z;
    }

    public boolean isStuckTo(Player player) {
        return !removed && stuckPlayer == player;
    }

    public long getAttachmentSeed() {
        return attachmentSeed;
    }

    public static void restoreStuckArrow(Player player, long seed) {
        Level level = player.level;
        Arrow arrow = new Arrow(level, null, player.x, player.y, player.z, 0, 0, 0);
        arrow.attachTo(player, seed);
        level.addEntity(arrow);
    }

    private void syncAttachmentPosition() {
        setPos(stuckPlayer.x, (stuckPlayer.bb.y0 + stuckPlayer.bb.y1) * .5f, stuckPlayer.z);
    }

    public static void renderStuckArrows(
            Player player, HumanoidModel model, TextureManager textures, float scale) {
        if (player.level == null || player.level.blockMap == null || player.health <= 0) return;
        boolean bound = false;
        try {
            for (Entity entity : player.level.blockMap.all) {
                if (!(entity instanceof Arrow arrow)
                        || arrow.stuckPlayer != player
                        || arrow.removed) continue;
                if (!bound) {
                    GL11.glPushAttrib(
                            GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
                    bound = true;
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glEnable(GL11.GL_ALPHA_TEST);
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load("/item/arrows.png"));
                }
                arrow.renderAttached(model, scale);
            }
        } finally {
            if (bound) GL11.glPopAttrib();
        }
    }

    private void renderAttached(HumanoidModel model, float scale) {
        // Keep a fixed part, position and direction across all poses and frames.
        var random = new java.util.Random(attachmentSeed);
        ModelPart part =
                switch (random.nextInt(6)) {
                    case 0 -> model.head;
                    case 1 -> model.body;
                    case 2 -> model.rightArm;
                    case 3 -> model.leftArm;
                    case 4 -> model.rightLeg;
                    default -> model.leftLeg;
                };
        if (!part.render) return;
        Vec3D a = part.vertices[0].vector;
        Vec3D b = part.vertices[6].vector;
        float u = random.nextFloat(), v = random.nextFloat(), w = random.nextFloat();
        float dx = 1 - 2 * u, dy = 1 - 2 * v, dz = 1 - 2 * w;
        GL11.glPushMatrix();
        try {
            part.applyTransform(scale);
            GL11.glTranslatef(
                    (Math.min(a.x, b.x) + Math.abs(b.x - a.x) * u) * scale,
                    (a.y + (b.y - a.y) * v) * scale,
                    (a.z + (b.z - a.z) * w) * scale);
            GL11.glRotatef((float) Math.toDegrees(Math.atan2(dx, dz)) - 90, 0, 1, 0);
            GL11.glRotatef((float) Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), 0, 0, 1);
            GL11.glScalef(.8f, .8f, .8f);
            GL11.glTranslatef(-.37f, 0, 0);
            renderArrowMesh();
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void removeStuckArrows(Player player) {
        if (player.level == null || player.level.blockMap == null) return;
        for (Entity entity : player.level.blockMap.all) {
            if (entity instanceof Arrow arrow && arrow.stuckPlayer == player) arrow.remove();
        }
    }

    private void tryInstantEndermanDodge() {
        if (level == null || bb == null)
            return;

        List<Entity> nearby = level.findEntities(this,
                bb.grow(DODGE_ENTITY_SCAN_RADIUS, DODGE_ENTITY_SCAN_RADIUS, DODGE_ENTITY_SCAN_RADIUS));
        if (nearby == null || nearby.isEmpty())
            return;

        // Make a defensive copy (snapshot) so modifications during iteration don't CME.
        List<Entity> snapshot = new ArrayList<Entity>(nearby);

        try {
            for (Entity e : snapshot) {
                if (!(e instanceof Enderman))
                    continue;
                Enderman em = (Enderman) e;

                if (em.removed || em.health <= 0)
                    continue;
                if (em.bb == null)
                    continue;

                // Predict future arrow AABB positions and see if they intersect the Enderman's
                // current AABB.
                for (int t = 1; t <= DODGE_PREDICT_TICKS; t++) {
                    AABB futureBox = bb.cloneMove(xd * t, yd * t, zd * t);
                    if (futureBox.intersects(em.bb)) {
                        // Found a predicted hit: attempt to teleport the Enderman away before
                        // collision.
                        boolean teleported = teleportEndermanSafely(em, DODGE_SEARCH_RADIUS, DODGE_ATTEMPTS_PER_ENTITY);
                        if (teleported) {
                            // Successful dodge: the arrow will continue and thus miss.
                            // Briefly damp arrow a little to avoid accidental re-hit due to tiny rounding.
                            xd *= 0.9f;
                            yd *= 0.9f;
                            zd *= 0.9f;
                        }
                        // Whether succeeded or not, stop predicting for this enderman this tick.
                        break;
                    }
                }
            }
        } catch (RuntimeException ex) {
            // Defensive fallback: some unexpected mutation happened; swallow to avoid
            // crashing the game tick.
            // (We prefer a missy dodge failure over a hard crash.)
        }
    }

    /**
     * Try to teleport the enderman to a dry, non-colliding column near it. Returns
     * true if teleport succeeded.
     */
    private boolean teleportEndermanSafely(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        // If currently submerged, prefer to teleport out — still apply same checks.
        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int ty = lvl.getHighestTile(tx, tz);

            if (!lvl.isInBounds(tx, ty, tz))
                continue;

            // feet at ty; ensure there's actually a solid block under feet (so we're not
            // hovering inside 0)
            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            // Build candidate AABB from mob size (use mob.bb dimensions when available)
            AABB candidate;
            if (mob.bb != null) {
                float halfX = (mob.bb.x1 - mob.bb.x0) * 0.5f;
                float halfZ = (mob.bb.z1 - mob.bb.z0) * 0.5f;
                float height = (mob.bb.y1 - mob.bb.y0);
                final float EPS = 0.01f;
                candidate = new AABB(cx - halfX + EPS, cy + EPS, cz - halfZ + EPS, cx + halfX - EPS, cy + height - EPS,
                        cz + halfZ - EPS);
            } else {
                // fallback conservative size (enderman-like)
                float half = 0.6f;
                candidate = new AABB(cx - half, cy, cz - half, cx + half, cy + 2.9f, cz + half);
            }

            // Reject if intersects liquid or overlaps blocks/entities
            if (lvl.containsAnyLiquid(candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue; // avoid entity overlap
            if (lvl.getCubes(candidate).size() > 0)
                continue; // avoid block collisions

            // success: teleport there
            mob.setPos(cx, cy, cz);
            mob.xd = mob.yd = mob.zd = 0;
            spawnTeleportParticlesAt(mob, cx, cy, cz);
            return true;
        }
        return false;
    }

    private void spawnTeleportParticlesAt(Entity mob, float cx, float cy, float cz) {
        if (mob.level == null || mob.level.particleEngine == null)
            return;

        float bbWidth = 0.6f;
        float bbHeight = 2.9f;
        try {
            if (mob.bb != null) {
                bbWidth = (mob.bb.x1 - mob.bb.x0) * 0.5f;
                bbHeight = (mob.bb.y1 - mob.bb.y0);
            }
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < 20; i++) {
            float px = cx + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            float py = cy + mob.level.random.nextFloat() * bbHeight;
            float pz = cz + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            mob.level.particleEngine.spawnParticle(new SmokeParticle(mob.level, px, py, pz));
        }
    }

    public boolean isTurretProjectile() {
        return stolen || owner instanceof CrossbowTurret;
    }

    private boolean isBullet() {
        return bullet;
    }

    private void playImpact() {
        if (isBullet()) {
            emitSmoke(3);
            level.playSound("random/bowhit", this, .7f, .55f);
        } else {
            level.playSound("random/bowhit", this, .7f, 1f);
        }
    }

    private void emitSmoke(int count) {
        if (level.particleEngine == null)
            return;
        for (int i = 0; i < count; i++)
            level.particleEngine.spawnParticle(new SmokeParticle(level, x, y, z));
    }

    @Override
    public void render(TextureManager textureManager, float unknown0) {
        if (isBullet() || stuckPlayer != null) return;
        textureId = textureManager.load("/item/arrows.png");

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        float brightness = level.getRenderBrightness((int) x, (int) y, (int) z);

        Vec3D position =
                new Vec3D(
                        xo + (x - xo) * unknown0,
                        yo + (y - yo) * unknown0,
                        zo + (z - zo) * unknown0);
        float yaw = yRotO + (yRot - yRotO) * unknown0;
        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        GL11.glTranslatef(position.x, position.y - this.heightOffset / 2.0F, position.z);
        GL11.glRotatef(yaw - 90.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(xRotO + (xRot - xRotO) * unknown0, 0.0F, 0.0F, 1.0F);
        renderArrowMesh();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
        renderFire(textureManager, position);
    }

    private static void renderArrowMesh() {
        GL11.glRotatef(45.0F, 1.0F, 0.0F, 0.0F);
        ShapeRenderer shapeRenderer = ShapeRenderer.instance;
        float shaftU = 24.0F / 32.0F;

        float unknown1 = 0;
        float unknown2 = 5 / 32f;
        float unknown3 = 0.15625F;

        float unknown4 = 5 / 32f;
        float unknown5 = 10 / 32f;
        float unknown6 = 0.05625F;

        GL11.glScalef(unknown6, unknown6, unknown6);

        GL11.glNormal3f(unknown6, 0.0F, 0.0F);

        shapeRenderer.begin();
        shapeRenderer.vertexUV(-7.0F, -2.0F, -2.0F, 0.0F, unknown4);
        shapeRenderer.vertexUV(-7.0F, -2.0F, 2.0F, unknown3, unknown4);
        shapeRenderer.vertexUV(-7.0F, 2.0F, 2.0F, unknown3, unknown5);
        shapeRenderer.vertexUV(-7.0F, 2.0F, -2.0F, 0.0F, unknown5);
        shapeRenderer.end();

        GL11.glNormal3f(-unknown6, 0.0F, 0.0F);

        shapeRenderer.begin();
        shapeRenderer.vertexUV(-7.0F, 2.0F, -2.0F, 0.0F, unknown4);
        shapeRenderer.vertexUV(-7.0F, 2.0F, 2.0F, unknown3, unknown4);
        shapeRenderer.vertexUV(-7.0F, -2.0F, 2.0F, unknown3, unknown5);
        shapeRenderer.vertexUV(-7.0F, -2.0F, -2.0F, 0.0F, unknown5);
        shapeRenderer.end();

        for (int unknown7 = 0; unknown7 < 4; unknown7++) {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

            GL11.glNormal3f(0.0F, -unknown6, 0.0F);

            shapeRenderer.begin();
            shapeRenderer.vertexUV(-8.0F, -2.0F, 0.0F, 0.0F, unknown1);
            shapeRenderer.vertexUV(8.0F, -2.0F, 0.0F, shaftU, unknown1);
            shapeRenderer.vertexUV(8.0F, 2.0F, 0.0F, shaftU, unknown2);
            shapeRenderer.vertexUV(-8.0F, 2.0F, 0.0F, 0.0F, unknown2);
            shapeRenderer.end();
        }
    }

    private void renderFire(TextureManager textureManager, Vec3D position) {
        if (remainingFireTicks > 0) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
            try {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glBindTexture(
                        GL11.GL_TEXTURE_2D,
                        textureManager.load(
                                net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
                ShapeRenderer shapeRenderer = ShapeRenderer.instance;
                shapeRenderer.begin();
                shapeRenderer.color(1, 1, 1);
                FireBlock.renderFlames(
                        shapeRenderer,
                        position.x - .15f,
                        position.y - .15f,
                        position.z - .15f,
                        .3f,
                        .45f);
                shapeRenderer.end();
            } finally {
                GL11.glPopAttrib();
            }
        }
    }

    @Override
    public void playerTouch(Entity entity) {
        if (isBullet() || stuckPlayer != null || !(entity instanceof Player)) return;
        Player player = (Player) entity;

        // Disable pickup in Creative
        if (player.level != null && player.level.creativeMode) return;

        if (!removed
                && hasHit
                && player.inventory.addResource(
                        net.classicremastered.minecraft.level.itemstack.Equipment.ARROW.id + 256)) {
            TakeEntityAnim takeEntityAnim = new TakeEntityAnim(level, this, player);
            level.addEntity(takeEntityAnim);
            remove();
        }
    }

    @Override
    public void awardKillScore(Entity entity, int score) {
        if (owner != null) owner.awardKillScore(entity, score);
    }

    public static final long serialVersionUID = 0L;

    public float xd;
    public float yd;
    public float zd;

    private float yRot;
    private float xRot;
    private float yRotO;
    private float xRotO;

    private boolean hasHit = false;
    public boolean isEmbedded() {
        return hasHit;
    }

    public void releaseFromBlock() {
        stuckPlayer = null;
        hasHit = false;
        collision = false;
        onGround = false;
        time = 0;
        stickTime = 0;
    }

    private int stickTime = 0;

    private Entity owner;

    public int time = 0;
    private int type = 0;


    private int damage;
    private boolean bullet;
    private boolean stolen;
    private Player stuckPlayer;
    private long attachmentSeed;

    public Entity getOwner() {
        return owner;
    }
}
