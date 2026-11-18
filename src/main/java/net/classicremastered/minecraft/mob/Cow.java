package net.classicremastered.minecraft.mob;

import java.io.IOException;
import java.io.ObjectInputStream;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.SaddleItem;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.nbt.CompoundTag;

public final class Cow extends QuadrupedMob {
    public static final long serialVersionUID = 0L;
    private boolean saddled;

    public Cow(Level level, float x, float y, float z) {
        super(level, x, y, z);
        setSize(.9f, 1.4f);
        heightOffset = .9f;
        setPos(x, y, z);
        health = lastHealth = healthLimit();
        persistent = true;
        textureName = "/mob/cow.png";
        initSounds();
    }

    @Override
    protected int healthLimit() {
        return 10;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initSounds();
    }

    private void initSounds() {
        soundIdle = "mob/cow/classic_idle";
        soundHurt = "mob/cow/classic_hurt";
    }

    public boolean isSaddled() {
        return saddled;
    }

    @Override
    public float getSeatY() {
        // The seated humanoid hip is 12 model pixels above this anchor.
        return bb.y0 + .658f;
    }

    public void saddle() {
        saddled = true;
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        if (saddled)
            tag.putBoolean("Saddled", true);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        saddled = tag.getBoolean("Saddled");
    }

    @Override
    public void die(Entity killer) {
        if (!dead && saddled && level.doMobLoot && !level.isNetworkMode())
            level.addEntity(new net.classicremastered.minecraft.entity.DroppedItem(level, x, y, z,
                    SaddleItem.ID));
        saddled = false;
        super.die(killer);
    }

    @Override
    public void hurt(Entity attacker, int damage) {
        int before = health;
        super.hurt(attacker, damage);
        if (health <= 0 || health >= before || !(ai instanceof BasicAI behavior)) return;
        float dx = attacker == null ? 0 : x - attacker.x;
        float dz = attacker == null ? 0 : z - attacker.z;
        float distance = (float) Math.hypot(dx, dz);
        if (distance < .001f) {
            double heading = level.random.nextDouble() * Math.PI * 2;
            dx = (float) Math.cos(heading);
            dz = (float) Math.sin(heading);
            distance = 1;
        }
        behavior.navigateTo(x + dx * 6 / distance, bb.y0, z + dz * 6 / distance, 1.3f);
    }
}
