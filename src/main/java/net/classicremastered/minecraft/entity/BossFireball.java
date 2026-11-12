package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.util.CreativeModeHelper;

import org.lwjgl.opengl.GL11;

/** A visible, straight-flying rage projectile with swept collision. */
public final class BossFireball extends Entity {
    private static final long serialVersionUID = 1L;
    private final Mob owner;
    private Player deflectedBy;
    private int age;

    public BossFireball(Level level, Mob owner, float x, float y, float z, Vec3D velocity) {
        super(level);
        this.owner = owner;
        setSize(.4f, .4f);
        heightOffset = .2f;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
    }

    @Override
    public boolean isPickable() {
        return !removed && deflectedBy == null;
    }

    public void deflect(Player player) {
        if (!isPickable()
                || player.level != level
                || player.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(player)) return;
        double yaw = Math.toRadians(-player.aimYaw(1) - 180);
        double pitch = Math.toRadians(-player.xRot);
        float speed = .9f;
        xd = (float) (Math.sin(yaw) * Math.cos(pitch)) * speed;
        yd = (float) Math.sin(pitch) * speed;
        zd = (float) (Math.cos(yaw) * Math.cos(pitch)) * speed;
        deflectedBy = player;
        age = 0;
        level.playSound("random/bow", this, .9f, 1.5f);
    }

    @Override
    public void tick() {
        if (removed) return;
        if (owner.removed
                || owner.health <= 0
                || owner.level != level
                || level.creativeMode
                || CombatRules.difficulty(level) == Difficulty.PEACEFUL) {
            remove();
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        Vec3D start = new Vec3D(x, y, z), end = start.add(xd, yd, zd);
        Mob attacker = deflectedBy == null ? owner : deflectedBy;
        var wall = level.clip(start, end, true);
        float distance = wall == null ? Float.MAX_VALUE : start.distance(wall.vec);
        Mob victim = null;
        for (Object o : level.findEntities(this, bb.expand(xd, yd, zd).grow(.3f, .3f, .3f)))
            if (o instanceof Mob m && m != attacker && !m.removed && m.health > 0) {
                var hit = m.bb.grow(.2f, .2f, .2f).clip(start, end);
                if (hit != null && start.distance(hit.vec) < distance) {
                    distance = start.distance(hit.vec);
                    victim = m;
                }
            }
        if (victim != null || wall != null) {
            float length = start.distance(end),
                    fraction = length > .001f ? Math.min(1, distance / length) : 0;
            setPos(x + xd * fraction, y + yd * fraction, z + zd * fraction);
            if (victim != null) {
                victim.hurt(attacker, 6);
                victim.remainingFireTicks = Math.max(victim.remainingFireTicks, 80);
                if (deflectedBy != null
                        && victim instanceof StickFigure boss
                        && boss.ai instanceof StickFigureAI ai) ai.powers.stagger(boss);
            }
            remove();
            level.playSound("random/explode", this, .7f, 1.4f);
            level.explode(attacker, x, y, z, 1.5f);
            return;
        }
        move(xd, yd, zd);
        if (level.particleEngine != null)
            level.particleEngine.spawnParticle(new SmokeParticle(level, x, y, z));
        if (++age >= 120) remove();
    }

    @Override
    public void render(TextureManager tm, float partial) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            GL11.glRotatef((age + partial) * 15, 1, 1, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            for (int layer = 0; layer < 2; layer++) {
                float radius = layer == 0 ? .36f : .2f;
                if (deflectedBy != null)
                    GL11.glColor4f(layer == 0 ? .15f : .8f, .85f, 1, layer == 0 ? .5f : 1);
                else GL11.glColor4f(1, layer == 0 ? .18f : .85f, .02f, layer == 0 ? .5f : 1);
                GL11.glBegin(GL11.GL_TRIANGLES);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2, b = (i + 1) * Math.PI / 2;
                    for (int side : new int[] {-1, 1}) {
                        GL11.glVertex3f(0, radius * side, 0);
                        GL11.glVertex3f(
                                (float) Math.cos(a) * radius, 0, (float) Math.sin(a) * radius);
                        GL11.glVertex3f(
                                (float) Math.cos(b) * radius, 0, (float) Math.sin(b) * radius);
                    }
                }
                GL11.glEnd();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
