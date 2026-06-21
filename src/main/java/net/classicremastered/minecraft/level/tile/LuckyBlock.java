package net.classicremastered.minecraft.level.tile;

import java.util.Random;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.PrimedTnt;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Creeper;
import net.classicremastered.minecraft.mob.Pig;
import net.classicremastered.minecraft.mob.Sheep;
import net.classicremastered.minecraft.mob.Chicken;
import net.classicremastered.minecraft.mob.IronGolem;

public final class LuckyBlock extends Block {
    public LuckyBlock(int id) {
        super(id, 190, "Lucky Block");
        this.setData(Tile$SoundType.wood, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void onBreak(Level level, int x, int y, int z) {
    }

    @Override
    public void dropItems(Level level, int x, int y, int z, float chance) {
    }

    @Override
    public void onRemoved(Level level, int x, int y, int z) {
        super.onRemoved(level, x, y, z);
        
        // Only run triggers on local singleplayer / server side
        if (level.isNetworkMode()) {
            return;
        }

        Random rand = new Random();
        int outcome = rand.nextInt(6);
        float px = x + 0.5F;
        float py = y + 0.5F;
        float pz = z + 0.5F;

        switch (outcome) {
            case 0: // Diamonds Jackpot
                level.playSound("random/anvil_use", px, py, pz, 1.0F, 1.0F);
                int numDrops = rand.nextInt(2) + 2; // 2 or 3
                for (int i = 0; i < numDrops; i++) {
                    int blockId = rand.nextBoolean() ? Block.DIAMOND_BLOCK_NEW.id : Block.DIAMOND_ORE_NEW.id;
                    level.addEntity(new DroppedBlock(level, px + (rand.nextFloat() - 0.5F) * 0.5F, py, pz + (rand.nextFloat() - 0.5F) * 0.5F, blockId));
                }
                break;
            case 1: // Creeper trap
                level.playSound("random/fuse", px, py, pz, 1.0F, 1.0F);
                spawnMob(level, new Creeper(level, px, py, pz), px, y, pz);
                break;
            case 2: // Friendly animals
                level.playSound("mob/sheep/say", px, py, pz, 1.0F, 1.0F);
                spawnMob(level, new Pig(level, px, py, pz), px, y, pz);
                spawnMob(level, new Sheep(level, px, py, pz), px, y, pz);
                spawnMob(level, new Chicken(level, px, py, pz), px, y, pz);
                break;
            case 3: // Lit TNT Trap
                level.playSound("random/fuse", px, py, pz, 1.0F, 1.0F);
                level.addEntity(new PrimedTnt(level, px, py, pz));
                break;
            case 4: // Rainbow wool shower
                level.playSound("random/pop", px, py, pz, 1.0F, 1.0F);
                int[] woolIds = {
                    Block.RED_WOOL.id, Block.ORANGE_WOOL.id, Block.YELLOW_WOOL.id,
                    Block.GREEN_WOOL.id, Block.BLUE_WOOL.id, Block.PURPLE_WOOL.id, Block.SPONGE.id
                };
                for (int wId : woolIds) {
                    level.addEntity(new DroppedBlock(level, px + (rand.nextFloat() - 0.5F) * 0.8F, py + rand.nextFloat() * 0.5F, pz + (rand.nextFloat() - 0.5F) * 0.8F, wId));
                }
                break;
            case 5: // Iron Golem guard
                level.playSound("random/anvil_use", px, py, pz, 1.0F, 1.0F);
                IronGolem golem = new IronGolem(level, px, py, pz);
                golem.builtByPlayer = true;
                spawnMob(level, golem, px, y, pz);
                break;
        }
    }

    private void spawnMob(Level level, Mob mob, float x, float y, float z) {
        float hh = mob.bbHeight * 0.5F;
        mob.setPos(x, y + hh + 0.05F, z);
        level.addEntity(mob);
    }
}
