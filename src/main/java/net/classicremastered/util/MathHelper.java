package net.classicremastered.util;

import java.util.Random;

public final class MathHelper {

    private static final float[] SIN_TABLE = new float[65536];

    /** Shared random instance for all game math use */
    public static final Random random = new Random();

    public static float sin(float v) {
        return SIN_TABLE[(int)(v * 10430.378F) & 0xFFFF];
    }

    public static float cos(float v) {
        return SIN_TABLE[(int)(v * 10430.378F + 16384.0F) & 0xFFFF];
    }

    public static float sqrt(float v) {
        return (float)Math.sqrt(v);
    }

    public static int floor(float f) {
        int i = (int) f;
        return f < i ? i - 1 : i;
    }

    /** Clamp a float value to [min,max]. */
    public static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Clamp an int value to [min,max]. */
    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Absolute value (float). */
    public static float abs(float v) {
        return v < 0 ? -v : v;
    }

    /** Linear interpolation. */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Wrap angle to [-180, 180). */
    public static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    /** Random float in [0,1). */
    public static float randFloat() {
        return random.nextFloat();
    }

    /** Random int in [min,max]. */
    public static int randRange(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** Random float in [min,max). */
    public static float randRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN_TABLE[i] = (float)Math.sin(i * Math.PI * 2.0D / 65536.0D);
        }
    }
}
