package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

/** Base class for non-stackable tools (maxStackSize=1). */
public class ToolItem extends Item {
    public enum Kind {
        NONE,
        PICKAXE,
        AXE,
        SHOVEL
    }

    public enum Material {
        WOOD(2),
        STONE(4),
        IRON(6),
        DIAMOND(8),
        GOLDEN(12);

        public final float speed;

        Material(float speed) {
            this.speed = speed;
        }
    }

    private Kind kind = Kind.NONE;
    private Material material = Material.IRON;

    public ToolItem(int id, String name, String texture) {
        super(id, name, texture, 1);
    }

    public ToolItem(int id, String name, String texture, Kind kind, Material material) {
        this(id, name, texture);
        mining(kind, material);
    }

    public final ToolItem mining(Kind kind, Material material) {
        this.kind = kind;
        this.material = material;
        return this;
    }

    public final Kind kind() {
        return kind;
    }

    public final float attackDamage() {
        if (kind != Kind.PICKAXE && kind != Kind.SHOVEL) return 0;
        int bonus =
                switch (material) {
                    case WOOD, GOLDEN -> 0;
                    case STONE -> 1;
                    case IRON -> 2;
                    case DIAMOND -> 3;
                };
        return bonus + (kind == Kind.PICKAXE ? 2f : 2.5f);
    }

    public final float miningSpeed(net.classicremastered.minecraft.level.tile.Block block) {
        boolean effective =
                switch (kind) {
                    case PICKAXE -> block.prefersPickaxe();
                    case AXE -> block.prefersAxe();
                    case SHOVEL -> block.prefersShovel();
                    default -> false;
                };
        return effective ? material.speed : 1;
    }

    @Override
    public void use(Player player, Level level) {
        // Tools don't do a right-click action by default.
        // Attack/mining behavior is handled in click/mining code.
    }
}
