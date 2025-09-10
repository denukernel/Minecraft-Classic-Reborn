package net.classicremastered.minecraft.level.tile;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.player.Player;

public class StructureBlock extends Block {

    public enum Type {
        ALPHA, BETA, GAMMA, DELTA
    }

    private final Type type;

    public StructureBlock(int id, int textureId, String name, Type type) {
        super(id, textureId, name);
        this.explodes = false;
        this.setData(Tile$SoundType.stone, 1.0F, 1.0F, 999.0F);
        this.type = type;
    }

    @Override
    public boolean onRightClick(Level level, int x, int y, int z, Player player, int face) {
        switch (type) {
            case ALPHA: // teleport player to sky
                player.y += 50;
                if (player.y > level.depth - 1) {
                    player.y = level.depth - 1;
                }
                break;

            case BETA: // set world to night & spawn zombies
                level.setTime(18000);
                for (int i = 0; i < 5; i++) {
                    Zombie zombie = new Zombie(level,
                        x + (level.random.nextInt(5) - 2),
                        y,
                        z + (level.random.nextInt(5) - 2));
                    level.addEntity(zombie);
                }
                break;

            case GAMMA: // fire at player's feet
                int px = (int) Math.floor(player.x);
                int py = (int) Math.floor(player.y - 1);
                int pz = (int) Math.floor(player.z);
                level.setTile(px, py, pz, FIRE.id);
                break;

            case DELTA: // apply cursed effect for 5 seconds
                player.deltaCurseTicks = 100; // 20 ticks/sec * 5 sec
                player.deltaCurseCounter = 0;
                break;
        }
        return true;
    }

    @Override
    public int getDrop() {
        return 0; // no drops
    }

    @Override
    public boolean prefersPickaxe() { return false; }
    @Override
    public boolean prefersShovel() { return false; }
    @Override
    public boolean prefersAxe() { return false; }
}
