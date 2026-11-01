package net.classicremastered.minecraft.level.generator.noise;


public abstract class Noise
{
	public abstract double compute(double x, double z);

	public double compute(double x, double y, double z)
	{
		return compute(x, z);
	}
}
