package net.classicremastered.minecraft.render;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.model.*;
import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public final class HeldItemRenderer {
    public static void renderWorld(Player player, float partial) {
        if (player.isSpectator())
            return;
        if (GunModel.isGun(player.inventory.getSelected() - 256)) {
            GunRenderer.renderWorld(player, partial);
        }
    }

    public static void renderInHand(
            Player player,
            HumanoidModel model,
            TextureManager textures,
            float scale,
            float brightness) {
        int selected = player.inventory.getSelected();
        if (GunModel.isGun(selected - 256)) return;
        float armPitch = model.rightArm.pitch;
        if (player.developerPowers().aimingBlock(player)) model.rightArm.pitch = -2.5f;
        try {
            if (selected == 262) {
                BowItem bow = (BowItem) Item.BOW;
                HandItemRenderer.renderBow(
                        model.rightArm, textures, bow.getTexture(player), scale, brightness, 0);
            } else {
                HandItemRenderer.renderStack(model.rightArm, textures, selected, scale, brightness);
            }
        } finally {
            model.rightArm.pitch = armPitch;
        }
    }

    public static void render(Renderer renderer, float partial) {
        if (renderer.minecraft == null
                || renderer.minecraft.player == null
                || renderer.minecraft.cameraMode != 0) return;
        Player player = renderer.minecraft.player;
        HeldBlock held = renderer.heldBlock;
        int selected = held.getRenderedId(player.inventory.getSelected());
        if (selected >= 256 && GunModel.isGun(selected - 256)) {
            GunRenderer.renderFirstPerson(player, selected - 256, partial);
            return;
        }

        float brightness = player.getRenderBrightness(partial);
        float equipDrop = (1 - (held.lastPos + (held.pos - held.lastPos) * partial)) * .6f;
        float attack = held.moving ? Math.max(0, Math.min(1, (held.offset + partial) / 6f)) : 0;
        double swingAngle = Math.sqrt(attack) * Math.PI;
        float swing = (float) Math.sin(swingAngle);
        float rise = (float) Math.sin(2 * swingAngle);
        float reach = (float) Math.sin(attack * Math.PI);
        float turn = (float) Math.sin(attack * attack * Math.PI);
        TextureManager textures = renderer.minecraft.textureManager;
        int displayId = Item.iconId(selected);
        Block block =
                displayId > 0 && displayId < Block.blocks.length ? Block.blocks[displayId] : null;
        Item item =
                displayId >= 256 && displayId - 256 < Item.items.length
                        ? Item.items[displayId - 256]
                        : null;

        GL11.glPushMatrix();
        try {
            var powers = player.developerPowers();
            float dodge = powers.dodgePose(player, partial);
            GL11.glTranslatef(.06f * dodge * powers.dodgeSide(), -.08f * dodge, .04f * dodge);
            GL11.glRotatef(10 * dodge * powers.dodgeSide(), 0, 0, 1);
            float kick = powers.kickPose(player, partial);
            if (kick > 0) renderKick(player, textures, brightness, kick);
            if (block == null && item == null) {
                float punch = powers.punchPose(player, partial);
                if (powers.active(player)) {
                    float side = powers.leftPunch() ? -1 : 1;
                    GL11.glScalef(side, 1, 1);
                    GL11.glTranslatef(.5f - .28f * punch, -.6f + .16f * punch, -.9f - .65f * punch);
                    GL11.glRotatef(45 - 30 * punch, 0, 1, 0);
                    renderArm(player, textures, brightness);
                    return;
                }
                GL11.glTranslatef(
                        .64f - .3f * swing, -.6f - equipDrop + .4f * rise, -.72f - .4f * reach);
                GL11.glRotatef(45 + 70 * swing, 0, 1, 0);
                GL11.glRotatef(-20 * turn, 0, 0, 1);
                renderArm(player, textures, brightness);
                return;
            }

            GL11.glTranslatef(.56f, -.52f - equipDrop, -.72f);
            if (block != null && powers.aimingBlock(player)) {
                GL11.glTranslatef(-.10f, .28f, -.35f);
                GL11.glRotatef(-18, 1, 0, 0);
            } else if (item instanceof BowItem bow && bow.isDrawing(player)) {
                float draw = bow.getDrawProgress(player, partial);
                float power = (draw * draw + 2 * draw) / 3;
                GL11.glTranslatef(-.2785682f, .18344387f, .15731531f);
                GL11.glRotatef(-13.935f, 1, 0, 0);
                GL11.glRotatef(35.3f, 0, 1, 0);
                GL11.glRotatef(-9.785f, 0, 0, 1);
                GL11.glTranslatef(0, 0, .04f * power);
                GL11.glScalef(1, 1, 1 + .2f * power);
                GL11.glRotatef(-45, 0, 1, 0);
            } else {
                GL11.glTranslatef(-.4f * swing, .2f * rise, -.2f * reach);
                GL11.glRotatef(45 - 20 * turn, 0, 1, 0);
                GL11.glRotatef(-20 * swing, 0, 0, 1);
                GL11.glRotatef(-80 * swing, 1, 0, 0);
                GL11.glRotatef(-45, 0, 1, 0);
            }

            if (block != null) {
                // The classic block mesh uses the block corner as its origin.
                GL11.glRotatef(45, 0, 1, 0);
                GL11.glScalef(.4f, .4f, .4f);
                GL11.glTranslatef(-.5f, -.5f, -.5f);
                BlockItemRenderer.render(block, textures, brightness);
            } else {
                float pose = player.developerPowers().swordPose(player, selected, partial);
                if (pose > 0) {
                    GL11.glTranslatef(-.65f * pose, .16f * pose, -.18f * pose);
                    GL11.glRotatef(100 * pose, 0, 0, 1);
                    GL11.glRotatef(-45 * pose, 0, 1, 0);
                } else if (pose < 0) {
                    GL11.glTranslatef(.32f * pose, -.18f * pose, .60f * pose);
                    GL11.glRotatef(-65 * pose, 1, 0, 0);
                }
                // Generated and handheld models share the same first-person display transform.
                GL11.glTranslatef(1.13f / 16, 3.2f / 16, 1.13f / 16);
                GL11.glRotatef(-90, 0, 1, 0);
                GL11.glRotatef(25, 0, 0, 1);
                GL11.glScalef(.68f, .68f, .68f);
                if (item instanceof BowItem bow) {
                    ItemMeshRenderer.render(textures, bow.getTexture(player), brightness);
                } else {
                    ItemMeshRenderer.renderItem(textures, item, brightness);
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void renderKick(
            Player player, TextureManager textures, float brightness, float kick) {
        HumanoidModel model = player.getModel();
        if (model == null) return;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            player.bindTexture(textures);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor3f(brightness, brightness, brightness);
            GL11.glTranslatef(.22f, -1.1f + .55f * kick, -.35f - .3f * kick);
            GL11.glRotatef(-75 * kick, 1, 0, 0);
            ModelPart leg = model.rightLeg;
            if (!leg.hasList) leg.generateList(.0625f);
            GL11.glCallList(leg.list);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void renderArm(Player player, TextureManager textures, float brightness) {
        HumanoidModel model = player.getModel();
        if (model == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            player.bindTexture(textures);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor3f(brightness, brightness, brightness);
            GL11.glTranslatef(-1, 3.6f, 3.5f);
            GL11.glRotatef(120, 0, 0, 1);
            GL11.glRotatef(200, 1, 0, 0);
            GL11.glRotatef(-135, 0, 1, 0);
            GL11.glTranslatef(5.6f, 0, 0);
            // Render the arm at its shoulder pivot without the third-person pose.
            GL11.glTranslatef(-5f / 16, 2f / 16, 0);
            GL11.glRotatef((float) Math.toDegrees(.1), 0, 0, 1);
            ModelPart arm = model.rightArm;
            if (!arm.hasList) arm.generateList(.0625f);
            GL11.glCallList(arm.list);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
