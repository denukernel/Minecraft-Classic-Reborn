package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.RedstoneEngine.Pos;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ToolItem;

public final class Miner extends Worker {
    private static final long serialVersionUID = 1L;
    public static final short REGISTRY_ID = 94;
    private transient MiningCrack crack;

    public record MiningCrack(Pos block, int blockId, float progress) {
    }

    public Miner(Level level, float x, float y, float z) {
        super(level, x, y, z, "Miner");
        equipment[4] = Equipment.PICKAXES[1] + 256;
        ai = new MinerAI();
    }

    ToolItem workTool() {
        ToolItem equipped = tool(equipment[4]);
        if (equipped != null && equipped.kind() == ToolItem.Kind.AXE)
            return equipped;
        for (int i = 0; i < pack().totalSize(); i++) {
            ToolItem tool = tool(pack().getId(i));
            if (pack().getCount(i) > 0 && tool != null && tool.kind() == ToolItem.Kind.AXE)
                return tool;
        }
        return equipped != null && equipped.kind() == ToolItem.Kind.PICKAXE ? equipped : null;
    }

    public int displayedTool() {
        ToolItem tool = workTool();
        return tool != null ? tool.id + 256 : equipment[4];
    }

    public MiningCrack miningCrack() {
        return crack;
    }

    void updateCrack(Pos block, int blockId, float progress) {
        crack = new MiningCrack(block, blockId, progress);
    }

    @Override
    public void tick() {
        crack = null;
        super.tick();
    }

    private static ToolItem tool(int raw) {
        int id = raw - 256;
        return id >= 0 && id < Item.items.length && Item.items[id] instanceof ToolItem tool ? tool : null;
    }
}
