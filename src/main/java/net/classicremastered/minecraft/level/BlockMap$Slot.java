package net.classicremastered.minecraft.level;

import java.io.Serializable;

import net.classicremastered.minecraft.Entity;

class BlockMap$Slot implements Serializable {

    public static final long serialVersionUID = 0L;
    private int xSlot;
    private int ySlot;
    private int zSlot;
    final BlockMap blockMap;

    private BlockMap$Slot(BlockMap m) {
        this.blockMap = m;
    }

    public BlockMap$Slot init(float x, float y, float z) {
        if (blockMap.infiniteMode) {
            // collapse all entities to one slot
            this.xSlot = this.ySlot = this.zSlot = 0;
            return this;
        }

        this.xSlot = (int) (x / 16.0F);
        this.ySlot = (int) (y / 16.0F);
        this.zSlot = (int) (z / 16.0F);

        if (this.xSlot < 0)
            this.xSlot = 0;
        if (this.ySlot < 0)
            this.ySlot = 0;
        if (this.zSlot < 0)
            this.zSlot = 0;

        if (this.xSlot >= BlockMap.getWidth(this.blockMap))
            this.xSlot = BlockMap.getWidth(this.blockMap) - 1;
        if (this.ySlot >= BlockMap.getDepth(this.blockMap))
            this.ySlot = BlockMap.getDepth(this.blockMap) - 1;
        if (this.zSlot >= BlockMap.getHeight(this.blockMap))
            this.zSlot = BlockMap.getHeight(this.blockMap) - 1;

        return this;
    }

    public void add(Entity e) {
        this.blockMap.entityGrid[(this.zSlot * BlockMap.getDepth(this.blockMap) + this.ySlot)
                * BlockMap.getWidth(this.blockMap) + this.xSlot].add(e);
    }

    public void remove(Entity e) {
        this.blockMap.entityGrid[(this.zSlot * BlockMap.getDepth(this.blockMap) + this.ySlot)
                * BlockMap.getWidth(this.blockMap) + this.xSlot].remove(e);
    }

    // synthetic helpers
    static int getXSlot(BlockMap$Slot s) {
        return s.xSlot;
    }

    static int getYSlot(BlockMap$Slot s) {
        return s.ySlot;
    }

    static int getZSlot(BlockMap$Slot s) {
        return s.zSlot;
    }

    BlockMap$Slot(BlockMap m, SyntheticClass dummy) {
        this(m);
    }
}
