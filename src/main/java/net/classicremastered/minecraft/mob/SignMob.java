// File: src/net/classicremastered/minecraft/mob/SignMob.java
package net.classicremastered.minecraft.mob;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;

public final class SignMob extends Mob {

    private static final ModelPart board = new ModelPart(0, 0);
    private static final ModelPart stick;

    static {
        board.setBounds(-12.0F, -14.0F, -1.0F, 24, 12, 2, 0.0F); // board
        stick = new ModelPart(0, 14);
        stick.setBounds(-1.0F, -2.0F, -1.0F, 2, 14, 2, 0.0F);   // stick
    }

    private final String[] taunts = {
        "KEEP OUT!",
        "NO TRESPASSING",
        "STOP!",
        "TURN BACK!"
    };
    private String currentText = "KEEP OUT!";

    public SignMob(Level level, float x, float y, float z) {
        super(level);
        this.setPos(x, y, z);
        this.setSize(0.6F, 1.8F);
        this.heightOffset = this.bbHeight / 2.0F;

        this.health = 10;
        this.deathScore = 5;
        this.soundIdle = "random/wood_click";
        this.soundHurt = "random/wood_click";
        this.soundDeath = "random/pop";

        // ✅ must not be null or empty, otherwise SpawnMobCommand skips it
        this.modelName = "signmob";
    }

    @Override
    public void tick() {
        super.tick();

        // Gravity
        this.yd -= 0.04F;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.91F;
        this.yd *= 0.98F;
        this.zd *= 0.91F;
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
            this.yd *= -0.5F;
        }

        // Basic chase AI
        Player target = this.level.getNearestPlayer(this.x, this.y, this.z, 16.0f);
        if (target != null && !target.removed) {
            // changed: use persistent AI instead of recreating
            if (!(this.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI)) {
                this.ai = new net.classicremastered.minecraft.mob.ai.BasicAI();
                ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).runSpeed = 0.23F; // changed: normal speed cap
            }
            ((net.classicremastered.minecraft.mob.ai.BasicAI) this.ai).attackTarget = target;

            if (this.distanceTo(target) < 1.5F && this.attackTime == 0) {
                this.attackTime = ATTACK_DURATION;
                target.hurt(this, 2);
                this.currentText = taunts[level.random.nextInt(taunts.length)];
            }
        }
    }


    @Override
    public void render(TextureManager tm, float partial) {
        GL11.glPushMatrix();

        float px = this.xo + (this.x - this.xo) * partial;
        float py = this.yo + (this.y - this.yo) * partial - this.heightOffset / 2.0F;
        float pz = this.zo + (this.z - this.zo) * partial;
        GL11.glTranslatef(px, py, pz);

        GL11.glRotatef(-this.yRot, 0.0F, 1.0F, 0.0F); // fixed: now faces player
        
        int tex = tm.load("/item/sign.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Draw model
        GL11.glPushMatrix();
        GL11.glScalef(1.0F, -1.0F, -1.0F);
        board.render(0.0625F);
        stick.render(0.0625F);
        GL11.glPopMatrix();

        // Text overlay
        if (this.currentText != null && !this.currentText.isEmpty() && level != null && level.minecraft != null) {
            net.classicremastered.minecraft.gui.FontRenderer font = level.minecraft.fontRenderer;
            if (font != null) {
                float scale = 0.016666668F;
                GL11.glTranslatef(0.0F, 0.5F, 0.09F);
                GL11.glScalef(scale, -scale, scale);
                GL11.glNormal3f(0.0F, 0.0F, -1.0F * scale);
                GL11.glEnable(GL11.GL_BLEND);

                int width = font.getWidth(this.currentText);
                font.renderNoShadow(this.currentText, -width / 2, -5, 0x202020);

                GL11.glDisable(GL11.GL_BLEND);
            }
        }

        GL11.glPopMatrix();
    }

    @Override
    public void die(Entity killer) {
        super.die(killer);
        // Drop wood when killed
        this.level.addEntity(new net.classicremastered.minecraft.item.Item(
            this.level, this.x, this.y, this.z,
            net.classicremastered.minecraft.level.tile.Block.WOOD.id
        ));
    }
}
