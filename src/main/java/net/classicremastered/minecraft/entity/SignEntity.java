package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.gui.FontRenderer;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.ModelPart;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.nbt.CompoundTag;

import org.lwjgl.opengl.GL11;

/** A stationary, editable standing sign. */
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

    public static final int LINE_COUNT = 4;
    public static final int INK_COLOR = 0x17120D;
    public static final float MODEL_HEIGHT = 26f / 16f;
    // Diagonal of the 24 by 2 pixel board, rounded up for its rotating footprint.
    public static final float MODEL_FOOTPRINT = 1.51f;
    public static final int LINE_LENGTH = 15;
    private final String[] messages = {"", "", "", ""};
    private final int blockX, blockY, blockZ;
    private final float rot;
    private int health = 4;

    public SignEntity(Level level, int x, int y, int z, float rotation) {
        super(level);
        blockX = x;
        blockY = y;
        blockZ = z;
        rot = rotation;
        setSize(.7f, 1.625f);
        heightOffset = bbHeight / 2;
        moveTo(x + .5f, y + heightOffset, z + .5f, rotation, 0);
        makeStepSound = false;
    }

    public boolean hasSupport() {
        if (!level.isInBounds(blockX, blockY, blockZ)
                || blockY <= 0
                || blockY + bbHeight > level.depth) return false;
        Block below = Block.blocks[level.getTile(blockX, blockY - 1, blockZ)];
        return below != null && below.isSolid() && below.isCube();
    }

    public String line(int row) {
        return messages[row];
    }

    public void setLine(int row, String line) {
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < line.length() && filtered.length() < LINE_LENGTH; i++) {
            char c = line.charAt(i);
            if (c >= 32 && c < 127) filtered.append(c);
        }
        messages[row] = filtered.toString();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(null);
        tag.putInt("X", blockX);
        tag.putInt("Y", blockY);
        tag.putInt("Z", blockZ);
        tag.putFloat("Rotation", rot);
        for (int i = 0; i < LINE_COUNT; i++) tag.putString("Line" + i, messages[i]);
        return tag;
    }

    public static SignEntity load(Level level, CompoundTag tag) {
        float rotation = tag.getFloat("Rotation");
        int x = tag.getInt("X"), y = tag.getInt("Y"), z = tag.getInt("Z");
        if (!Float.isFinite(rotation) || !level.isInBounds(x, y, z)) return null;
        SignEntity sign = new SignEntity(level, x, y, z, rotation);
        if (!sign.hasSupport()) return null;
        for (int i = 0; i < LINE_COUNT; i++) sign.setLine(i, tag.getString("Line" + i));
        return sign;
    }

    @Override
    public boolean isPickable() {
        return !removed;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void hurt(Entity attacker, int damage) {
        if (removed || level.isNetworkMode() || damage <= 0) return;
        health -= damage;
        if (health <= 0 || level.creativeMode) breakSign();
    }

    @Override
    public void tick() {
        if (!hasSupport() || !level.getCubes(bb).isEmpty()) breakSign();
    }

    private void breakSign() {
        if (removed) return;
        if (!level.creativeMode && !level.isNetworkMode() && Item.OAK_SIGN != null) {
            level.addEntity(new DroppedItem(level, x, y, z, Item.OAK_SIGN.id, 1));
        }
        remove();
    }

    @Override
    public void render(TextureManager texMgr, float partial) {
        float px = xo + (x - xo) * partial;
        float pz = zo + (z - zo) * partial;
        FontRenderer font = level.minecraft == null ? null : level.minecraft.fontRenderer;
        renderStandingSign(texMgr, font, px, blockY, pz, rot, messages);
    }

    public static void renderStandingSign(
            TextureManager texMgr,
            FontRenderer font,
            float x,
            float feetY,
            float z,
            float rotation,
            String... lines) {
        GL11.glPushMatrix();

        GL11.glTranslatef(x, feetY + .75f, z);

        // Facing
        GL11.glRotatef(rotation, 0.0F, 1.0F, 0.0F);

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
            float scale = 0.015625F;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glTranslatef(0, .5f, .075f);
            GL11.glScalef(scale, -scale, scale);
            GL11.glNormal3f(0.0F, 0.0F, -1.0F * scale);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            for (int i = 0; i < lines.length; i++) {
                String msg = lines[i];
                int width = font.getLiteralWidth(msg);
                GL11.glPushMatrix();
                GL11.glScalef(Math.min(1f, 88f / Math.max(1, width)), 1, 1);
                font.renderLiteral(msg, -width / 2, i * 10 - (lines.length - 1) * 5 - 4, INK_COLOR);
                GL11.glPopMatrix();
            }

            GL11.glPopAttrib();
        }

        GL11.glPopMatrix();

        // restore texture state for the rest of the engine
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int terrainTex =
                texMgr.load(
                        net.classicremastered.minecraft.render.BlockTextureAtlas
                                .RESOURCE); // fetch texture ID
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrainTex);
    }
}
