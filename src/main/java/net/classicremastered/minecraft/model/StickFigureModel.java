package net.classicremastered.minecraft.model;

import net.classicremastered.minecraft.entity.BossThrownBlock;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI;
import net.classicremastered.minecraft.mob.ai.stickfigure.StickFigurePowers;
import net.classicremastered.minecraft.render.BlockItemRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import org.lwjgl.opengl.GL11;

public final class StickFigureModel extends Model {

    private static final int GROUND_Y = 32, SHOULDER_Y = 6, ARM_LENGTH = 7;
    private static final float MODEL_SCALE = 1f / 16;
    public final ModelPart head,
            body,
            shoulders,
            rightArm,
            rightForearm,
            leftArm,
            leftForearm,
            rightLeg,
            rightShin,
            leftLeg,
            leftShin;
    private int strikeTicks, throwTicks = -1, heldBlockId, heldBlockSize;
    private boolean kicking, holdingPhysicsGun, rushing;
    private float swimAmount, previousSwimAmount, swimBlend;
    private float runLean, runBounce;

    public StickFigureModel() {
        groundOffset = GROUND_Y;
        int oldW = TexturedQuad.ATLAS_W, oldH = TexturedQuad.ATLAS_H;
        try {
            TexturedQuad.ATLAS_W = TexturedQuad.ATLAS_H = 128;
            head = new ModelPart(0, 0);
            head.setBounds(-3f, -6f, -3f, 6, 6, 6, 0);
            head.setPosition(0f, 6f, 0f);
            body = new ModelPart(25, 0);
            body.setBounds(-1f, 0f, -1f, 2, 10, 2, 0);
            body.setPosition(0f, 6f, 0f);
            shoulders = new ModelPart(34, 0);
            shoulders.setBounds(-3f, 0f, -1f, 6, 2, 2, 0);
            shoulders.setPosition(0f, 6f, 0f);
            rightArm = new ModelPart(51, 0);
            rightArm.setBounds(-1f, 0f, -1f, 2, ARM_LENGTH, 2, 0);
            rightArm.setPosition(-3f, SHOULDER_Y, 0f);
            rightForearm = new ModelPart(60, 0);
            rightForearm.setBounds(-1f, 0f, -1f, 2, ARM_LENGTH, 2, 0);
            rightForearm.setPosition(0f, ARM_LENGTH, 0f);
            leftArm = new ModelPart(69, 0);
            leftArm.setBounds(-1f, 0f, -1f, 2, ARM_LENGTH, 2, 0);
            leftArm.setPosition(3f, SHOULDER_Y, 0f);
            leftForearm = new ModelPart(78, 0);
            leftForearm.setBounds(-1f, 0f, -1f, 2, ARM_LENGTH, 2, 0);
            leftForearm.setPosition(0f, ARM_LENGTH, 0f);
            rightLeg = new ModelPart(87, 0);
            rightLeg.setBounds(-1f, 0f, -1f, 2, 8, 2, 0);
            rightLeg.setPosition(-1.5f, 16f, 0f);
            rightShin = new ModelPart(96, 0);
            rightShin.setBounds(-1f, 0f, -1f, 2, 8, 2, 0);
            rightShin.setPosition(0f, 8f, 0f);
            leftLeg = new ModelPart(105, 0);
            leftLeg.setBounds(-1f, 0f, -1f, 2, 8, 2, 0);
            leftLeg.setPosition(1.5f, 16f, 0f);
            leftShin = new ModelPart(114, 0);
            leftShin.setBounds(-1f, 0f, -1f, 2, 8, 2, 0);
            leftShin.setPosition(0f, 8f, 0f);
            rightArm.addChild(rightForearm);
            leftArm.addChild(leftForearm);
            rightLeg.addChild(rightShin);
            leftLeg.addChild(leftShin);
        } finally {
            TexturedQuad.ATLAS_W = oldW;
            TexturedQuad.ATLAS_H = oldH;
        }
    }

    @Override
    public void preAnimate(Object entity) {
        throwTicks = -1;
        holdingPhysicsGun = false;
        rushing = false;
        heldBlockId = heldBlockSize = 0;
        if (entity instanceof StickFigure s) {
            strikeTicks = s.strikeTicks;
            kicking = s.kicking;
            previousSwimAmount = s.getSwimAmount(0);
            swimAmount = s.getSwimAmount(1);
            rushing = s.ai instanceof StickFigureAI ai && ai.powers.rushTicks > 0;
            holdingPhysicsGun = s.ai instanceof StickFigureAI ai
                    && ai.powers.phase == StickFigurePowers.Phase.PHYSICS_GUN;
            if (s.ai instanceof StickFigureAI ai
                    && ai.powers.phase == StickFigurePowers.Phase.THROW) {
                throwTicks = ai.powers.phaseTicks;
                heldBlockId = ai.powers.heldBlockId();
                heldBlockSize = ai.powers.heldBlockSize();
            }
        } else {
            strikeTicks = 0;
            kicking = false;
            previousSwimAmount = swimAmount = 0;
        }
    }

    @Override
    public void setRotationAngles(
            float swing, float amount, float age, float yaw, float pitch, float scale) {
        head.pitch = head.yaw = head.roll = 0;
        body.pitch = body.yaw = body.roll = 0;
        shoulders.pitch = shoulders.yaw = shoulders.roll = 0;
        rightArm.pitch = rightArm.yaw = rightArm.roll = 0;
        rightForearm.pitch = rightForearm.yaw = rightForearm.roll = 0;
        leftArm.pitch = leftArm.yaw = leftArm.roll = 0;
        leftForearm.pitch = leftForearm.yaw = leftForearm.roll = 0;
        rightLeg.pitch = rightLeg.yaw = rightLeg.roll = 0;
        rightShin.pitch = rightShin.yaw = rightShin.roll = 0;
        leftLeg.pitch = leftLeg.yaw = leftLeg.roll = 0;
        leftShin.pitch = leftShin.yaw = leftShin.roll = 0;
        float movement = Math.max(0, Math.min(1, amount));
        swimBlend = previousSwimAmount
                + (swimAmount - previousSwimAmount) * (age - (float) Math.floor(age));
        float cycle = swing * .9f;
        float stride = (float) Math.cos(cycle) * (rushing ? 1.4f : 1.15f) * movement;
        boolean running = !holdingPhysicsGun && throwTicks < 0 && strikeTicks == 0;
        runLean = running ? (rushing ? 22f : 14f) * movement * (1 - swimBlend) : 0;
        runBounce = running
                ? Math.abs((float) Math.sin(cycle)) * .065f * movement * (1 - swimBlend)
                : 0;
        head.yaw = Math.max(-.7f, Math.min(.7f, yaw / 57.295776f));
        head.pitch = Math.max(-.4f, Math.min(.4f, pitch / 57.295776f));
        rightLeg.pitch = stride;
        leftLeg.pitch = -stride;
        rightArm.pitch = -stride * .85f;
        leftArm.pitch = stride * .85f;
        rightForearm.pitch = leftForearm.pitch = -1.15f * movement;
        rightArm.roll = .1f;
        leftArm.roll = -.1f;
        rightShin.pitch = (.2f + Math.max(0, -stride) * .9f) * movement;
        leftShin.pitch = (.2f + Math.max(0, stride) * .9f) * movement;
        if (swimBlend > 0) {
            float stroke = age * .24f;
            head.pitch = blend(head.pitch, -.85f, swimBlend);
            // A bounded stroke prevents angle wrapping from snapping during transitions.
            rightArm.pitch = blend(rightArm.pitch, -1.5f + (float) Math.sin(stroke) * 1.2f, swimBlend);
            leftArm.pitch = blend(leftArm.pitch, -1.5f - (float) Math.sin(stroke) * 1.2f, swimBlend);
            rightArm.roll = blend(rightArm.roll, .3f, swimBlend);
            leftArm.roll = blend(leftArm.roll, -.3f, swimBlend);
            rightForearm.pitch = blend(rightForearm.pitch, -.45f, swimBlend);
            leftForearm.pitch = blend(leftForearm.pitch, -.45f, swimBlend);
            float kick = (float) Math.sin(stroke * 2) * .35f;
            rightLeg.pitch = blend(rightLeg.pitch, kick, swimBlend);
            leftLeg.pitch = blend(leftLeg.pitch, -kick, swimBlend);
            rightShin.pitch = blend(rightShin.pitch, .2f + Math.max(0, -kick), swimBlend);
            leftShin.pitch = blend(leftShin.pitch, .2f + Math.max(0, kick), swimBlend);
        }
        if (holdingPhysicsGun) {
            rightArm.pitch = -(float) Math.PI / 2;
            rightArm.roll = 0;
            rightForearm.pitch = 0;
        } else if (throwTicks >= 0)
            animateThrow(throwTicks + (age - (float) Math.floor(age)));
        else if (strikeTicks > 0) {
            float phase = (14 - strikeTicks) / 14f;
            float reach = (float) Math.sin(Math.min(1, phase * 1.7f) * Math.PI);
            if (kicking) {
                rightLeg.pitch = -.4f - reach * 1.4f;
                rightShin.pitch = .5f * (1 - reach);
                leftArm.pitch = -1;
                rightArm.pitch = .5f;
                body.pitch = .15f;
            } else {
                rightArm.pitch = -.5f - reach * 1.6f;
                rightForearm.pitch = -.8f * (1 - reach);
                leftArm.pitch = -1;
                leftForearm.pitch = -.8f;
                body.yaw = -reach * .2f;
            }
        }
    }

    private static float blend(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private record ThrowPose(float arm, float forearm, float windup, float swing, float recovery) {
    }

    private static ThrowPose throwPose(float ticks) {
        float release = StickFigurePowers.THROW_RELEASE_TICK;
        float windup = Math.min(1, ticks / (release - 3));
        float swing = Math.max(0, Math.min(1, (ticks - release + 3) / 3));
        float recovery = Math.max(
                0,
                1
                        - Math.max(0, ticks - release)
                                / (StickFigurePowers.THROW_END_TICK - release));
        return new ThrowPose(
                (-1.1f - 2.45f * windup + 2.1f * swing) * recovery,
                (-.4f - .5f * windup + .75f * swing) * recovery,
                windup,
                swing,
                recovery);
    }

    private void animateThrow(float ticks) {
        ThrowPose pose = throwPose(ticks);
        rightArm.pitch = leftArm.pitch = pose.arm();
        rightForearm.pitch = leftForearm.pitch = pose.forearm();
        rightArm.roll = leftArm.roll = 0;
        body.pitch = (-.2f * pose.windup() + .4f * pose.swing()) * pose.recovery();
        rightLeg.pitch = -.2f * pose.windup() * pose.recovery();
        leftLeg.pitch = .25f * pose.windup() * pose.recovery();
    }

    private static float blockWidth(int size) {
        return size == 2 ? 2f : BossThrownBlock.SMALL_BLOCK_WIDTH;
    }

    /** Block center above the feet and forward from the body at release. */
    public static Vec3D releaseOffset(int size) {
        ThrowPose pose = throwPose(StickFigurePowers.THROW_RELEASE_TICK);
        float upper = ARM_LENGTH * MODEL_SCALE, lower = upper + blockWidth(size) * .5f;
        float wrist = pose.arm() + pose.forearm();
        return new Vec3D(
                0,
                (GROUND_Y - SHOULDER_Y) * MODEL_SCALE
                        - upper * (float) Math.cos(pose.arm())
                        - lower * (float) Math.cos(wrist),
                -upper * (float) Math.sin(pose.arm()) - lower * (float) Math.sin(wrist));
    }

    public void renderHeldBlock(TextureManager textures, float scale, float brightness) {
        if (heldBlockId == 0)
            return;
        GL11.glPushMatrix();
        try {
            // Both arms share a pitch, so removing the shoulder's x offset gives their
            // midpoint.
            GL11.glTranslatef(-rightArm.x * scale, 0, 0);
            rightArm.applyTransform(scale);
            rightForearm.applyTransform(scale);
            float width = blockWidth(heldBlockSize);
            GL11.glTranslatef(0, ARM_LENGTH * scale + width * .5f, 0);
            GL11.glScalef(-1, -1, 1);
            BlockItemRenderer.renderCube(
                    Block.blocks[heldBlockId], textures, brightness, heldBlockSize, width);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public void render(float swing, float amount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, amount, age, yaw, pitch, scale);
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, GROUND_Y * scale - runBounce, 0);
            GL11.glRotatef(runLean, 1, 0, 0);
            GL11.glTranslatef(0, -GROUND_Y * scale, 0);
            if (swimBlend > 0 && throwTicks < 0 && !holdingPhysicsGun) {
                org.lwjgl.opengl.GL11.glTranslatef(0, 16 * scale, 0);
                org.lwjgl.opengl.GL11.glRotatef(-65 * swimBlend, 1, 0, 0);
                org.lwjgl.opengl.GL11.glTranslatef(0, -16 * scale, 0);
            }
            head.render(scale);
            body.render(scale);
            shoulders.render(scale);
            rightArm.render(scale);
            leftArm.render(scale);
            rightLeg.render(scale);
            leftLeg.render(scale);
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}
