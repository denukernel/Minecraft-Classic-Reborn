package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.item.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class BowItem extends ToolItem {
    private boolean isDrawing = false;
    private int drawTicks = 0;

    public BowItem(int id) {
        super(id, "Bow", "/items/bow.png");
    }

    @Override
    public void use(Player player, Level level) {
        isDrawing = true;
        drawTicks = 0;
        System.out.println("[Bow] use() start drawing");
    }

    @Override
    public void tick(Player player, Level level) {
        if (isDrawing) {
            drawTicks++;
            // clamp to max charge
            if (drawTicks > 20) drawTicks = 20;
            // debug
            // System.out.println("[Bow] tick, drawTicks=" + drawTicks);
        }
    }

    @Override
    public void releaseUse(Player player, Level level) {
        if (isDrawing) {
            if (player.arrows > 0) {
                int power = Math.max(1, drawTicks / 7); // 0..2 → 1..3 power steps
                float velocity = 0.9F + (0.3F * power);

                level.addEntity(new Arrow(
                    level,
                    player,
                    player.x,
                    player.y,
                    player.z,
                    player.yRot,
                    player.xRot,
                    velocity
                ));

                player.arrows--; // consume one arrow
                if (player.minecraft != null && player.minecraft.hud != null) {
                    player.minecraft.hud.addChat("&7Shot an arrow! Remaining: " + player.arrows);
                }
            } else {
                if (player.minecraft != null && player.minecraft.hud != null) {
                    player.minecraft.hud.addChat("&cNo arrows left!");
                }
            }
        }
        isDrawing = false;
        drawTicks = 0;
    }


    @Override
    public String getTexture() {
        if (isDrawing) {
            if (drawTicks > 14) return "/items/bow_pulling_2.png";
            if (drawTicks > 7)  return "/items/bow_pulling_1.png";
            return "/items/bow_pulling_0.png";
        }
        return "/items/bow.png";
    }
}
