// File: src/net/classicremastered/minecraft/item/SignEntity.java
package net.classicremastered.minecraft.item;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gui.FontRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.level.tile.Block;

/**
 * Port of Survival Test 0.24 Sign (entity form, not a block).
 * Renders a wooden board + stick, with hardcoded 4 lines of text.
 */
public final class SignEntity extends Entity {

    private static final ModelPart board = new ModelPart(0, 0);
    private static final ModelPart stick;

    static {
        // Classic board (24×12×2)
        board.setBounds(-12.0F, -14.0F, -1.0F, 24, 12, 2, 0.0F);

        // Stick (2×14×2)
        stick = new ModelPart(0, 14);
        stick.setBounds(-1.0F, -2.0F, -1.0F, 2, 14, 2, 0.0F);
    }

    private float rot;
    private final String[] messages = {
        "This is a test",
        "of the signs.",
        "Each line can",
        "be 15 chars!"
    };

    private final FontRenderer font;

    // --- NEW: simple health system ---
    private int health = 4; // punches required

    public SignEntity(Minecraft mc, float x, float y, float z, float yaw) {
        super(mc.level);
        this.setSize(0.5F, 1.5F);
        this.heightOffset = this.bbHeight / 2.0F;

        this.setPos(x, y, z);
        this.rot = -yaw;
        this.heightOffset = 1.5F;

        // Initial thrown velocity
        this.xd = -(float) Math.sin(this.rot * Math.PI / 180.0) * 0.05F;
        this.yd = 0.2F;
        this.zd = -(float) Math.cos(this.rot * Math.PI / 180.0) * 0.05F;

        this.makeStepSound = false;
        this.font = mc.fontRenderer;
    }

    @Override
    public boolean isPickable() {
        return !this.removed;
    }

    // --- NEW: can take damage ---
    @Override
    public void hurt(Entity attacker, int damage) {
        if (this.removed) return;
        this.health -= damage;
        if (this.health <= 0) {
            this.remove();
        }
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.yd -= 0.04F; // gravity
        this.move(this.xd, this.yd, this.zd);

        this.xd *= 0.98F;
        this.yd *= 0.98F;
        this.zd *= 0.98F;

        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
            this.yd *= -0.5F;
        }
    }

    @Override
    public void remove() {
        if (!this.removed && this.level != null) {
            // Drop a wooden plank block when sign is destroyed
            this.level.addEntity(
                new net.classicremastered.minecraft.item.Item(
                    this.level,
                    this.x, this.y, this.z,
                    Block.WOOD.id
                )
            );
        }
        super.remove();
    }

    @Override
    public void render(TextureManager texMgr, float partial) {
        GL11.glPushMatrix();

        // Position
        float px = this.xo + (this.x - this.xo) * partial;
        float py = this.yo + (this.y - this.yo) * partial - this.heightOffset / 2.0F;
        float pz = this.zo + (this.z - this.zo) * partial;
        GL11.glTranslatef(px, py, pz);

        // Facing
        GL11.glRotatef(this.rot, 0.0F, 1.0F, 0.0F);

        // Bind sign texture
        int tex = texMgr.load("/item/sign.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Draw model
        GL11.glPushMatrix();
        GL11.glScalef(1.0F, -1.0F, -1.0F);
        board.render(0.0625F);
        stick.render(0.0625F);
        GL11.glPopMatrix();

        // Draw text overlay
        if (font != null) {
            float scale = 0.016666668F;
            GL11.glTranslatef(0.0F, 0.5F, 0.09F);
            GL11.glScalef(scale, -scale, scale);
            GL11.glNormal3f(0.0F, 0.0F, -1.0F * scale);
            GL11.glEnable(GL11.GL_BLEND);

            for (int i = 0; i < messages.length; i++) {
                String msg = messages[i];
                int width = font.getWidth(msg);
                font.renderNoShadow(msg, -width / 2, i * 10 - messages.length * 5, 0x202020);
            }

            GL11.glDisable(GL11.GL_BLEND);
        }

        GL11.glPopMatrix();

        // restore texture state for the rest of the engine
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int terrainTex = texMgr.load("/terrain.png"); // fetch texture ID
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrainTex);
    }
}
