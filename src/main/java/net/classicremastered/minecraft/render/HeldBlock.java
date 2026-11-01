package net.classicremastered.minecraft.render;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.tile.Block;

public class HeldBlock
{
	public HeldBlock(Minecraft minecraft)
	{
		this.minecraft = minecraft;
	}

	public Minecraft minecraft;

	public Block block = null;
    private int renderedId = Integer.MIN_VALUE;

    public int getRenderedId(int selected) {
        return renderedId == Integer.MIN_VALUE ? selected : renderedId;
    }

    public void resetEquip(int selected) {
        renderedId = selected;
        block = selected > 0 && selected < Block.blocks.length ? Block.blocks[selected] : null;
        pos = lastPos = 1;
    }

    /** Change the visual item only after it has lowered out of view. */
    public void tickEquip(int selected) {
        if (renderedId == Integer.MIN_VALUE) renderedId = selected;
        lastPos = pos;
        float target = selected == renderedId ? 1 : 0;
        pos += Math.max(-.25f, Math.min(.25f, target - pos));
        if (pos <= .05f && selected != renderedId) {
            renderedId = selected;
            block = selected > 0 && selected < Block.blocks.length ? Block.blocks[selected] : null;
        }
    }

	public float pos = 0.0F;
	public float lastPos = 0.0F;

	public int offset = 0;

	public boolean moving = false;
}
