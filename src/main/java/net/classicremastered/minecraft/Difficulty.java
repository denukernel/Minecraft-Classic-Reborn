package net.classicremastered.minecraft;

public enum Difficulty {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD, VERY_HARD, EXTREME;

    public String getLabel() {
        switch (this) {
            case PEACEFUL: return "Peaceful";
            case EASY:     return "Easy";
            case NORMAL:   return "Normal";
            case HARD:     return "Hard";
            case VERY_HARD:
                return "Very Hard";
            case EXTREME:
                return "Extreme";
            }
        return "Unknown";
    }

    public static Difficulty fromString(String s, Difficulty fallback) {
        if (s == null) return fallback;
        try {
            return Difficulty.valueOf(
                    s.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public Difficulty next() {
        switch (this) {
            case PEACEFUL: return EASY;
            case EASY:     return NORMAL;
            case NORMAL:   return HARD;
            case HARD:
                return VERY_HARD;
            case VERY_HARD:
                return EXTREME;
            case EXTREME:
                return PEACEFUL;
            }
        return NORMAL;
    }
}
