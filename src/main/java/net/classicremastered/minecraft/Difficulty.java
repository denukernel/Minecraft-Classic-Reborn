package net.classicremastered.minecraft;

public enum Difficulty {
    PEACEFUL("Peaceful"),
    EASY("Easy"),
    NORMAL("Normal"),
    HARD("Hard"),
    VERY_HARD("Very Hard"),
    EXTREME("Extreme");

    private static final Difficulty[] CYCLE = values();

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Difficulty fromString(String s, Difficulty fallback) {
        if (s == null) return fallback;
        try {
            return Difficulty.valueOf(
                    s.trim()
                            .toUpperCase(java.util.Locale.ROOT)
                            .replace(' ', '_')
                            .replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public Difficulty next() {
        return CYCLE[(ordinal() + 1) % CYCLE.length];
    }
}
