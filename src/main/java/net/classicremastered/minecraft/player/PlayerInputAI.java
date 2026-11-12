package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.mob.ai.BasicAI;

// PlayerAI
public class PlayerInputAI extends BasicAI {

    public PlayerInputAI(Player player) {
        this.player = player;
    }

    @Override
    protected void update() {
        boolean autoJump = player.consumeAutoJump();
        this.jumping = player.input.jumping || autoJump;

        this.xxa = player.movementStrafe();
        this.yya = player.input.yya;

        this.running = player.input.running;
    }

    public static final long serialVersionUID = 0L;

    private Player player;
}
