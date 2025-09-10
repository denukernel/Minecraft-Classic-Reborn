package net.classicremastered.minecraft.sound;

import net.classicremastered.minecraft.Entity;

public class EntitySoundPos extends BaseSoundPos
{
	public EntitySoundPos(Entity source, Entity listener)
	{
		super(listener);

		this.source = source;
	}

	@Override
	public float getRotationDiff()
	{
		return super.getRotationDiff(source.x, source.z);
	}

	@Override
	public float getDistanceSq()
	{
		return super.getDistanceSq(source.x, source.y, source.z);
	}

	private Entity source;
}
