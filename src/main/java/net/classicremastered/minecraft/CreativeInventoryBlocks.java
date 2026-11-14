package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.tile.Block;

import java.util.ArrayList;
import java.util.List;

public final class CreativeInventoryBlocks {

    public static List allowedBlocks;
    public String username;
    public String sessionId;
    public String mppass;
    public boolean haspaid;

    public CreativeInventoryBlocks(String var1, String var2) {
        this.username = var1;
        this.sessionId = var2;
    }

    static {
        (allowedBlocks = new ArrayList()).add(Block.STONE);
        allowedBlocks.add(Block.COBBLESTONE);
        allowedBlocks.add(Block.BRICK);
        allowedBlocks.add(Block.DIRT);
        allowedBlocks.add(Block.WOOD);
        allowedBlocks.add(Block.LOG);
        allowedBlocks.add(Block.LEAVES);
        allowedBlocks.add(Block.SNOWY_GRASS);
        allowedBlocks.add(Block.SNOWY_LEAVES);
        allowedBlocks.add(Block.SNOW_LAYER);
        allowedBlocks.add(Block.SNOW_BLOCK);
        allowedBlocks.add(Block.SHORT_GRASS);
        allowedBlocks.add(Block.LADDER);
        allowedBlocks.add(Block.GLASS);
        allowedBlocks.add(Block.SLAB);
        allowedBlocks.add(Block.MOSSY_COBBLESTONE);
        allowedBlocks.add(Block.SAPLING);
        allowedBlocks.add(Block.DANDELION);
        allowedBlocks.add(Block.ROSE);
        allowedBlocks.add(Block.BROWN_MUSHROOM);
        allowedBlocks.add(Block.RED_MUSHROOM);
        allowedBlocks.add(Block.SAND);
        allowedBlocks.add(Block.GRAVEL);
        allowedBlocks.add(Block.SPONGE);
        allowedBlocks.add(Block.RED_WOOL);
        allowedBlocks.add(Block.ORANGE_WOOL);
        allowedBlocks.add(Block.YELLOW_WOOL);
        allowedBlocks.add(Block.LIME_WOOL);
        allowedBlocks.add(Block.GREEN_WOOL);
        allowedBlocks.add(Block.AQUA_GREEN_WOOL);
        allowedBlocks.add(Block.CYAN_WOOL);
        allowedBlocks.add(Block.BLUE_WOOL);
        allowedBlocks.add(Block.PURPLE_WOOL);
        allowedBlocks.add(Block.INDIGO_WOOL);
        allowedBlocks.add(Block.VIOLET_WOOL);
        allowedBlocks.add(Block.MAGENTA_WOOL);
        allowedBlocks.add(Block.PINK_WOOL);
        allowedBlocks.add(Block.BLACK_WOOL);
        allowedBlocks.add(Block.GRAY_WOOL);
        allowedBlocks.add(Block.WHITE_WOOL);
        allowedBlocks.add(Block.COAL_ORE);
        allowedBlocks.add(Block.IRON_ORE);
        allowedBlocks.add(Block.GOLD_ORE);
        allowedBlocks.add(Block.IRON_BLOCK);
        allowedBlocks.add(Block.GOLD_BLOCK);
        allowedBlocks.add(Block.BOOKSHELF);
        allowedBlocks.add(Block.TNT);
        allowedBlocks.add(Block.OBSIDIAN);
        allowedBlocks.add(Block.MAGMA);
        allowedBlocks.add(Block.BEDROCK);

        // --- New custom blocks in creative inventory ---
        allowedBlocks.add(Block.DIAMOND_ORE_NEW);
        allowedBlocks.add(Block.DIAMOND_BLOCK_NEW);
        allowedBlocks.add(Block.BEACON);
        allowedBlocks.add(Block.FURNACE);
        allowedBlocks.add(Block.TORCH);
        allowedBlocks.add(Block.PUMPKIN);
        allowedBlocks.add(Block.CACTUS);
        allowedBlocks.add(Block.PISTON);
        allowedBlocks.add(Block.LUCKY_BLOCK);
        allowedBlocks.add(Block.GRASS);
        allowedBlocks.add(Block.WORKBENCH);
        allowedBlocks.add(Block.JUKEBOX);
        allowedBlocks.add(Block.STRUCTURE_ALPHA);
        allowedBlocks.add(Block.STRUCTURE_BETA);
        allowedBlocks.add(Block.STRUCTURE_GAMMA);
        allowedBlocks.add(Block.STRUCTURE_DELTA);
        allowedBlocks.add(Block.OAK_STAIRS);
        allowedBlocks.add(Block.COBBLESTONE_STAIRS);
        allowedBlocks.add(Block.STONE_BRICKS);
        allowedBlocks.add(Block.STONE_BRICK_STAIRS);
        for (int id = 64; id <= 89; id++)
            if (Block.blocks[id] != null && id != 73 && id != 75)
                allowedBlocks.add(Block.blocks[id]);
    }
}
