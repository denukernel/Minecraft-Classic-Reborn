// File: src/net/classicremastered/minecraft/mob/SignMob.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.combat.HostileTargets;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public final class SignMob extends Mob {

    private transient float boardYaw, previousBoardYaw;

    private final String[] taunts = {"KEEP OUT!", "NO TRESPASSING", "STOP!", "TURN BACK!"};
    private String currentText = "KEEP OUT!";

    public SignMob(Level level, float x, float y, float z) {
        super(level);

        this.setSize(SignEntity.MODEL_FOOTPRINT, SignEntity.MODEL_HEIGHT);
        this.heightOffset = this.bbHeight / 2.0F;
        this.setPos(x, y, z);

        this.health = 10;
        this.deathScore = 5;
        this.soundIdle = "random/wood_click";
        this.soundHurt = "random/wood_click";
        this.soundDeath = "random/pop";

        // ✅ must not be null or empty, otherwise SpawnMobCommand skips it
        this.modelName = "signmob";
    }

    @Override
    public void aiStep() {
        BasicAI movement = (BasicAI) ai;
        movement.bind(level, this);
        Player target = level.getNearestPlayer(x, y, z, 16f);
        if (target != null
                && (target.removed
                        || target.health <= 0
                        || CreativeModeHelper.shouldIgnoreTarget(target)
                        || !HostileTargets.canLocate(this, target))) target = null;
        movement.setPursuitTarget(target);
        super.aiStep();
    }

    @Override
    public void tick() {
        previousBoardYaw = boardYaw;
        super.tick();

        if (health <= 0
                || removed
                || !(((BasicAI) ai).attackTarget instanceof Player target)
                || target.removed
                || target.health <= 0) return;

        float desired = (float) Math.toDegrees(Math.atan2(target.x - x, target.z - z));
        float turn = (float) Math.IEEEremainder(desired - boardYaw, 360);
        boardYaw = previousBoardYaw + Math.max(-30f, Math.min(30f, turn));

        if (attackTime == 0
                && Math.hypot(target.x - x, target.z - z) < 1.5f
                && target.bb.y0 < bb.y1
                && target.bb.y1 > bb.y0
                && HostileTargets.hasLineOfSight(this, target)) {
            attackTime = ATTACK_DURATION;
            target.hurt(this, 2);
            currentText = taunts[level.random.nextInt(taunts.length)];
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        SignEntity.renderStandingSign(
                tm,
                level == null || level.minecraft == null ? null : level.minecraft.fontRenderer,
                xo + (x - xo) * partial,
                renderFeetY(partial),
                zo + (z - zo) * partial,
                previousBoardYaw + (boardYaw - previousBoardYaw) * partial,
                currentText);
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        super.die(killer);
        if (level == null || level.isNetworkMode() || !level.doMobLoot) return;
        // Drop wood when killed
        this.level.addEntity(
                new net.classicremastered.minecraft.entity.DroppedBlock(
                        this.level,
                        this.x,
                        this.y,
                        this.z,
                        net.classicremastered.minecraft.level.tile.Block.WOOD.id));
    }
}
