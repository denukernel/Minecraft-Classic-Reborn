package net.classicremastered.minecraft.level.tile;

import java.util.List;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.RedstoneEngine;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

public class CircuitBlock extends Block {
    public enum Kind {
        WIRE,
        TORCH,
        NORMAL_TORCH,
        LEVER,
        BUTTON,
        PLATE,
        REPEATER,
        COMPARATOR,
        SOURCE,
        LAMP,
        LIT_LAMP,
        PISTON,
        STICKY_PISTON,
        HEAD
    }

    public final Kind kind;

    public CircuitBlock(int id, String name, Kind kind, int texture) {
        super(id, texture, name);
        this.kind = kind;
        setData(Tile$SoundType.stone, 1, 1, kind == Kind.WIRE ? 0 : .5f);
        if (!isSolid()) setBounds(.1f, 0, .1f, .9f, isTorch() ? .625f : .15f, .9f);
    }

    @Override
    public boolean isSolid() {
        return kind == Kind.SOURCE
                || kind == Kind.LAMP
                || kind == Kind.LIT_LAMP
                || kind == Kind.PISTON
                || kind == Kind.STICKY_PISTON
                || kind == Kind.HEAD;
    }

    @Override
    public boolean isCube() {
        return isSolid();
    }

    @Override
    public boolean isOpaque() {
        return isSolid() && kind != Kind.PISTON && kind != Kind.STICKY_PISTON && kind != Kind.HEAD;
    }

    @Override
    public int getLightValue() {
        return kind == Kind.LIT_LAMP
                ? 15
                : kind == Kind.NORMAL_TORCH ? 14 : kind == Kind.TORCH ? 7 : 0;
    }

    @Override
    public AABB getCollisionBox(int x, int y, int z) {
        return isSolid() ? super.getCollisionBox(x, y, z) : null;
    }

    public static int horizontalFacing(Player player) {
        if (player == null) return 3;
        return new int[] {3, 4, 2, 5}[Math.floorMod((int) Math.floor(player.yRot / 90f + .5f), 4)];
    }

    private boolean isTorch() {
        return kind == Kind.TORCH || kind == Kind.NORMAL_TORCH;
    }

    private boolean isPiston() {
        return kind == Kind.PISTON || kind == Kind.STICKY_PISTON;
    }

    public boolean needsFloorSupport() {
        return kind == Kind.WIRE
                || kind == Kind.PLATE
                || kind == Kind.REPEATER
                || kind == Kind.COMPARATOR;
    }

    public boolean needsSupport() {
        return isTorch() || kind == Kind.BUTTON || kind == Kind.LEVER || needsFloorSupport();
    }

    @Override
    public boolean canPlaceOnFace(Level level, int x, int y, int z, int face) {
        if (!needsSupport()) return true;
        if ((isTorch() && face == 0) || (needsFloorSupport() && face != 1)) return false;
        Pos support = new Pos(x, y, z).step(face ^ 1);
        Block block = blocks[level.getTile(support.x(), support.y(), support.z())];
        return block != null
                && block.occludesFace(level, support.x(), support.y(), support.z(), face);
    }

    @Override
    public boolean occludesFace(Level level, int x, int y, int z, int face) {
        if (kind == Kind.HEAD) return false;
        if (!isPiston()) return super.occludesFace(level, x, y, z, face);
        RedstoneEngine.State state = level.redstone().state(x, y, z);
        return !state.extended || face == (state.facing ^ 1);
    }

    private AABB[] modelBounds(Level level, int x, int y, int z) {
        RedstoneEngine.State state = level.redstone().state(x, y, z);
        int facing = state.facing;
        if (kind == Kind.HEAD) {
            return new AABB[] {
                BlockModels.bounds(x, y, z, 0, 0, 0, 16, 16, 4, facing),
                BlockModels.bounds(x, y, z, 6, 6, 4, 10, 10, 16, facing)
            };
        }
        if (isPiston()) {
            AABB base =
                    BlockModels.bounds(x, y, z, 0, 0, state.extended ? 4 : 0, 16, 16, 16, facing);
            if (!state.extended) return new AABB[] {base};
            return new AABB[] {base, BlockModels.bounds(x, y, z, 6, 6, 0, 10, 10, 4, facing)};
        }
        if (kind == Kind.BUTTON) {
            return new AABB[] {
                BlockModels.bounds(x, y, z, 5, 6, state.on ? 15 : 14, 11, 10, 16, facing)
            };
        }
        if (kind == Kind.LEVER)
            return new AABB[] {BlockModels.leverBounds(x, y, z, state.mount, facing)};
        if (isTorch()) return new AABB[] {BlockModels.torchBounds(x, y, z, facing)};
        if (kind == Kind.REPEATER || kind == Kind.COMPARATOR) {
            AABB base = BlockModels.bounds(x, y, z, 0, 0, 0, 16, 2, 16, facing);
            if (kind == Kind.COMPARATOR)
                return new AABB[] {
                    base,
                    BlockModels.bounds(x, y, z, 7, 2, 2, 9, state.on ? 7 : 5, 4, facing),
                    BlockModels.bounds(x, y, z, 4, 2, 11, 6, 7, 13, facing),
                    BlockModels.bounds(x, y, z, 10, 2, 11, 12, 7, 13, facing)
                };
            int post = BlockModels.repeaterPostZ(state.delay);
            return new AABB[] {
                base,
                BlockModels.bounds(x, y, z, 7, 2, 2, 9, 7, 4, facing),
                BlockModels.bounds(x, y, z, 7, 2, post, 9, 7, post + 2, facing)
            };
        }
        return new AABB[] {super.getSelectionBox(x, y, z)};
    }

    @Override
    public void addCollisionBoxes(Level level, int x, int y, int z, AABB query, List<AABB> boxes) {
        if (!isPiston() && kind != Kind.HEAD) {
            super.addCollisionBoxes(level, x, y, z, query, boxes);
            return;
        }
        for (AABB box : modelBounds(level, x, y, z)) if (query.intersectsInner(box)) boxes.add(box);
    }

    @Override
    public AABB getSelectionBox(Level level, int x, int y, int z) {
        return modelBounds(level, x, y, z)[0];
    }

    @Override
    public MovingObjectPosition clip(Level level, int x, int y, int z, Vec3D start, Vec3D end) {
        MovingObjectPosition closest = null;
        for (AABB box : modelBounds(level, x, y, z)) {
            MovingObjectPosition hit = box.clip(start, end);
            if (hit != null
                    && (closest == null
                            || start.distanceSquared(hit.vec)
                                    < start.distanceSquared(closest.vec))) {
                closest = new MovingObjectPosition(x, y, z, hit.face, hit.vec);
            }
        }
        return closest;
    }

    @Override
    public void onPlacedBy(Level level, int x, int y, int z, Player player, int face) {
        RedstoneEngine.State state = level.redstone().state(x, y, z);
        state.facing = horizontalFacing(player);
        if (kind == Kind.LEVER) state.mount = face;
        else if (needsSupport() && !needsFloorSupport()) state.facing = face;
        if (isPiston()) {
            Vec3D look = GravityGunItem.look(player);
            if (Math.abs(look.y) > Math.max(Math.abs(look.x), Math.abs(look.z))) {
                state.facing = look.y < 0 ? 1 : 0;
            }
        }
        level.onBlockChanged(x, y, z);
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        if (level.isNetworkMode()) return false;
        RedstoneEngine.State state = level.redstone().state(x, y, z);
        switch (kind) {
            case LEVER -> state.on = !state.on;
            case BUTTON -> {
                state.on = true;
                state.release = level.tickCount + 20;
            }
            case REPEATER -> state.delay = state.delay % 4 + 1;
            case COMPARATOR -> state.on = !state.on;
            default -> {
                return false;
            }
        }
        level.onBlockChanged(x, y, z);
        level.playSound("random/click", x + .5f, y + .5f, z + .5f, .4f, state.on ? .6f : .5f);
        return true;
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        RedstoneEngine.State state = level.redstone().state(x, y, z);
        float b = Math.max(kind == Kind.LIT_LAMP ? 1 : .2f, level.getRenderBrightness(x, y + 1, z));
        if (kind == Kind.WIRE) {
            BlockModels.wire(
                    renderer,
                    x,
                    y,
                    z,
                    level.redstone().wireConnections(new Pos(x, y, z)),
                    state.power,
                    b);
        } else {
            draw(renderer, x, y, z, state, b);
        }
        return true;
    }

    @Override
    public void renderFullbright(ShapeRenderer renderer) {
        RedstoneEngine.State preview = new RedstoneEngine.State(id);
        preview.power = kind == Kind.TORCH ? 15 : 0;
        preview.facing = isTorch() || kind == Kind.BUTTON ? 1 : 2;
        draw(renderer, -2, 0, 0, preview, 1);
    }

    @Override
    public void renderPreview(ShapeRenderer renderer) {
        renderer.begin();
        RedstoneEngine.State preview = new RedstoneEngine.State(id);
        preview.power = kind == Kind.TORCH ? 15 : 0;
        preview.facing = isTorch() || kind == Kind.BUTTON ? 1 : 3;
        draw(renderer, 0, 0, 0, preview, 1);
        renderer.end();
    }

    private void box(
            ShapeRenderer r,
            int x,
            int y,
            int z,
            float a,
            float b,
            float c,
            float d,
            float e,
            float f,
            int tex,
            float light) {
        BlockShape.box(r, x, y, z, a, b, c, d, e, f, tex, tex, tex, light, light, light);
    }

    private void draw(ShapeRenderer r, int x, int y, int z, RedstoneEngine.State s, float light) {
        switch (kind) {
            case WIRE -> BlockModels.wire(r, x, y, z, 0, s.power, light);
            case TORCH, NORMAL_TORCH ->
                    BlockModels.torch(
                            r,
                            x,
                            y,
                            z,
                            s.facing,
                            kind == Kind.NORMAL_TORCH ? textureId : s.power > 0 ? 140 : 141,
                            light);
            case LEVER -> BlockModels.lever(r, x, y, z, s.mount, s.facing, s.on, light);
            case BUTTON -> BlockModels.button(r, x, y, z, s.facing, s.on, light);
            case PLATE ->
                    box(
                            r,
                            x,
                            y,
                            z,
                            .0625f,
                            0,
                            .0625f,
                            .9375f,
                            s.power > 0 ? .03125f : .0625f,
                            .9375f,
                            1,
                            light);
            case REPEATER, COMPARATOR -> {
                int tex =
                        kind == Kind.REPEATER
                                ? (s.power > 0 ? 144 : 143)
                                : (s.power > 0 ? 146 : 145);
                BlockModels.logicGate(
                        r,
                        x,
                        y,
                        z,
                        s.facing,
                        tex,
                        kind == Kind.COMPARATOR,
                        s.power > 0,
                        s.on,
                        s.delay,
                        light);
            }
            case PISTON, STICKY_PISTON -> {
                BlockModels.piston(
                        r, x, y, z, s.facing, s.extended, false, kind == Kind.STICKY_PISTON, light);
            }
            case HEAD -> {
                BlockModels.piston(r, x, y, z, s.facing, true, true, s.on, light);
            }
            default -> box(r, x, y, z, 0, 0, 0, 1, 1, 1, textureId, light);
        }
    }

    @Override
    public int getDrop() {
        if (kind == Kind.WIRE)
            return net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256;
        return kind == Kind.LIT_LAMP ? REDSTONE_LAMP.id : kind == Kind.HEAD ? PISTON.id : id;
    }
}
