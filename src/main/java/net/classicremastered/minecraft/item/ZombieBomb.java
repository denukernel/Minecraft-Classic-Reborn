// File: src/com/mojang/minecraft/item/ZombieBomb.java
package net.classicremastered.minecraft.item;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.particle.TerrainParticle;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.util.MathHelper;

import java.util.Random;

public class ZombieBomb extends Entity {
    private float gravity = 0.04F;
    private float airDrag = 0.98F;
    private float groundFriction = 0.70F;
    private float explosionPower = 3.0F; // smaller than TNT
    private int life; // fuse
    private Entity owner;

    public ZombieBomb(Level level, Entity owner, float x, float y, float z,
                      float yaw, float pitch, float power, int fuse) {
        super(level);
        setSize(0.25F, 0.25F);
        heightOffset = bbHeight / 2.0F;
        setPos(x, y, z);

        this.owner = owner;

        float yawRad = yaw * (float)Math.PI / 180.0F;
        float pitchRad = pitch * (float)Math.PI / 180.0F;

        float vx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float vz =  MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        float vy = -MathHelper.sin(pitchRad);

        this.xd = vx * power;
        this.yd = vy * power + 0.04F;
        this.zd = vz * power;

        this.life = (fuse > 0 ? fuse : 40);
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;

        // physics
        yd -= gravity;
        move(xd, yd, zd);
        xd *= airDrag;
        yd *= airDrag;
        zd *= airDrag;

        if (onGround) {
            xd *= groundFriction;
            zd *= groundFriction;
            yd *= -0.5F;
        }

        // fuse logic
        if (--life > 0) {
            level.particleEngine.spawnParticle(new SmokeParticle(level, x, y + 0.6F, z));
        } else {
            explode();
        }
    }

    private void explode() {
        if (removed) return;
        remove();

        if (level == null) return;
        level.playSound("random/explode", this, 1.0F, 1.0F);

        int radius = (int)explosionPower;
        for (int xx=(int)(x-radius); xx <= (int)(x+radius); xx++) {
            for (int yy=(int)(y-radius); yy <= (int)(y+radius); yy++) {
                for (int zz=(int)(z-radius); zz <= (int)(z+radius); zz++) {
                    if (!level.isInBounds(xx,yy,zz)) continue;

                    float dx = xx+0.5f-x, dy = yy+0.5f-y, dz = zz+0.5f-z;
                    float dist2 = dx*dx+dy*dy+dz*dz;
                    if (dist2 > radius*radius) continue;

                    int id = level.getTile(xx,yy,zz);

                    if (id == Block.COBBLESTONE.id ||
                        id == Block.WOOD.id ||
                        id == Block.GLASS.id) {
                        Block.blocks[id].dropItems(level, xx,yy,zz,0.3F);
                        level.setTile(xx,yy,zz,0);
                        Block.blocks[id].explode(level, xx,yy,zz);

                        level.particleEngine.spawnParticle(new TerrainParticle(
                            level, xx+0.5f,yy+0.5f,zz+0.5f,
                            dx*0.1f,dy*0.1f,dz*0.1f,
                            Block.blocks[id]));
                    }
                }
            }
        }
    }
    @Override
    public void render(net.classicremastered.minecraft.render.TextureManager textureManager, float partial) {
        int tex = textureManager.load("/terrain.png");
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, tex);

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);

        float brightness = 1.0F;
        try {
            brightness = level.getBrightness(bx, by, bz);
        } catch (Throwable ignored) {}

        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        org.lwjgl.opengl.GL11.glTranslatef(xo + (x - xo) * partial - 0.5F,
                                           yo + (y - yo) * partial - 0.5F,
                                           zo + (z - zo) * partial - 0.5F);

        // base TNT cube
        ShapeRenderer sr = ShapeRenderer.instance;
        Block.TNT.renderPreview(sr);

        // flashing overlay (like primed TNT)
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);

        float alpha = ((life / 4 + 1) % 2) * 0.4F;
        if (life <= 16) alpha = ((life + 1) % 2) * 0.6F;
        if (life <= 2) alpha = 0.9F;

        // tint white flash (you can make this green if you want)
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, alpha);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                                          org.lwjgl.opengl.GL11.GL_ONE);

        Block.TNT.renderPreview(sr);

        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LIGHTING);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    @Override
    public boolean isPickable() { return !removed; }
}
