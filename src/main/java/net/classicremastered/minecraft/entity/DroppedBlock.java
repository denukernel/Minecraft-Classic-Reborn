package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.minecraft.render.BlockItemRenderer;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public class DroppedBlock extends Entity {
    public int count = 1;

    public DroppedBlock(Level level1, float x, float y, float z, int block) {
        this(level1, x, y, z, block, 1);
    }

    public DroppedBlock(Level level1, float x, float y, float z, int block, int count) {
        super(level1);
        this.count = count;

        setSize(0.25F, 0.25F);

        heightOffset = bbHeight / 2.0F;

        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;

        resource = block;

        rot = (float) (Math.random() * 360.0D);

        xd = (float) (Math.random() * 0.20000000298023224D - 0.10000000149011612D);
        yd = 0.2F;
        zd = (float) (Math.random() * 0.20000000298023224D - 0.10000000149011612D);

        makeStepSound = false;
    }

    @Override
    public void tick() {
        if (removed) return;
        xo = x;
        yo = y;
        zo = z;

        yd -= 0.04F;
        move(xd, yd, zd);
        if (cactusDamagesStack()) return;

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
    public void render(TextureManager textures, float partial) {
        Block block = Block.blocks[resource];
        if (block == null) return;
        float ageInTicks = tickCount + partial;
        float hover = MathHelper.sin(ageInTicks / 10 + rot) * .1f + .1f;

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(xo + (x - xo) * partial, yo + (y - yo) * partial - heightOffset + .0625f + hover,
                    zo + (z - zo) * partial);
            GL11.glRotatef(rot + ageInTicks * 3, 0, 1, 0);
            GL11.glScalef(.25f, .25f, .25f);
            GL11.glTranslatef(-.5f, 0, -.5f);
            BlockItemRenderer.render(block, textures, getRenderBrightness(partial));
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public void playerTouch(Entity entity) {
        if (removed) return;
        if (!(entity instanceof Player)) return;

        // tiny delay to avoid instant double pickup when spawning in-player
        if (age < 10) return;

        Player player = (Player) entity;

        int added = 0;
        for (int i = 0; i < this.count; i++) {
            if (player.inventory.addResource(resource)) {
                added++;
            } else {
                break;
            }
        }

        if (added > 0) {
            // --- Play pickup pop sound (safe random) ---
            if (this.level != null) {
                float pitch = 1.0F + (MathHelper.random.nextFloat() - 0.5F) * 0.2F;
                this.level.playSound("random/pop", this.x, this.y, this.z, 0.2F, pitch);
            }

            this.count -= added;
            if (this.count <= 0) {
                if (level != null) level.addEntity(new TakeEntityAnim(level, this, player));
                remove();
            }
        }
    }

    public static final long serialVersionUID = 0L;

    private float rot;

    private int resource;

    private int tickCount;

    private int age = 0;

    public int getBlockId() {
        return resource;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = Math.max(0, age);
        this.tickCount = this.age;
    }
}
