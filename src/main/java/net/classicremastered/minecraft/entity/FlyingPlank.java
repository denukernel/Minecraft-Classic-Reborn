package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.GlassBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.render.*;
import org.lwjgl.opengl.GL11;

public final class FlyingPlank extends Entity {
    private final Entity owner;
    private final boolean glass;
    private int age;
    public FlyingPlank(Level l, Entity owner, float x, float y, float z, float dx, float dy, float dz) {
        this(l, owner, x, y, z, dx, dy, dz, false);
    }

    public FlyingPlank(Level l, Entity owner, float x, float y, float z, float dx, float dy, float dz,
            boolean glass) {
        super(l);
        this.owner = owner;
        this.glass = glass;
        setSize(.7f, .7f);
        heightOffset = 0;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        xd = dx;
        yd = dy;
        zd = dz;
    }

    @Override
    public void tick() {
        if (removed)
            return;
        xo = x;
        yo = y;
        zo = z;
        yd -= .035f;
        Vec3D start = new Vec3D(x, y, z), end = start.add(xd, yd, zd);
        MovingObjectPosition wall = level.clip(new Vec3D(x, y, z), end, true);
        float nearest = wall == null ? Float.MAX_VALUE : start.distance(wall.vec);
        Mob target = null;
        for (Object o : level.findEntities(this, bb.expand(xd, yd, zd).grow(.4f, .4f, .4f)))
            if (o instanceof Mob m && m != owner && !m.removed && m.health > 0) {
                MovingObjectPosition hit = m.bb.grow(.35f, .35f, .35f).clip(start, end);
                if (hit != null && start.distance(hit.vec) < nearest) {
                    nearest = start.distance(hit.vec);
                    target = m;
                }
            }
        if (target != null) {
            target.hurt(owner, glass ? 3 : 4);
            shatter();
            return;
        }
        move(xd, yd, zd);
        yRot += 24;
        if (wall != null || collision || onGround || horizontalCollision || ++age >= 100)
            shatter();
    }

    private void shatter() {
        if (removed)
            return;
        removed = true;
        if (level.particleEngine != null)
            (glass ? Block.GLASS : Block.WOOD).spawnBreakParticles(level, (int) Math.floor(x),
                    (int) Math.floor(y), (int) Math.floor(z),
                    level.particleEngine);
        if (glass) {
            GlassBlock.playShatter(level, x, y, z);
        } else {
            level.playSound("step/wood", this, .8f, 1.1f);
            level.addEntity(new DroppedBlock(level, x, y, z, Block.WOOD.id));
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            GL11.glRotatef(yRot, 1, 1, 0);
            GL11.glScalef(.7f, .7f, .7f);
            GL11.glTranslatef(-.5f, -.5f, -.5f);
            BlockItemRenderer.render(glass ? Block.GLASS : Block.WOOD, tm,
                    getRenderBrightness(partial));
        } finally {
            GL11.glPopMatrix();
        }
    }
}
