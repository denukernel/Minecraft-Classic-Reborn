package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.render.TextureManager;
import org.lwjgl.opengl.GL11;

public class TakeEntityAnim extends Entity {
    private static final long serialVersionUID = 1L;
    private static final int DURATION = 3;

    private int time;
    private final Entity item;
    private final Entity player;
    private final float xorg, yorg, zorg;

    public TakeEntityAnim(Level level, Entity item, Entity player) {
        super(level);
        this.item = item;
        this.player = player;
        xorg = item.x;
        yorg = item.y;
        zorg = item.z;
        setSize(1, 1);
        setPos(xorg, yorg, zorg);
    }

    @Override
    public void tick() {
        if (++time >= DURATION || player.removed) {
            remove();
            return;
        }
        float progress = progress(0);
        setPos(lerp(xorg, player.x, progress),
                lerp(yorg, player.y - player.heightOffset / 2, progress),
                lerp(zorg, player.z, progress));
    }

    @Override
    public void render(TextureManager textures, float partial) {
        float progress = progress(partial);
        float x = lerp(xorg, lerp(player.xo, player.x, partial), progress);
        float y = lerp(yorg, lerp(player.yo, player.y, partial) - player.heightOffset / 2, progress);
        float z = lerp(zorg, lerp(player.zo, player.z, partial), progress);

        GL11.glPushMatrix();
        try {
            // Move the rendered mesh without changing the collected entity.
            GL11.glTranslatef(x - lerp(item.xo, item.x, partial),
                    y - lerp(item.yo, item.y, partial),
                    z - lerp(item.zo, item.z, partial));
            item.render(textures, partial);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private float progress(float partial) {
        float elapsed = (time + partial) / DURATION;
        return elapsed * elapsed;
    }

    private static float lerp(float from, float to, float partial) {
        return from + (to - from) * partial;
    }
}
