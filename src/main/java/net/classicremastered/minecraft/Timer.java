package net.classicremastered.minecraft;

public class Timer
{
	public Timer(float tps)
	{
		this.tps = tps;
		lastSysClock = System.currentTimeMillis();
		lastHRClock = System.nanoTime() / 1000000L;
	}

	public float tps;
	public double lastHR;
	public int elapsedTicks;
	public float delta;
	public float speed = 1.0F;
	public float elapsedDelta = 0.0F;
	public long lastSysClock;
	public long lastHRClock;
	public double adjustment = 1.0D;
}
