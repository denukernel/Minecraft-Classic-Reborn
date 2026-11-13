package net.classicremastered.minecraft;

public class ChatLine {
    public record Action(String label, Runnable run) {}

    public final java.util.List<Action> actions;

    public ChatLine(String message) {
        this(message, new Action[0]);
    }

    public ChatLine(String message, Action... actions) {
        this.message = message;
        this.time = 0;
        this.actions = java.util.List.of(actions);
    }

    public String message;
    public int time;
}
