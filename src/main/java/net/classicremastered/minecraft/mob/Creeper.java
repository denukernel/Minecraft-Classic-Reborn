package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.*;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.mob.ai.creeper.CreeperAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.util.CreativeModeHelper;

public class Creeper extends Mob {

    public static final long serialVersionUID = 0L;
    public static final int FUSE_LENGTH = 30;
    public int fuseTicks, previousFuseTicks;
    public boolean ignited;

    public Creeper(Level level, float x, float y, float z) {
        super(level);
        heightOffset = 1.62f;
        modelName = "creeper";
        textureName = "/mob/creeper.png";
        ai = new CreeperAI();
        deathScore = 200;
        coinDrop = 4;
        footSize = .45f;
        initSounds();
        setPos(x, y, z);
    }

    private void initSounds() {
        soundHurt = "mob/creeper/say";
        soundDeath = "mob/creeper/death";
    }

    private void readObject(java.io.ObjectInputStream input)
            throws java.io.IOException, ClassNotFoundException {
        input.defaultReadObject();
        initSounds();
    }

    @Override
    public void tick() {
        // Migrate saved creepers away from the legacy melee AI.
        if (!(ai instanceof CreeperAI)) ai = new CreeperAI();
        super.tick();
        previousFuseTicks = fuseTicks;
        if (health <= 0 || removed || level == null) return;
        Entity target = ((BasicAttackAI) ai).attackTarget;
        boolean valid =
                target instanceof Mob m
                        && m.health > 0
                        && !m.removed
                        && !CreativeModeHelper.shouldIgnoreTarget(target)
                        && CombatRules.difficulty(level) != Difficulty.PEACEFUL;
        float distance = valid ? distanceTo(target) : Float.MAX_VALUE;
        boolean clear =
                valid
                        && level.clip(
                                        new Vec3D(x, bb.y0 + bbHeight * .85f, z),
                                        new Vec3D(
                                                target.x,
                                                target.bb.y0 + target.bbHeight * .5f,
                                                target.z),
                                        true)
                                == null;
        ignited = clear && distance < (ignited ? 6f : 2.5f);
        if (!valid) {
            fuseTicks = previousFuseTicks = 0;
            ignited = false;
            return;
        }
        if (ignited) {
            if (fuseTicks == 0) level.playSound("random/fuse", this, 1, .8f);
            fuseTicks++;
            xd = zd = 0;
            if (fuseTicks >= FUSE_LENGTH) {
                remove();
                level.playSound("random/explode", this, 1, 1);
                level.explode(this, x, bb.y0, z, 3f);
            }
        } else fuseTicks = Math.max(0, fuseTicks - 1);
    }

    public float fuseProgress(float partial) {
        return Math.min(
                1, (previousFuseTicks + (fuseTicks - previousFuseTicks) * partial) / FUSE_LENGTH);
    }
}
