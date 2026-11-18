package net.classicremastered.minecraft.mob;

import java.util.Random;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.nbt.CompoundTag;

public class Sheep extends QuadrupedMob {

    public static final long serialVersionUID = 0L;
    public boolean hasFur = true;
    public boolean grazing = false;
    public int grazingTime = 0;
    public float graze;
    public float grazeO;
    private Random random;

    public Sheep(Level var1, float var2, float var3, float var4) {
        super(var1, var2, var3, var4);
        this.health = this.lastHealth = healthLimit();
        this.setSize(1.4F, 1.72F);
        this.setPos(var2, var3, var4);
        this.heightOffset = 1.72F;
        this.soundIdle = "mob/sheep/say";
        this.soundHurt = "mob/sheep/say";
        this.soundDeath = "mob/sheep/say";
        this.textureName = "/mob/sheep.png";
        this.ai = new net.classicremastered.minecraft.mob.ai.sheep.SheepAI();
        this.random = new Random(); // <--- fix
    }

    @Override
    protected final int healthLimit() {
        return 8;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        soundIdle = soundHurt = soundDeath = "mob/sheep/say";
    }

    public void aiStep() {
        super.aiStep();
        this.grazeO = this.graze;
        if (this.grazing) {
            this.graze += 0.2F;
        } else {
            this.graze -= 0.2F;
        }

        if (this.graze < 0.0F) {
            this.graze = 0.0F;
        }

        if (this.graze > 1.0F) {
            this.graze = 1.0F;
        }
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        if (killer != null) killer.awardKillScore(this, 10);
        super.die(killer);
        if (level != null && !level.isNetworkMode() && level.doMobLoot) {
            ItemRegistry.bootstrap();
            level.addEntity(
                    new DroppedItem(
                            level, x, y, z, Item.RAW_MUTTON.id, 1 + level.random.nextInt(2)));
        }
    }

    public boolean shear() {
        if (!hasFur || health <= 0 || dead || removed || level == null || level.isNetworkMode())
            return false;
        hasFur = false;
        if (!level.creativeMode) {
            int dropCount = 2 + level.random.nextInt(3);
            for (int i = 0; i < dropCount; ++i) {
                level.addEntity(new DroppedBlock(level, x, y, z, Block.WHITE_WOOL.id));
            }
        }
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return !hasFur || super.isPersistenceRequired();
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putBoolean("Sheared", !hasFur);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        hasFur = !tag.getBoolean("Sheared");
    }
}
