package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.mob.ai.BasicAI;

// PlayerAI
public class PlayerInputAI extends BasicAI {
    static final float SPRINT_MULTIPLIER = 1.3f;

    public PlayerInputAI(Player player) {
        this.player = player;
    }

    @Override
    protected void update() {
        runMultiplier = SPRINT_MULTIPLIER;
        boolean autoJump = player.consumeAutoJump();
        this.jumping = player.input.jumping || autoJump;

        this.xxa = player.movementStrafe();
        this.yya = player.input.yya;

        this.running =
                player.input.running
                        && this.yya > 0
                        && !player.isSneaking()
                        && !player.isHoldingLadder();
    }

    public static final long serialVersionUID = 0L;

    private Player player;
}
