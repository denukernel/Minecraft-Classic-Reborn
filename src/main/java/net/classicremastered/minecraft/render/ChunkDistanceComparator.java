package net.classicremastered.minecraft.render;

import java.util.Comparator;

import net.classicremastered.minecraft.player.Player;

public class ChunkDistanceComparator implements Comparator
{
	public ChunkDistanceComparator(Player player)
	{
		this.player = player;
	}

	@Override
	public int compare(Object o1, Object o2)
	{
		Chunk chunk = (Chunk)o1;
		Chunk other = (Chunk)o2;

		float sqDist = chunk.distanceSquared(player);
		float otherSqDist = other.distanceSquared(player);

		if(sqDist == otherSqDist)
		{
			return 0;
		} else if(sqDist > otherSqDist) {
			return -1;
		} else {
			return 1;
		}
	}

	private Player player;
}
