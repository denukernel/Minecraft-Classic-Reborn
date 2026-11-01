package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.render.ShapeRenderer;
import org.lwjgl.opengl.GL11;
import net.classicremastered.util.MathHelper;
import java.util.List;

public class ThrownEnderPearl extends Entity {
    public float xd, yd, zd;
    private Entity owner;
    private int time = 0;

    public ThrownEnderPearl(Level level, Entity owner, float x, float y, float z, float yaw, float pitch, float speed) {
        super(level);
        this.owner = owner;
        this.setSize(0.25F, 0.25F);
        this.heightOffset = 0.125F;

        float cy = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float cp = MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);

        float lx = sy * cp;
        float ly = sp;
        float lz = cy * cp;

        this.x = x + lx * 0.5f;
        this.y = y + ly * 0.5f;
        this.z = z + lz * 0.5f;

        this.xd = lx * speed;
        this.yd = ly * speed;
        this.zd = lz * speed;

        this.setPos(this.x, this.y, this.z);
    }

    @Override
    public void tick() {
        time++;
        if (time > 100) {
            remove();
            return;
        }

        xo = x;
        yo = y;
        zo = z;

        yd -= 0.04F; // Gravity

        // Move with collision checking
        float prevXd = xd;
        float prevYd = yd;
        float prevZd = zd;
        move(xd, yd, zd);

        // Check if we hit a block (velocity changed) or collided
        boolean hitBlock = (xd != prevXd) || (yd != prevYd) || (zd != prevZd) || this.onGround || this.horizontalCollision;

        if (hitBlock) {
            teleport(prevXd, prevYd, prevZd);
            return;
        }

        // Check if we hit any mob
        AABB checkBB = bb.grow(0.2f, 0.2f, 0.2f);
        List targets = level.blockMap.getEntities(this, checkBB);
        for (Object obj : targets) {
            if (obj instanceof net.classicremastered.minecraft.mob.Mob) {
                net.classicremastered.minecraft.mob.Mob m = (net.classicremastered.minecraft.mob.Mob) obj;
                if (m != owner && m.isPickable() && m.health > 0) {
                    teleport(prevXd, prevYd, prevZd);
                    return;
                }
            }
        }
    }

    private void teleport(float vx, float vy, float vz) {
        if (!level.isNetworkMode() && owner instanceof net.classicremastered.minecraft.player.Player) {
            net.classicremastered.minecraft.player.Player p = (net.classicremastered.minecraft.player.Player) owner;
            
            // Spawn particles at old location
            spawnTeleportParticles(p.x, p.y, p.z);

            // Pull back target position by 0.5 blocks to avoid wall/floor clipping
            float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float tx = x;
            float ty = y;
            float tz = z;
            if (len > 0.01f) {
                tx -= (vx / len) * 0.5f;
                ty -= (vy / len) * 0.5f;
                tz -= (vz / len) * 0.5f;
            }

            // Teleport player
            p.setPos(tx, ty + p.heightOffset, tz);

            // Spawn particles at new location
            spawnTeleportParticles(x, y, z);

            level.playSound("random/explode", p, 1.0F, 1.5F);

            // Hurt player by 5 damage (2.5 hearts)
            if (!level.creativeMode) {
                p.hurt(null, 5);
            }
        }
        remove();
    }

    private void spawnTeleportParticles(float px, float py, float pz) {
        if (level.particleEngine != null) {
            for (int i = 0; i < 15; i++) {
                float rx = (float) (Math.random() - 0.5) * 1.5f;
                float ry = (float) (Math.random() - 0.5) * 1.5f;
                float rz = (float) (Math.random() - 0.5) * 1.5f;
                level.particleEngine.spawnParticle(new net.classicremastered.minecraft.particle.SmokeParticle(level, px + rx, py + ry, pz + rz));
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        net.classicremastered.minecraft.level.itemstack.Item it = net.classicremastered.minecraft.level.itemstack.Item.items[20]; // Ender Pearl
        if (it == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(xo + (x - xo) * partial, yo + (y - yo) * partial + 0.1F, zo + (z - zo) * partial);
        GL11.glRotatef((System.currentTimeMillis() / 10) % 360, 0, 1, 0);
        GL11.glScalef(0.03F, -0.03F, 0.1F);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glTranslatef(-8.0F, -8.0F, 0.0F);
        it.renderIcon(tm, ShapeRenderer.instance);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isPushable() { return false; }
}
