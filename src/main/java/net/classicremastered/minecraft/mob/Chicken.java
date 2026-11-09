// File: src/com/mojang/minecraft/mob/Chicken.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;

public class Chicken extends Mob {

    private static final float WIDTH = .6F;
    private static final float HEIGHT = .95F;

    @Override
    public float getRenderBrightness(float partial) {
        return level.getRenderBrightness(
                (int) Math.floor(x),
                (int) Math.floor(bb.y0 + bbHeight * .65f),
                (int) Math.floor(z));
    }

    // --- Wing physics (Beta 1.8 style) ---
    public float wingRotation = 0.0F; // accumulates rotation
    public float destPos = 0.0F; // flap progress target
    public float wingSpeed = 0.0F; // flap speed
    private static final java.util.Random random = new java.util.Random();

    public Chicken(Level level, float x, float y, float z) {
        super(level);
        this.modelName = "chicken";
        this.textureName = "/mob/chicken.png";
        this.setSize(WIDTH, HEIGHT);
        this.setPos(x, y, z);
        this.health = 4;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        setSize(WIDTH, HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();

        // glide slowly
        if (!this.onGround && this.yd < 0.0F) {
            this.yd *= 0.6F;
        }

        // --- Beta 1.8 wing physics ---
        this.destPos = this.onGround ? (this.destPos - 0.3F) : (this.destPos + 1.2F);
        if (this.destPos < 0.0F) this.destPos = 0.0F;
        if (this.destPos > 1.0F) this.destPos = 1.0F;

        if (!this.onGround && this.wingSpeed < 1.0F) {
            this.wingSpeed = 1.0F;
        }

        this.wingSpeed *= 0.9F;
        this.wingRotation += this.wingSpeed * 2.0F;
    }

    @Override
    protected void causeFallDamage(float dist) {
        // chickens take no fall damage
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        super.die(killer);

        if (level == null || level.isNetworkMode() || !level.doMobLoot) return;

        // Drop 1–2 feathers
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            DroppedItem feather =
                    new DroppedItem(
                            level,
                            this.x + (random.nextFloat() - 0.5F), // random offset
                            this.y + 0.5F,
                            this.z + (random.nextFloat() - 0.5F),
                            Item.FEATHER.id);
            level.addEntity(feather);
        }
    }
}
