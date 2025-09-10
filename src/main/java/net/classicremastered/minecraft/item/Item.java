package net.classicremastered.minecraft.item;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import java.util.Random;

import org.lwjgl.opengl.GL11;

public class Item extends Entity {
    private int pickupDelay = 10; // ~0.5s at 20 tps (tune)
    private boolean pickingUp = false; // one-shot guard
    private static final java.util.Random rand = new java.util.Random();

    public Item(Level level1, float x, float y, float z, int block) {
        super(level1);

        setSize(0.25F, 0.25F);

        heightOffset = bbHeight / 2.0F;

        setPos(x, y, z);

        resource = block;

        rot = (float) (Math.random() * 360.0D);

        xd = (float) (Math.random() * 0.20000000298023224D - 0.10000000149011612D);
        yd = 0.2F;
        zd = (float) (Math.random() * 0.20000000298023224D - 0.10000000149011612D);

        makeStepSound = false;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;

        yd -= 0.04F;
        move(xd, yd, zd);

        xd *= 0.98F;
        yd *= 0.98F;
        zd *= 0.98F;

        if (onGround) {
            xd *= 0.7F;
            zd *= 0.7F;
            yd *= -0.5F;
        }

        tickCount++;
        age++;

        if (age >= 6000) {
            remove();
        }
    }

    @Override
    public void render(TextureManager textureManager, float unknown0) {
        textureId = textureManager.load("/terrain.png");

        GL11.glBindTexture(3553, this.textureId);

        float brightness = level.getBrightness((int) x, (int) y, (int) z);
        float unknown1 = rot + ((float) tickCount + unknown0) * 3.0F;

        GL11.glPushMatrix();
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);

        float unknown2 = (brightness = MathHelper.sin(unknown1 / 10.0F)) * 0.1F + 0.1F;

        GL11.glTranslatef(xo + (x - xo) * unknown0, yo + (y - yo) * unknown0 + unknown2, zo + (z - zo) * unknown0);
        GL11.glRotatef(unknown1, 0.0F, 1.0F, 0.0F);

        models[resource].generateList();

        brightness = (brightness = (brightness = brightness * 0.5F + 0.5F) * brightness) * brightness;

        GL11.glColor4f(1.0F, 1.0F, 1.0F, brightness * 0.4F);
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 1);
        GL11.glDisable(3008);

        models[resource].generateList();

        GL11.glEnable(3008);
        GL11.glDisable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
        GL11.glEnable(3553);
    }

    @Override
    public void playerTouch(Entity entity) {
        if (removed) return;
        if (!(entity instanceof Player)) return;

        // tiny delay to avoid instant double pickup when spawning in-player
        if (age < 10) return; // ~0.5s at 20 TPS

        Player player = (Player) entity;

        if (player.inventory.addResource(resource)) {
            // --- Play pickup pop sound (safe random) ---
            if (this.level != null) {
                float pitch = 1.0F + (MathHelper.random.nextFloat() - 0.5F) * 0.2F;
                this.level.playSound("random/pop", this.x, this.y, this.z, 0.2F, pitch);
            }

            // optional: level.addEntity(new TakeEntityAnim(level, this, player));
            remove();
        }
    }


    public static final long serialVersionUID = 0L;

    private static ItemModel[] models = new ItemModel[256];

    private float xd;
    private float yd;
    private float zd;

    private float rot;

    private int resource;

    private int tickCount;

    private int age = 0;

    public static void initModels() {
        for (int unknown0 = 0; unknown0 < 256; unknown0++) {
            Block var1 = Block.blocks[unknown0];

            if (var1 != null) {
                models[unknown0] = new ItemModel(var1.textureId);
            }
        }

    }
}
