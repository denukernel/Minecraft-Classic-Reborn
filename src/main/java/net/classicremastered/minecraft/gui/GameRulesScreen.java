package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.chat.commands.GameruleCommand;
import net.classicremastered.minecraft.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class GameRulesScreen extends GuiScreen {
    private static final String[] LABELS = {
        "Daylight Cycle", "Mob Spawning", "Mob Loot", "Keep Inventory", "Defenders Follow Player"
    };
    private final GuiScreen parent;
    private final Map<String, Boolean> rules;
    private final Consumer<Map<String, Boolean>> done;

    public GameRulesScreen(
            GuiScreen parent, Map<String, Boolean> rules, Consumer<Map<String, Boolean>> done) {
        super(parent);
        this.parent = parent;
        this.rules = new LinkedHashMap<>(rules);
        this.done = done;
    }

    public static Map<String, Boolean> values(Level level) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String rule : GameruleCommand.RULES)
            values.put(
                    rule,
                    level == null
                            ? !rule.equals("keepInventory")
                            : GameruleCommand.value(level, rule, null));
        return values;
    }

    static void apply(Level level, Map<String, Boolean> rules) {
        rules.forEach((rule, value) -> GameruleCommand.value(level, rule, value));
    }

    @Override
    public void onOpen() {
        buttons.clear();
        int top = rulesTop();
        for (int i = 0; i < GameruleCommand.RULES.size(); i++)
            buttons.add(new Button(i, width / 2 - 100, top + 24 * i, label(i)));
        int bottom = top + 24 * GameruleCommand.RULES.size() + 6;
        buttons.add(new Button(10, width / 2 - 100, bottom, 98, 20, "Done"));
        buttons.add(new Button(11, width / 2 + 2, bottom, 98, 20, "Cancel"));
    }

    private int rulesTop() {
        return Math.max(30, (height - 24 * GameruleCommand.RULES.size() - 20) / 2);
    }

    private String label(int i) {
        return LABELS[i] + ": " + (rules.get(GameruleCommand.RULES.get(i)) ? "ON" : "OFF");
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 10) {
            done.accept(Map.copyOf(rules));
            minecraft.setCurrentScreen(parent);
        } else if (button.id == 11) {
            minecraft.setCurrentScreen(parent);
        } else {
            String rule = GameruleCommand.RULES.get(button.id);
            rules.put(rule, !rules.get(rule));
            button.text = label(button.id);
        }
    }

    @Override
    public void render(int mouseX, int mouseY) {
        if (minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, width, height, 0x60000000, 0xA0000000);
        drawCenteredString(fontRenderer, "Game Rules", width / 2, rulesTop() - 20, 0xFFFFFF);
        super.render(mouseX, mouseY);
    }
}
