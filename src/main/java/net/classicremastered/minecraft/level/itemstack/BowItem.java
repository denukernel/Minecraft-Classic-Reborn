package net.classicremastered.minecraft.level.itemstack;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class BowItem extends ToolItem {
    private static final Map<Player, Integer> drawing = new WeakHashMap<>();
    private static WeakReference<Player> active = new WeakReference<>(null);
    public BowItem(int id) {
        super(id, "Bow", "/items/bow.png");
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || level == null || player.health <= 0 || player.removed)
            return;
        drawing.put(player, 0);
        active = new WeakReference<>(player);
    }

    @Override
    public void tick(Player player, Level level) {
        if (drawing.containsKey(player)) {
            drawing.put(player, Math.min(20, drawing.get(player) + drawRate(player)));
        }
    }

    private int drawRate(Player player) {
        return player.developerPowers().active(player) ? 3 : 1;
    }

    public boolean isDrawing(Player player) {
        return drawing.containsKey(player);
    }

    public float getDrawProgress(Player player, float partial) {
        Integer ticks = drawing.get(player);
        return ticks == null ? 0 : Math.max(0, Math.min(1, (ticks + partial * drawRate(player)) / 20f));
    }

    public static void cancelIfNotSelected(Player player) {
        if (player != null && (player.inventory.getSelected() != 262 || player.health <= 0 || player.removed))
            drawing.remove(player);
    }

    public static void cancelDraw(Player player) {
        drawing.remove(player);
    }

    @Override
    public void releaseUse(Player player, Level level) {
        Integer ticks = drawing.remove(player);
        if (ticks == null || player == null || level == null || player.health <= 0 || player.removed)
            return;
        int ammo = Equipment.ARROW.id + 256;
        if (level.creativeMode || player.inventory.removeResource(ammo)) {
            float draw = ticks / 20f;
            float velocity = (.7f + 1.7f * draw) * drawRate(player);
            Arrow arrow =
                    new Arrow(
                            level,
                            player,
                            player.x,
                            player.y,
                            player.z,
                            player.aimYaw(1f),
                            player.xRot,
                            velocity);
            // Start on the crosshair ray. The legacy constructor offsets arrows sideways.
            arrow.setPos(player.x, player.y, player.z);
            arrow.xo = player.x;
            arrow.yo = player.y;
            arrow.zo = player.z;
            level.addEntity(arrow);
            level.playSound("random/bow", player, 1f, .9f + .25f * draw);
        }
    }

    public String getTexture(Player player) {
        Integer ticks = drawing.get(player);
        if (ticks == null)
            return "/items/bow.png";
        return ticks > 14
                ? "/items/bow_pulling_2.png"
                : ticks > 7 ? "/items/bow_pulling_1.png" : "/items/bow_pulling_0.png";
    }

    @Override
    public String getTexture() {
        return getTexture(active.get());
    }
}
