package net.classicremastered.minecraft.item;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Arrow extends Entity
{
    public Arrow(Level level1, Entity owner, float x, float y, float z, float unknown0, float unknown1, float unknown2)
    {
        super(level1);

        this.owner = owner;

        setSize(0.3F, 0.5F);

        heightOffset = bbHeight / 2.0F;
        damage = 3;

        if(!(owner instanceof Player))
        {
            type = 1;
        } else {
            damage = 7;
        }

        heightOffset = 0.25F;

        float unknown3 = MathHelper.cos(-unknown0 * 0.017453292F - 3.1415927F);
        float unknown4 = MathHelper.sin(-unknown0 * 0.017453292F - 3.1415927F);

        unknown0 = MathHelper.cos(-unknown1 * 0.017453292F);
        unknown1 = MathHelper.sin(-unknown1 * 0.017453292F);

        slide = false;

        gravity = 1.0F / unknown2;

        xo -= unknown3 * 0.2F;
        zo += unknown4 * 0.2F;

        x -= unknown3 * 0.2F;
        z += unknown4 * 0.2F;

        xd = unknown4 * unknown0 * unknown2;
        yd = unknown1 * unknown2;
        zd = unknown3 * unknown0 * unknown2;

        setPos(x, y, z);

        unknown3 = MathHelper.sqrt(xd * xd + zd * zd);

        yRotO = yRot = (float)(Math.atan2((double)xd, (double)zd) * 180.0D / 3.1415927410125732D);
        xRotO = xRot = (float)(Math.atan2((double)yd, (double)unknown3) * 180.0D / 3.1415927410125732D);

        makeStepSound = false;
    }

    // --- Dodge tuning ---
    private static final int DODGE_PREDICT_TICKS = 12; // how many ticks ahead to check for impact
    private static final int DODGE_SEARCH_RADIUS = 32; // search radius (blocks) for safe teleport
    private static final int DODGE_ATTEMPTS_PER_ENTITY = 24; // tries to find a safe spot
    private static final int DODGE_ENTITY_SCAN_RADIUS = 16; // find Endermen within this grow radius

    @Override
    public void tick()
    {
        time++;

        xRotO = xRot;
        yRotO = yRot;

        xo = x;
        yo = y;
        zo = z;

        // If arrow hasn't hit yet, attempt pre-impact dodge for any Endermen in path
        if(!hasHit && level != null && bb != null) {
            tryInstantEndermanDodge();
        }

        if(hasHit)
        {
            stickTime++;

            if(type == 0)
            {
                if(stickTime >= 300 && Math.random() < 0.009999999776482582D)
                {
                    remove();
                }
            } else if(type == 1 && stickTime >= 20) {
                remove();
            }
        } else {
            xd *= 0.998F;
            yd *= 0.998F;
            zd *= 0.998F;

            yd -= 0.02F * gravity;

            int unknown0 = (int)(MathHelper.sqrt(xd * xd + yd * yd + zd * zd) / 0.2F + 1.0F);

            float x0 = xd / (float)unknown0;
            float y0 = yd / (float)unknown0;
            float z0 = zd / (float)unknown0;

            for(int unknown4 = 0; unknown4 < unknown0 && !collision; unknown4++)
            {
                AABB unknown5 = bb.expand(x0, y0, z0);

                if(level.getCubes(unknown5).size() > 0)
                {
                    collision = true;
                }

                List blockMapEntitiesList = level.blockMap.getEntities(this, unknown5);

                for(int currentEntity = 0; currentEntity < blockMapEntitiesList.size(); currentEntity++)
                {
                    Entity entity = (Entity)blockMapEntitiesList.get(currentEntity);

                    if((entity).isShootable() && (entity != owner || time > 5))
                    {
                        entity.hurt(this, damage);

                        collision = true;

                        remove();

                        return;
                    }
                }

                if(!collision)
                {
                    bb.move(x0, y0, z0);

                    x += x0;
                    y += y0;
                    z += z0;

                    blockMap.moved(this);
                }
            }

            if(collision)
            {
                hasHit = true;

                xd = yd = zd = 0.0F;
            }

            if(!hasHit)
            {
                float unknown6 = MathHelper.sqrt(xd * xd + zd * zd);

                yRot = (float)(Math.atan2((double)xd, (double)zd) * 180.0D / 3.1415927410125732D);

                for(xRot = (float)(Math.atan2((double)yd, (double)unknown6) * 180.0D / 3.1415927410125732D); xRot - xRotO < -180.0F; xRotO -= 360.0F)
                {
                    System.out.println("test");
                    // TODO: ?.
                }

                while(xRot - xRotO >= 180.0F)
                {
                    xRotO += 360.0F;
                }

                while(yRot - yRotO < -180.0F)
                {
                    yRotO -= 360.0F;
                }

                while(yRot - yRotO >= 180.0F)
                {
                    yRotO += 360.0F;
                }
            }
        }
    }

    /**
     * Predict incoming collision path and teleport any Enderman that would be hit.
     * Non-destructive: arrow continues; teleport makes arrow miss.
     *
     * IMPORTANT: use a snapshot copy of the nearby list before iterating because
     * teleporting an Enderman mutates blockMap / entity lists and would otherwise
     * throw ConcurrentModificationException.
     */
    private void tryInstantEndermanDodge() {
        if(level == null || bb == null) return;

        // gather nearby entities (Endermen) inside a reasonable area
        List<Entity> nearby = level.findEntities(this, bb.grow(DODGE_ENTITY_SCAN_RADIUS, DODGE_ENTITY_SCAN_RADIUS, DODGE_ENTITY_SCAN_RADIUS));
        if(nearby == null || nearby.isEmpty()) return;

        // Make a defensive copy (snapshot) so modifications during iteration don't CME.
        List<Entity> snapshot = new ArrayList<Entity>(nearby);

        try {
            for (Entity e : snapshot) {
                if (!(e instanceof Enderman)) continue;
                Enderman em = (Enderman) e;

                if (em.removed || em.health <= 0) continue;
                if (em.bb == null) continue;

                // Predict future arrow AABB positions and see if they intersect the Enderman's current AABB.
                for (int t = 1; t <= DODGE_PREDICT_TICKS; t++) {
                    AABB futureBox = bb.cloneMove(xd * t, yd * t, zd * t);
                    if (futureBox.intersects(em.bb)) {
                        // Found a predicted hit: attempt to teleport the Enderman away before collision.
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
            // Defensive fallback: some unexpected mutation happened; swallow to avoid crashing the game tick.
            // (We prefer a missy dodge failure over a hard crash.)
        }
    }

    /**
     * Try to teleport the enderman to a dry, non-colliding column near it.
     * Returns true if teleport succeeded.
     */
    private boolean teleportEndermanSafely(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null) return false;
        Level lvl = mob.level;

        // If currently submerged, prefer to teleport out — still apply same checks.
        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int ty = lvl.getHighestTile(tx, tz);

            if (!lvl.isInBounds(tx, ty, tz)) continue;

            // feet at ty; ensure there's actually a solid block under feet (so we're not hovering inside 0)
            if (ty <= 0) continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz)) continue;

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
                candidate = new AABB(cx - halfX + EPS, cy + EPS, cz - halfZ + EPS,
                                     cx + halfX - EPS, cy + height - EPS, cz + halfZ - EPS);
            } else {
                // fallback conservative size (enderman-like)
                float half = 0.6f;
                candidate = new AABB(cx - half, cy, cz - half, cx + half, cy + 2.9f, cz + half);
            }

            // Reject if intersects liquid or overlaps blocks/entities
            if (lvl.containsAnyLiquid(candidate)) continue;
            if (!lvl.isFree(candidate)) continue; // avoid entity overlap
            if (lvl.getCubes(candidate).size() > 0) continue; // avoid block collisions

            // success: teleport there
            mob.setPos(cx, cy, cz);
            mob.xd = mob.yd = mob.zd = 0;
            spawnTeleportParticlesAt(mob, cx, cy, cz);
            return true;
        }
        return false;
    }

    private void spawnTeleportParticlesAt(Entity mob, float cx, float cy, float cz) {
        if (mob.level == null || mob.level.particleEngine == null) return;

        float bbWidth = 0.6f;
        float bbHeight = 2.9f;
        try {
            if (mob.bb != null) {
                bbWidth = (mob.bb.x1 - mob.bb.x0) * 0.5f;
                bbHeight = (mob.bb.y1 - mob.bb.y0);
            }
        } catch (Throwable ignored) {}

        for (int i = 0; i < 20; i++) {
            float px = cx + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            float py = cy + mob.level.random.nextFloat() * bbHeight;
            float pz = cz + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            mob.level.particleEngine.spawnParticle(new SmokeParticle(mob.level, px, py, pz));
        }
    }

    @Override
    public void render(TextureManager textureManager, float unknown0)
    {
        textureId = textureManager.load("/item/arrows.png");

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        float brightness = level.getBrightness((int)x, (int)y, (int)z);

        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        GL11.glTranslatef(xo + (x - xo) * unknown0, this.yo + (this.y - this.yo) * unknown0 - this.heightOffset / 2.0F, this.zo + (this.z - this.zo) * unknown0);
        GL11.glRotatef(yRotO + (yRot - yRotO) * unknown0 - 90.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(xRotO + (xRot - xRotO) * unknown0, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(45.0F, 1.0F, 0.0F, 0.0F);

        ShapeRenderer shapeRenderer = ShapeRenderer.instance;

        unknown0 = 0.5F;

        float unknown1 = (float)(0 + type * 10) / 32.0F;
        float unknown2 = (float)(5 + type * 10) / 32.0F;
        float unknown3 = 0.15625F;

        float unknown4 = (float)(5 + type * 10) / 32.0F;
        float unknown5 = (float)(10 + type * 10) / 32.0F;
        float unknown6 = 0.05625F;

        GL11.glScalef(0.05625F, unknown6, unknown6);

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

        for(int unknown7 = 0; unknown7 < 4; unknown7++)
        {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

            GL11.glNormal3f(0.0F, -unknown6, 0.0F);

            shapeRenderer.vertexUV(-8.0F, -2.0F, 0.0F, 0.0F, unknown1);
            shapeRenderer.vertexUV(8.0F, -2.0F, 0.0F, unknown0, unknown1);
            shapeRenderer.vertexUV(8.0F, 2.0F, 0.0F, unknown0, unknown2);
            shapeRenderer.vertexUV(-8.0F, 2.0F, 0.0F, 0.0F, unknown2);
            shapeRenderer.end();
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    @Override
    public void playerTouch(Entity entity)
    {
        Player player = (Player)entity;

        if(hasHit && owner == player && player.arrows < 99)
        {
            TakeEntityAnim takeEntityAnim = new TakeEntityAnim(level, this, player);

            level.addEntity(takeEntityAnim);

            player.arrows++;

            remove();
        }
    }

    @Override
    public void awardKillScore(Entity entity, int score)
    {
        owner.awardKillScore(entity, score);
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

    private int stickTime = 0;

    private Entity owner;

    private int time = 0;
    private int type = 0;

    private float gravity = 0.0F;

    private int damage;

    public Entity getOwner()
    {
        return owner;
    }
}
