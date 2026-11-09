package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.FlintAndSteelItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ToolItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.ModelPart;

import org.lwjgl.opengl.GL11;

/** Third-person item displays relative to the posed right arm. */
public final class HandItemRenderer {
    public static void renderBow(
            ModelPart arm,
            TextureManager textures,
            String texture,
            float scale,
            float brightness,
            float shoulderOffset) {
        renderBow(arm, textures, texture, scale, brightness, shoulderOffset, 10, 1);
    }

    public static void renderBow(
            ModelPart arm,
            TextureManager textures,
            String texture,
            float scale,
            float brightness,
            float shoulderOffset,
            float handLength,
            float bowScale) {
        GL11.glPushMatrix();
        try {
            // Match the skeleton hand offset and the modern bow item display transform.
            GL11.glTranslatef(shoulderOffset * scale, 0, 0);
            arm.applyTransform(scale);
            GL11.glRotatef(-90, 1, 0, 0);
            GL11.glRotatef(180, 0, 1, 0);
            GL11.glTranslatef(scale, 2 * scale, -handLength * scale);
            GL11.glTranslatef(-scale, -2 * scale, 2.5f * scale);
            GL11.glRotatef(-80, 1, 0, 0);
            GL11.glRotatef(260, 0, 1, 0);
            GL11.glRotatef(-40, 0, 0, 1);
            float size = 16 * .9f * scale * bowScale;
            GL11.glScalef(size, size, size);
            ItemMeshRenderer.render(textures, texture, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void applyRightHand(ModelPart arm, float scale) {
        arm.applyTransform(scale);
        // Modern item-in-hand layer, relative to the shoulder pivot.
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glTranslatef(scale, 2 * scale, -10 * scale);
    }

    public static void renderStack(
            ModelPart arm, TextureManager textures, int stackId, float scale, float brightness) {
        if (stackId <= 0) return;
        if (stackId == Block.LEVER.id) stackId = Item.iconId(stackId);
        if (stackId < 256) {
            Block block = Block.blocks[stackId];
            if (block == null) return;
            GL11.glPushMatrix();
            try {
                applyRightHand(arm, scale);
                GL11.glTranslatef(0, 2.5f * scale, 0);
                GL11.glRotatef(75, 1, 0, 0);
                GL11.glRotatef(45, 0, 1, 0);
                GL11.glScalef(6 * scale, 6 * scale, 6 * scale);
                GL11.glTranslatef(-.5f, -.5f, -.5f);
                BlockItemRenderer.render(block, textures, brightness);
            } finally {
                GL11.glPopMatrix();
            }
            return;
        }

        int id = stackId - 256;
        if (id >= Item.items.length || Item.items[id] == null) return;
        Item item = Item.items[id];
        // Flint and steel uses the generated display despite being a tool in gameplay.
        boolean handheld =
                item instanceof ToolItem
                        && !(item instanceof FlintAndSteelItem)
                        && (!(item instanceof Equipment.Gear gear) || gear.slot < 0);
        GL11.glPushMatrix();
        try {
            applyRightHand(arm, scale);
            GL11.glTranslatef(0, (handheld ? 4 : 3) * scale, (handheld ? .5f : 1) * scale);
            if (handheld) {
                GL11.glRotatef(-90, 0, 1, 0);
                GL11.glRotatef(55, 0, 0, 1);
            }
            float size = (handheld ? 13.6f : 8.8f) * scale;
            GL11.glScalef(size, size, size);
            ItemMeshRenderer.renderItem(textures, item, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
