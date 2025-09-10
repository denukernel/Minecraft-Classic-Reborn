package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.mob.ai.BasicAI;

// PlayerAI
public class Player$1 extends BasicAI
{
	public Player$1(Player player)
	{
		this.player = player;
	}

	@Override
	protected void update() {
	    this.jumping = player.input.jumping;

	    // If A/D still feel flipped, use: this.xxa = -player.input.xxa;
	    this.xxa = player.input.xxa;      // strafe stays as-is
	    this.yya = player.input.yya;     // flip forward ONCE (W -> positive forward)

	    this.running = player.input.running;
	}

	public static final long serialVersionUID = 0L;

	private Player player;
}
