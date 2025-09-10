package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Random;

public final class Renderer {

    public Minecraft minecraft;
    public float fogColorMultiplier = 1.0F;
    public boolean displayActive = false;
    public float fogEnd = 0.0F;
    public HeldBlock heldBlock;
    public int levelTicks;
    public Entity entity = null;
    public Random random = new Random();
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
    public float fogRed;
    public float fogBlue;
    public float fogGreen;

    public Renderer(Minecraft mc) {
        this.minecraft = mc;
        this.heldBlock = new HeldBlock(mc);
    }

    public Vec3D getPlayerVector(float var1) {
        Player p = this.minecraft.player;
        float x = p.xo + (p.x - p.xo) * var1;
        float y = p.yo + (p.y - p.yo) * var1;
        float z = p.zo + (p.z - p.zo) * var1;
        return new Vec3D(x, y, z);
    }

    public void hurtEffect(float partial) {
        Player pl = this.minecraft.player;
        float t = (float) pl.hurtTime - partial;

        // ===== Death tilt =====
        if (pl.health <= 0) {
            partial += (float) pl.deathTime;
            GL11.glRotatef(40.0F - 8000.0F / (partial + 200.0F), 0.0F, 0.0F, 1.0F);
        }

        // ===== Hurt wobble effect =====
        if (t >= 0.0F) {
            t = MathHelper.sin((t /= (float) pl.hurtDuration) * t * t * t * (float) Math.PI);
            float dir = pl.hurtDir;
            GL11.glRotatef(-pl.hurtDir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-t * 14.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(dir, 0.0F, 1.0F, 0.0F);
        }
    }

    public void applyBobbing(float partial) {
        Player p = this.minecraft.player;
        float walk = p.walkDist - p.walkDistO;
        walk = p.walkDist + walk * partial;
        float bob = p.oBob + (p.bob - p.oBob) * partial;
        float tilt = p.oTilt + (p.tilt - p.oTilt) * partial;
        GL11.glTranslatef(MathHelper.sin(walk * 3.1415927F) * bob * 0.5F,
                -Math.abs(MathHelper.cos(walk * 3.1415927F) * bob), 0.0F);
        GL11.glRotatef(MathHelper.sin(walk * 3.1415927F) * bob * 3.0F, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(Math.abs(MathHelper.cos(walk * 3.1415927F + 0.2F) * bob) * 5.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(tilt, 1.0F, 0.0F, 0.0F);
    }

    public void renderUnderwaterOverlay(float partial) {
        Player pl = this.minecraft.player;
        Level lvl = this.minecraft.level;
        if (pl == null || lvl == null)
            return;

        Block b = Block.blocks[lvl.getTile((int) pl.x, (int) (pl.y + 0.12F), (int) pl.z)];
        if (b == null)
            return;

        LiquidType l = b.getLiquidType();
        if (l == LiquidType.NOT_LIQUID)
            return;

        // === Setup ortho projection ===
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, minecraft.width, minecraft.height, 0.0, -1.0, 1.0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        // State
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Use the actual animated atlas texture
        int texId = minecraft.textureManager.load("/terrain.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        // Lookup the animated tile position
        int texIndex = (l == LiquidType.WATER) ? Block.WATER.textureId : Block.LAVA.textureId;
        int u0 = (texIndex % 16) * 16;
        int v0 = (texIndex / 16) * 16;
        float u1 = u0 / 256.0F;
        float v1 = v0 / 256.0F;
        float u2 = (u0 + 16) / 256.0F;
        float v2 = (v0 + 16) / 256.0F;

        if (l == LiquidType.WATER) {
            GL11.glColor4f(0.6F, 0.6F, 1.0F, 0.6F);
        } else {
            GL11.glColor4f(1.0F, 0.4F, 0.2F, 0.7F);
        }

        // Draw fullscreen quad with correct UVs (no manual scroll)
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2f(minecraft.width, 0);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2f(minecraft.width, minecraft.height);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(0, minecraft.height);
        GL11.glEnd();

        // restore state
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glColor4f(1F, 1F, 1F, 1F); // reset
    }

    public final void setLighting(boolean on) {
        if (!on) {
            GL11.glDisable(2896);
            GL11.glDisable(16384);
        } else {
            GL11.glEnable(2896);
            GL11.glEnable(16384);
            GL11.glEnable(2903);
            GL11.glColorMaterial(1032, 5634);
            float global = 0.7F;
            float ambient = 0.3F;
            Vec3D dir = (new Vec3D(0.0F, -1.0F, 0.5F)).normalize();
            GL11.glLight(16384, 4611, this.createBuffer(dir.x, dir.y, dir.z, 0.0F));
            GL11.glLight(16384, 4609, this.createBuffer(ambient, ambient, ambient, 1.0F));
            GL11.glLight(16384, 4608, this.createBuffer(0.0F, 0.0F, 0.0F, 1.0F));
            GL11.glLightModel(2899, this.createBuffer(global, global, global, 1.0F));
        }
    }

    public final void enableGuiMode() {
        int w = this.minecraft.width * 240 / this.minecraft.height;
        int h = this.minecraft.height * 240 / this.minecraft.height;
        GL11.glClear(256);
        GL11.glMatrixMode(5889);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, (double) w, (double) h, 0.0D, 100.0D, 300.0D);
        GL11.glMatrixMode(5888);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -200.0F);
    }

    public void updateFog() {
        Level lvl = this.minecraft.level;
        Player pl = this.minecraft.player;
        GL11.glFog(2918, this.createBuffer(this.fogRed, this.fogBlue, this.fogGreen, 1.0F));
        GL11.glNormal3f(0.0F, -1.0F, 0.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Block b = Block.blocks[lvl.getTile((int) pl.x, (int) (pl.y + 0.12F), (int) pl.z)];
        if (b != null && b.getLiquidType() != LiquidType.NOT_LIQUID) {
            LiquidType l = b.getLiquidType();
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP2);

            if (l == LiquidType.WATER) {
                // Vision capped at ~8 blocks
                GL11.glFogf(GL11.GL_FOG_DENSITY, 0.35F);
                GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, this.createBuffer(0.2F, 0.35F, 0.8F, 1.0F));
            } else if (l == LiquidType.LAVA) {
                // Vision capped at ~3 blocks
                GL11.glFogf(GL11.GL_FOG_DENSITY, 0.7F);
                GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, this.createBuffer(0.8F, 0.25F, 0.1F, 1.0F));
            }
        } else {
            // Normal air fog
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
            GL11.glFogf(GL11.GL_FOG_START, 0.0F);
            GL11.glFogf(GL11.GL_FOG_END, this.fogEnd);
            GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, this.createBuffer(1.0F, 1.0F, 1.0F, 1.0F));
        }

        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    public FloatBuffer createBuffer(float a, float b, float c, float d) {
        this.buffer.clear();
        this.buffer.put(a).put(b).put(c).put(d);
        this.buffer.flip();
        return this.buffer;
    }

    /**
     * Render the held item (non-block) with swing animation. Special-case swords
     * (SwordItem): arc slash + wrist twist + slight lunge.
     */
    public void renderHeldItem() {
        if (minecraft == null || minecraft.player == null)
            return;

        int selId = minecraft.player.inventory.getSelected();
        if (selId < 256)
            return; // only items here

        int itemId = selId - 256;
        net.classicremastered.minecraft.level.itemstack.Item[] itemsArr = net.classicremastered.minecraft.level.itemstack.Item.items;
        if (itemId < 0 || itemId >= itemsArr.length)
            return;

        net.classicremastered.minecraft.level.itemstack.Item it = itemsArr[itemId];
        if (it == null)
            return;

        // --- detect sword by class (safer than checking name strings) ---
        boolean isSword = (it instanceof net.classicremastered.minecraft.level.itemstack.SwordItem); // added

        GL11.glPushMatrix();

        // Base position
        GL11.glTranslatef(0.32F, 0.05F, -0.8F);

        // Swing animation (re-using HeldBlock timing)
        HeldBlock hb = this.heldBlock;
        float partial = minecraft.timer.delta;
        float swing = hb.lastPos + (hb.pos - hb.lastPos) * partial; // 0..1
        if (hb.moving) {
            float tSwing = ((float) hb.offset + partial) / 7.0F; // 0..1
            float root = MathHelper.sqrt(tSwing);
            float s1 = MathHelper.sin(root * (float) Math.PI); // sway
            float s2 = MathHelper.sin(root * (float) Math.PI * 2.0F); // bob
            float s3 = MathHelper.sin(tSwing * (float) Math.PI); // jab

            if (!isSword) {
                // default item swing
                GL11.glTranslatef(-s1 * 0.4F, s2 * 0.2F, -s3 * 0.2F);
            } else {
                // SWORD SPECIAL SWING
                // anticipation
                GL11.glTranslatef(-0.05F, 0.0F, 0.05F); // added

                // accelerated arc
                float accel = swing * swing * (3f - 2f * swing); // smoothstep 0..1
                float arcY = -65.0F * accel;
                float twistZ = -30.0F * accel;
                float dropX = 20.0F * accel;

                GL11.glRotatef(dropX, 1.0F, 0.0F, 0.0F); // slight dip
                GL11.glRotatef(arcY, 0.0F, 1.0F, 0.0F); // main arc
                GL11.glRotatef(twistZ, 0.0F, 0.0F, 1.0F); // wrist twist

                // forward lunge
                GL11.glTranslatef(0.00F, -0.02F * accel, -0.10F * accel);

                // tiny scale pump
                float pump = 1.0F + 0.06F * accel;
                GL11.glScalef(pump, pump, pump);
            }
        } else if (isSword) {
            // idle micro-wobble so sword isn’t dead-still
            float t = (this.levelTicks + partial) * 0.12f; // fixed
            float wob = MathHelper.sin(t) * 0.01f;
            GL11.glTranslatef(0.0F, -wob * 0.6F, wob * 0.5F);
            GL11.glRotatef(wob * 12.0F, 0F, 0F, 1F);
        }

        // Face upright & scale
        GL11.glScalef(0.05F, 0.053F, 0.02F);
        GL11.glRotatef(180.0F, 0F, 1F, 0F);
        GL11.glRotatef(180.0F, 0F, 0F, 1F);
        GL11.glRotatef(-7.0F, 0.7F, 0F, 2F);

        // draw icon
        String tex = it.getTexture();
        if (tex != null) {
            int texId = minecraft.textureManager.load(tex);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glDisable(GL11.GL_CULL_FACE);
            ShapeRenderer.instance.begin();
            ShapeRenderer.instance.vertexUV(0, 16, 0, 0, 1);
            ShapeRenderer.instance.vertexUV(16, 16, 0, 1, 1);
            ShapeRenderer.instance.vertexUV(16, 0, 0, 1, 0);
            ShapeRenderer.instance.vertexUV(0, 0, 0, 0, 0);
            ShapeRenderer.instance.end();
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        GL11.glPopMatrix();
    }

}
