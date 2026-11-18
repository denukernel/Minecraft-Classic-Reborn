// File: src/com/mojang/minecraft/mob/Chicken.java
package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.level.tile.Tile$SoundType;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.nbt.CompoundTag;

public class Chicken extends Mob {

    private static final long serialVersionUID = -9079409125521296344L;
    private static final float WIDTH = .6F;
    private static final float HEIGHT = .95F;
    private static final float CHICK_SCALE = .5F;
    private static final int GROWTH_TICKS = 24000;
    private int growthTicks;
    private int eggLayTicks;

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
        this.textureName = "/mob/chicken.png";
        this.setSize(WIDTH, HEIGHT);
        this.setPos(x, y, z);
        this.health = this.lastHealth = healthLimit();
        initSounds();
    }

    public static Chicken hatch(Level level, float x, float feetY, float z) {
        Chicken chick = new Chicken(level, x, feetY, z);
        chick.growthTicks = GROWTH_TICKS;
        chick.setSize(WIDTH * CHICK_SCALE, HEIGHT * CHICK_SCALE);
        chick.setPos(x, feetY + chick.bbHeight * .5f, z);
        chick.persistent = true;
        return chick;
    }

    @Override
    public boolean isBaby() {
        return growthTicks > 0;
    }

    @Override
    protected float modelScale() {
        return isBaby() ? CHICK_SCALE : super.modelScale();
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putInt("GrowthTicks", growthTicks);
        tag.putInt("EggLayTicks", eggLayTicks);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        growthTicks = tag.getInt("GrowthTicks");
        eggLayTicks = tag.getInt("EggLayTicks");
        setSize(WIDTH * modelScale(), HEIGHT * modelScale());
    }

    @Override
    protected final int healthLimit() {
        return 4;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        setSize(WIDTH * modelScale(), HEIGHT * modelScale());
        initSounds();
    }

    private void initSounds() {
        soundIdle = "mob/chicken/say";
        soundHurt = soundDeath = "mob/chicken/hurt";
    }

    @Override
    protected void playStepSound(Tile$SoundType groundSound) {
        if (level.minecraft == null
                || level.minecraft.settings == null
                || level.minecraft.settings.allowMobSounds) {
            playPositionalSound("mob/chicken/step", .15f, 1f);
        }
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

        if (health > 0 && !removed && !level.isNetworkMode()) {
            if (isBaby()) tickGrowth();
            else if (level.doMobLoot && riding == null && rider == null) tickEggLaying();
        }
    }

    private void tickGrowth() {
        if (growthTicks > 1) {
            growthTicks--;
            return;
        }

        float feet = bb.y0;
        AABB grown =
                new AABB(
                        x - WIDTH / 2,
                        feet,
                        z - WIDTH / 2,
                        x + WIDTH / 2,
                        feet + HEIGHT,
                        z + WIDTH / 2);
        if (!level.getCubes(grown).isEmpty()) return;
        growthTicks = 0;
        setSize(WIDTH, HEIGHT);
        bb = grown;
    }

    private void tickEggLaying() {
        if (eggLayTicks <= 0) eggLayTicks = 6000 + level.random.nextInt(6000);
        if (--eggLayTicks == 0) {
            ItemRegistry.bootstrap();
            level.addEntity(new DroppedItem(level, x, bb.y0 + .2f, z, Item.EGG.id));
        }
    }

    @Override
    protected void causeFallDamage(float dist) {
        // chickens take no fall damage
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        super.die(killer);

        if (level == null || level.isNetworkMode() || !level.doMobLoot || isBaby()) return;
        ItemRegistry.bootstrap();
        level.addEntity(new DroppedItem(level, x, y, z, Item.RAW_CHICKEN.id));

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
