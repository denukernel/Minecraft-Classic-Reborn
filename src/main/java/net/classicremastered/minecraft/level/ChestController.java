package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.RedstoneEngine.State;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.BlockModels;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.Frustrum;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

import org.lwjgl.opengl.GL11;

/** Joining and animated rendering share the same per-block inventories used by world saves. */
public final class ChestController {
    public record View(Pos first, Pos second, State firstState, State secondState) {
        public int size() {
            return second == null ? 27 : 54;
        }

        public State state(int slot) {
            return slot < 27 ? firstState : secondState;
        }
    }

    private ChestController() {}

    public static int right(int facing) {
        return switch (facing) {
            case 2 -> 5;
            case 3 -> 4;
            case 4 -> 2;
            default -> 3;
        };
    }

    private static boolean exists(Level level, Pos pos) {
        if (!level.isInBounds(pos.x(), pos.y(), pos.z())) return false;
        if (level instanceof LevelInfiniteTerrain terrain
                && !terrain.chunks().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return false;
        if (level instanceof LevelInfiniteFlat flat
                && !flat.chunks().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return false;
        return level.getTile(pos.x(), pos.y(), pos.z()) == Block.CHEST.id;
    }

    private static State state(Level level, Pos pos) {
        return level.redstone().state(pos.x(), pos.y(), pos.z());
    }

    public static Pos partner(Level level, Pos pos) {
        State state = state(level, pos);
        if (state.chestPartner < 2 || state.chestPartner > 5) return null;
        Pos other = pos.step(state.chestPartner);
        if (!exists(level, other)) return null;
        State neighbor = state(level, other);
        return neighbor.facing == state.facing && neighbor.chestPartner == (state.chestPartner ^ 1)
                ? other
                : null;
    }

    public static void join(Level level, Pos pos) {
        if (!exists(level, pos)) return;
        State state = state(level, pos);
        if (partner(level, pos) != null) return;
        state.chestPartner = -1;
        int right = right(state.facing);
        for (int direction : new int[] {right, right ^ 1}) {
            Pos other = pos.step(direction);
            if (!exists(level, other)) continue;
            State neighbor = state(level, other);
            if (neighbor.facing != state.facing || neighbor.chestPartner >= 2) continue;
            state.chestPartner = direction;
            neighbor.chestPartner = direction ^ 1;
            return;
        }
    }

    public static void removed(Level level, Pos pos, State old) {
        if (old.chestPartner < 2) return;
        Pos other = pos.step(old.chestPartner);
        if (exists(level, other) && state(level, other).chestPartner == (old.chestPartner ^ 1))
            state(level, other).chestPartner = -1;
    }

    public static View view(Level level, Pos pos) {
        if (!exists(level, pos)) return null;
        Pos other = partner(level, pos);
        if (other != null && state(level, pos).chestPartner != right(state(level, pos).facing)) {
            Pos swap = pos;
            pos = other;
            other = swap;
        }
        return new View(pos, other, state(level, pos), other == null ? null : state(level, other));
    }

    public static boolean valid(Level level, View view) {
        if (view == null || !exists(level, view.first)) return false;
        View current = view(level, view.first);
        return current.first.equals(view.first)
                && java.util.Objects.equals(current.second, view.second)
                && current.firstState == view.firstState
                && current.secondState == view.secondState;
    }

    public static boolean canOpen(Level level, View view) {
        if (view == null) return false;
        return !blocked(level, view.first) && (view.second == null || !blocked(level, view.second));
    }

    private static boolean blocked(Level level, Pos pos) {
        int id = level.getTile(pos.x(), pos.y() + 1, pos.z());
        return id > 0 && Block.blocks[id] != null && Block.blocks[id].isSolid();
    }

    public static void open(Level level, View view) {
        if (view == null) return;
        if (view.firstState.chestUsers == 0) sound(level, view, "random/chestopen");
        view.firstState.chestUsers++;
        if (view.secondState != null) view.secondState.chestUsers++;
    }

    public static void close(Level level, View view) {
        if (view == null || view.firstState.chestUsers == 0) return;
        view.firstState.chestUsers = Math.max(0, view.firstState.chestUsers - 1);
        if (view.secondState != null)
            view.secondState.chestUsers = Math.max(0, view.secondState.chestUsers - 1);
        if (view.firstState.chestUsers == 0) sound(level, view, "random/chestclosed");
    }

    private static void sound(Level level, View view, String name) {
        Pos a = view.first, b = view.second == null ? a : view.second;
        level.playSound(
                name,
                (a.x() + b.x()) * .5f + .5f,
                a.y() + .5f,
                (a.z() + b.z()) * .5f + .5f,
                .5f,
                1);
    }

    public static void tick(Level level) {
        for (Pos pos : level.redstone().chestPositions()) {
            State state = state(level, pos);
            // Existing worlds gain pairs when their neighboring chunks become available.
            if (state.chestPartner < 2) join(level, pos);
            state.previousLid = state.lid;
            state.lid =
                    state.chestUsers > 0
                            ? Math.min(1, state.lid + .1f)
                            : Math.max(0, state.lid - .1f);
        }
    }

    public static float openness(State state, float partial) {
        float open = state.previousLid + (state.lid - state.previousLid) * partial;
        return 1 - (1 - open) * (1 - open) * (1 - open);
    }

    public static AABB bounds(Level level, Pos pos) {
        Pos other = partner(level, pos);
        return new AABB(
                pos.x() + (other != null && other.x() < pos.x() ? 0 : .0625f),
                pos.y(),
                pos.z() + (other != null && other.z() < pos.z() ? 0 : .0625f),
                pos.x() + (other != null && other.x() > pos.x() ? 1 : .9375f),
                pos.y() + .875f,
                pos.z() + (other != null && other.z() > pos.z() ? 1 : .9375f));
    }

    public static int signal(Level level, Pos pos) {
        View view = view(level, pos);
        if (view == null) return 0;
        int total = 0;
        for (int slot = 0; slot < view.size(); slot++)
            total += Math.min(64, view.state(slot).counts[slot % 27]);
        return total == 0 ? 0 : 1 + 14 * total / (view.size() * 64);
    }

    public static void render(Level level, TextureManager textures, Frustrum frustum) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            for (Pos pos : level.redstone().chestPositions()) {
                if (frustum != null
                        && !frustum.isBoxInFrustrum(
                                pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 2, pos.z() + 1))
                    continue;
                State state = state(level, pos);
                Pos other = partner(level, pos);
                int half = other == null ? 0 : state.chestPartner == right(state.facing) ? 1 : -1;
                String texture =
                        half == 0 ? "chest.png" : half == 1 ? "chest_left.png" : "chest_right.png";
                GL11.glBindTexture(
                        GL11.GL_TEXTURE_2D, textures.load("/textures/entity/" + texture));
                float open = openness(state, level.renderPartial);
                if (other != null)
                    open = Math.max(open, openness(state(level, other), level.renderPartial));
                ShapeRenderer renderer = ShapeRenderer.instance;
                renderer.begin();
                BlockModels.chest(
                        renderer,
                        pos.x(),
                        pos.y(),
                        pos.z(),
                        state.facing,
                        level.getRenderBrightness(pos.x(), pos.y() + 1, pos.z()),
                        open,
                        half,
                        true);
                renderer.end();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
