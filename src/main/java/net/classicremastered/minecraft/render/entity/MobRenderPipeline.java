package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.Cow;
import net.classicremastered.minecraft.mob.HumanoidMob;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Skeleton;
import net.classicremastered.minecraft.mob.Spider;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.Model;
import net.classicremastered.minecraft.model.SkeletonModel;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.BlockTextureAtlas;
import net.classicremastered.minecraft.render.EquipmentRenderer;
import net.classicremastered.minecraft.render.HeldItemRenderer;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

final class MobRenderPipeline {
    private MobRenderPipeline() {}

    static <T extends Mob> void render(
            T mob, MobRenderer<T> renderer, TextureManager tm, float partial) {
        // ---- orientation interpolation ----
        while (mob.yBodyRotO - mob.yBodyRot < -180.0F) mob.yBodyRotO += 360.0F;
        while (mob.yBodyRotO - mob.yBodyRot >= 180.0F) mob.yBodyRotO -= 360.0F;
        while (mob.xRotO - mob.xRot < -180.0F) mob.xRotO += 360.0F;
        while (mob.xRotO - mob.xRot >= 180.0F) mob.xRotO -= 360.0F;
        while (mob.yRotO - mob.yRot < -180.0F) mob.yRotO += 360.0F;
        while (mob.yRotO - mob.yRot >= 180.0F) mob.yRotO -= 360.0F;

        float bodyRot = mob.renderBodyYaw(partial);
        float runAmt = mob.oRun + (mob.run - mob.oRun) * partial;
        float yaw = mob.renderHeadYaw(partial);
        float pitch = mob.renderHeadPitch(partial);
        float modelYawOffset = mob.rotOffs;
        if (mob instanceof Player) {
            modelYawOffset = 0f;
            if (mob.riding instanceof Mob mount) {
                bodyRot =
                        mount.yBodyRotO
                                + (float) Math.IEEEremainder(mount.yBodyRot - mount.yBodyRotO, 360f)
                                        * partial
                                - mount.rotOffs;
            } else if (mob.riding instanceof BoatEntity boat) {
                bodyRot = boat.renderYaw(partial);
            }
            float look =
                    (float)
                            Math.IEEEremainder(
                                    mob.yRotO + (mob.yRot - mob.yRotO) * partial - bodyRot, 360f);
            yaw = Math.max(-75f, Math.min(75f, look));
        } else if (mob instanceof Zombie
                && mob.isBaby()
                && !(mob instanceof Skeleton)
                && mob.riding instanceof Mob mount) {
            float look = bodyRot + yaw;
            bodyRot = mount.renderBodyYaw(partial) - mount.rotOffs;
            yaw = (float) Math.IEEEremainder(look - bodyRot, 360);
            yaw = Math.max(-75f, Math.min(75f, yaw));
        }

        GL11.glPushMatrix();
        float anim = mob.animStepO + (mob.animStep - mob.animStepO) * partial;

        // ---- base brightness color ----
        float baseBright = mob.getRenderBrightness(partial);
        GL11.glColor3f(baseBright, baseBright, baseBright);

        float scale = 0.0625F;

        Model baseModel = renderer.model(mob);
        float groundLift = renderer.groundOffset(mob);
        boolean groundSkeletonStride =
                baseModel instanceof SkeletonModel && mob.riding == null && mob.health > 0;
        if (groundSkeletonStride && mob.onGround) {
            groundLift -=
                    ((SkeletonModel) baseModel).groundedStrideOffset(anim, runAmt, mob.isBaby());
        }

        float walkBob =
                groundSkeletonStride
                        ? 0f
                        : Math.abs(MathHelper.cos(anim * .6662F))
                                * 5.0F
                                * runAmt
                                * mob.renderBobStrength();
        float bob = -walkBob - groundLift;

        // ---- move to entity ----
        float renderHeightOffset =
                mob.riding == null && mob instanceof Player player
                        ? player.getRenderHeightOffset(partial)
                        : mob.heightOffset;
        float anchorY =
                mob instanceof Player || mob.riding != null
                        ? mob.yo + (mob.y - mob.yo) * partial - renderHeightOffset
                        : mob.renderFeetY(partial);
        if (mob.riding instanceof BoatEntity
                && (mob instanceof HumanoidMob || mob instanceof Player)) {
            anchorY -= mob.isBaby() ? .19f : .30f;
        } else if (mob.riding != null
                && !(mob.riding instanceof Cow)
                && !(mob instanceof Skeleton && mob.riding instanceof Spider)
                && (mob instanceof Zombie || mob instanceof Player)) {
            anchorY -= mob.isBaby() ? .43f : .62f;
        }
        GL11.glTranslatef(
                mob.xo + (mob.x - mob.xo) * partial,
                anchorY + mob.renderOffset,
                mob.zo + (mob.z - mob.zo) * partial);

        boolean showFireOverlay = mob.remainingFireTicks > 0 && !mob.isWetForFire();
        if (mob instanceof Zombie zombie && zombie.sunlightFlameTicks > 0 && !mob.isWetForFire())
            showFireOverlay = true;

        if (showFireOverlay) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
            try {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load(BlockTextureAtlas.RESOURCE));
                float width = Math.max(.65f, mob.bbWidth * 1.35f);
                float layerHeight = Math.min(1f, mob.bbHeight);
                ShapeRenderer flames = ShapeRenderer.instance;
                flames.begin();
                flames.color(1, 1, 1);
                for (float layer = 0; layer < mob.bbHeight * .8f; layer += .55f) {
                    FireBlock.renderFlames(
                            flames, -width * .5f, layer, -width * .5f, width, layerHeight);
                    width *= .85f;
                }
                flames.end();
            } finally {
                GL11.glPopAttrib();
            }
        }

        // ---- hurt/death wobble ----
        float ht = (float) mob.hurtTime - partial;
        if (ht > 0.0F || mob.health <= 0) {
            if (ht < 0.0F) ht = 0.0F;
            else
                ht =
                        MathHelper.sin(
                                        (ht /= (float) mob.hurtDuration)
                                                * ht
                                                * ht
                                                * ht
                                                * (float) Math.PI)
                                * 14.0F;

            if (mob.health <= 0) {
                float deathF = ((float) mob.deathTime + partial) / 20.0F;
                ht += deathF * deathF * 800.0F;
                if (ht > 90.0F) ht = 90.0F;
            }

            float dir = mob.hurtDir;
            GL11.glRotatef(180.0F - bodyRot + modelYawOffset, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-ht, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(dir, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-(180.0F - bodyRot + modelYawOffset), 0.0F, 1.0F, 0.0F);
        }

        float modelScale = mob.renderScale();
        GL11.glScalef(modelScale, modelScale, modelScale);
        GL11.glTranslatef(0.0F, -bob * scale, 0.0F);
        GL11.glScalef(1.0F, -1.0F, 1.0F);
        GL11.glRotatef(180.0F - bodyRot + modelYawOffset, 0.0F, 1.0F, 0.0F);

        // Alpha/Cull state
        if (!mob.allowAlpha) GL11.glDisable(GL11.GL_ALPHA_TEST);
        else GL11.glDisable(GL11.GL_CULL_FACE);

        // ==== Delegated render ====
        GL11.glScalef(-1.0F, 1.0F, 1.0F);

        renderer.renderBase(mob, tm, partial, yaw, pitch, baseBright);
        if (baseModel instanceof HumanoidModel humanoid) {
            EquipmentRenderer.armor(mob, humanoid, tm, scale, baseBright);
            if (mob instanceof Player player) {
                HeldItemRenderer.renderInHand(player, humanoid, tm, scale, baseBright);
            } else {
                EquipmentRenderer.held(mob, humanoid, tm, scale, baseBright);
            }
        }

        float hurtLeft = mob.hurtTime - partial;
        boolean hurtNow = hurtLeft > 0 && mob.hurtDuration > 0;
        if (renderer.usesSharedDamageOverlay() && (hurtNow || mob.health <= 0)) {
            float fade = hurtNow ? hurtLeft / mob.hurtDuration : 0;
            float strength =
                    hurtNow ? fade * fade : .7f * (1 - Math.min(1, (mob.deathTime + partial) / 10));
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
            try {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1, .2f, .2f, Math.min(.75f, .55f * strength));
                renderer.bindSkin(mob, tm);
                renderer.renderPose(mob, tm, partial, yaw, pitch);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tm.load("/misc/hurt_overlay.png"));
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glColor4f(.95f, .25f, .15f, .5f * strength);
                renderer.renderPose(mob, tm, partial, yaw, pitch);
            } finally {
                GL11.glPopAttrib();
            }
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        if (mob.allowAlpha) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }
}
