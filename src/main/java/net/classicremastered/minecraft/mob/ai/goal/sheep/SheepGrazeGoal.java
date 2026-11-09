package net.classicremastered.minecraft.mob.ai.goal.sheep;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.mob.ai.sheep.SheepAI;
import net.classicremastered.util.MathHelper;

public final class SheepGrazeGoal extends MobGoal<SheepAI> {

    private Sheep sheep;
    public SheepGrazeGoal(SheepAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        if (!super.canStart() || !(ai.mob instanceof Sheep))
            return false;
        sheep = (Sheep) ai.mob;
        return sheep.grazing || (ai.mob.onGround && ai.random.nextInt(500) == 0);
    }

    @Override
    public void tick() {
        int x = MathHelper.floor(ai.mob.x);
        int y = MathHelper.floor(ai.mob.bb.y0 - 0.01F);
        int z = MathHelper.floor(ai.mob.z);
        if (!ai.mob.onGround || ai.level.getTile(x, y, z) != Block.GRASS.id) {
            sheep.grazing = false;
            ai.defaultMovement();
            return;
        }

        if (!sheep.grazing) {
            sheep.grazing = true;
            sheep.grazingTime = 0;
        } else if (++sheep.grazingTime >= 60) {
            ai.level.setTile(x, y, z, Block.DIRT.id);
            if (ai.random.nextInt(5) == 0) {
                sheep.hasFur = true;
            }
            sheep.grazing = false;
        }

        ai.xxa = 0.0F;
        ai.yya = 0.0F;
        ai.jumping = false;
        ai.mob.xRot = (float) (40 + sheep.grazingTime / 2 % 2 * 10);
    }
}
