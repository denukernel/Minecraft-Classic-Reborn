package net.classicremastered.minecraft;

public enum Difficulty {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD;

    public String getLabel() {
        switch (this) {
            case PEACEFUL: return "Peaceful";
            case EASY:     return "Easy";
            case NORMAL:   return "Normal";
            case HARD:     return "Hard";
        }
        return "Unknown";
    }

    public static Difficulty fromString(String s, Difficulty fallback) {
        if (s == null) return fallback;
        try {
            return Difficulty.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public Difficulty next() {
        switch (this) {
            case PEACEFUL: return EASY;
            case EASY:     return NORMAL;
            case NORMAL:   return HARD;
            case HARD:     return PEACEFUL;
        }
        return NORMAL;
    }
}
