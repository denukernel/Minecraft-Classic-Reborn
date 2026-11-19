package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;

/** Two wooden panels with facing and swing state stored in their block ids. */
public final class BuilderDoorBlock extends Block {
    private final boolean upper, open;
    private final int facing;

    BuilderDoorBlock(int id, boolean upper, boolean open, int facing) {
        super(id, upper ? 59 : 58, "Oak Door");
        this.upper = upper;
        this.open = open;
        this.facing = facing;
        setData(Tile$SoundType.wood, 1, 1, 1.5f);
        float thickness = 3f / 16;
        switch (Math.floorMod(facing - (open ? 1 : 0), 4)) {
            case 0 -> setBounds(0, 0, 0, 1, 1, thickness);
            case 1 -> setBounds(1 - thickness, 0, 0, 1, 1, 1);
            case 2 -> setBounds(0, 0, 1 - thickness, 1, 1, 1);
            case 3 -> setBounds(0, 0, 0, thickness, 1, 1);
            default -> throw new IllegalArgumentException("Invalid door facing");
        }
    }

    static void registerOtherFacings() {
        for (int facing = 1; facing < 4; facing++)
            for (int state = 0; state < 4; state++)
                new BuilderDoorBlock(stateId(facing, (state & 1) != 0, (state & 2) != 0),
                        (state & 1) != 0, (state & 2) != 0, facing);
    }

    private static int stateId(int facing, boolean upper, boolean open) {
        int base = facing == 0 ? 231 : 236 + (facing - 1) * 4;
        return base + (upper ? 1 : 0) + (open ? 2 : 0);
    }

    public static boolean isClosed(Block block) {
        return block instanceof BuilderDoorBlock door && !door.open;
    }

    public static boolean isInstalled(Level level, Pos bottom) {
        Block low = Block.blocks[level.getTile(bottom.x(), bottom.y(), bottom.z())];
        Block high = Block.blocks[level.getTile(bottom.x(), bottom.y() + 1, bottom.z())];
        return low instanceof BuilderDoorBlock lower && !lower.upper && high instanceof BuilderDoorBlock top
                && top.upper && lower.open == top.open && lower.facing == top.facing;
    }

    public static boolean install(Level level, Pos bottom) {
        return install(level, bottom, 0);
    }

    public static boolean install(Level level, Pos bottom, Player player) {
        int facing = switch (CircuitBlock.horizontalFacing(player)) {
            case 4 -> 1;
            case 2 -> 2;
            case 5 -> 3;
            default -> 0;
        };
        return install(level, bottom, facing);
    }

    private static boolean install(Level level, Pos bottom, int facing) {
        if (level.isNetworkMode() || level.blockMap == null
                || !level.isInBounds(bottom.x(), bottom.y() + 1, bottom.z())
                || !level.isSolidTile(bottom.x(), bottom.y() - 1, bottom.z())
                || level.getTile(bottom.x(), bottom.y(), bottom.z()) != 0
                || level.getTile(bottom.x(), bottom.y() + 1, bottom.z()) != 0)
            return false;
        AABB space = new AABB(bottom.x(), bottom.y(), bottom.z(), bottom.x() + 1, bottom.y() + 2,
                bottom.z() + 1);
        return panelClear(level, space) && writePair(level, bottom, facing, false);
    }

    private static boolean writePair(Level level, Pos bottom, int facing, boolean opened) {
        int x = bottom.x(), y = bottom.y(), z = bottom.z();
        int previous = level.getTile(x, y, z);
        if (!level.setTileNoNeighborChange(x, y, z, stateId(facing, false, opened)))
            return false;
        if (level.setTileNoNeighborChange(x, y + 1, z, stateId(facing, true, opened)))
            return true;
        level.setTileNoNeighborChange(x, y, z, previous);
        return false;
    }

    private boolean setOpen(Level level, int x, int y, int z, boolean opened) {
        Pos bottom = new Pos(x, upper ? y - 1 : y, z);
        if (level.isNetworkMode() || open == opened || !isInstalled(level, bottom))
            return false;
        Block next = Block.blocks[stateId(facing, false, opened)];
        AABB panel = next.getCollisionBox(x, bottom.y(), z).expand(0, 1, 0);
        if (!panelClear(level, panel) || !writePair(level, bottom, facing, opened))
            return false;
        level.playSound(
                opened ? "random/door_open" : "random/door_close",
                x + .5f,
                bottom.y() + 1,
                z + .5f,
                1,
                1);
        return true;
    }

    private static boolean panelClear(Level level, AABB panel) {
        for (Object entity : level.findEntities(null, panel)) {
            if (entity instanceof Mob mob && !mob.removed && mob.health > 0 && mob.bb.intersectsInner(panel))
                return false;
        }
        return true;
    }

    public static void openFor(Mob mob, float dx, float dz) {
        Level level = mob.level;
        if (level == null || level.blockMap == null || level.isNetworkMode())
            return;
        AABB sweep = mob.bb.expand(dx, 0, dz);
        for (int x = (int) Math.floor(sweep.x0); x <= Math.floor(sweep.x1); x++) {
            for (int z = (int) Math.floor(sweep.z0); z <= Math.floor(sweep.z1); z++) {
                for (int y = (int) Math.floor(sweep.y0); y <= Math.floor(sweep.y1); y++) {
                    Block block = Block.blocks[level.getTile(x, y, z)];
                    if (block instanceof BuilderDoorBlock door && !door.open
                            && sweep.intersectsInner(door.getCollisionBox(x, y, z))) {
                        door.setOpen(level, x, y, z, true);
                    }
                }
            }
        }
    }

    public static void close(Level level, Pos bottom) {
        Block block = Block.blocks[level.getTile(bottom.x(), bottom.y(), bottom.z())];
        if (block instanceof BuilderDoorBlock door && !door.upper)
            door.setOpen(level, bottom.x(), bottom.y(), bottom.z(), false);
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        if (player == null || player.isSpectator() || player.health <= 0 || level.isNetworkMode())
            return false;
        setOpen(level, x, y, z, !open);
        return true;
    }

    @Override
    public void onNeighborChange(Level level, int x, int y, int z, int changedId) {
        int baseY = upper ? y - 1 : y;
        if (!level.isSolidTile(x, baseY - 1, z) || !isInstalled(level, new Pos(x, baseY, z))) {
            level.setTile(x, y, z, 0);
        }
    }

    @Override
    public boolean render(Level level, int x, int y, int z, ShapeRenderer renderer) {
        float light = level.getRenderBrightness(x, y, z);
        renderer.color(light, light, light);
        boolean alongZ = (facing & 1) == (open ? 0 : 1);
        for (int side = 0; side < 6; side++) {
            boolean panelFace = alongZ ? side >= 4 : side == 2 || side == 3;
            renderSide(renderer, x, y, z, side, panelFace ? textureId : WOOD.textureId);
        }
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public int getDrop() {
        return Item.OAK_DOOR.id + 256;
    }
}
