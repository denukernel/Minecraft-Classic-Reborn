package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.player.PlayerStats;
import net.classicremastered.minecraft.mob.MobRegistry;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Map;
import java.util.List;

public final class StatsScreen extends GuiScreen {
    private final GuiScreen parent;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance();
    private PlayerStats global;
    private boolean showGlobal;
    private boolean showMobs;
    private int page;
    private Button previous, next, view;
    private List<Map.Entry<String, Long>> mobRows = List.of();

    public StatsScreen(GuiScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        buttons.clear();
        int center = width / 2;
        buttons.add(new Button(0, center - 100, 62, 98, 20, "This world"));
        buttons.add(new Button(1, center + 2, 62, 98, 20, "Global"));
        buttons.add(view = new Button(3, center - 100, height - 56, "Kills by mob"));
        buttons.add(new Button(2, center - 52, height - 32, 104, 20, "Done"));
        buttons.add(previous = new Button(4, center - 100, height - 32, 44, 20, "<"));
        buttons.add(next = new Button(5, center + 56, height - 32, 44, 20, ">"));
        MobRegistry.bootstrapDefaults();
        select(showGlobal);
    }

    @Override
    protected void onButtonClick(Button button) {
        switch (button.id) {
            case 0, 1 -> select(button.id == 1);
            case 2 -> minecraft.setCurrentScreen(parent);
            case 3 -> {
                showMobs = !showMobs;
                page = 0;
                updateControls();
            }
            case 4, 5 -> {
                page += button.id == 4 ? -1 : 1;
                updateControls();
            }
        }
    }

    private void select(boolean globalView) {
        showGlobal = globalView;
        page = 0;
        ((Button) buttons.get(0)).active = globalView;
        ((Button) buttons.get(1)).active = !globalView;
        if (globalView) {
            try {
                global = PlayerStats.loadGlobal();
            } catch (IOException | SecurityException error) {
                global = null;
            }
        }
        PlayerStats stats = selectedStats();
        mobRows =
                stats == null
                        ? List.of()
                        : stats.mobKills().entrySet().stream()
                                .sorted(
                                        Map.Entry.<String, Long>comparingByValue(
                                                        Comparator.reverseOrder())
                                                .thenComparing(
                                                        entry ->
                                                                PlayerStats.mobName(entry.getKey()))
                                                .thenComparing(Map.Entry::getKey))
                                .toList();
        updateControls();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 160) / 15);
    }

    private void updateControls() {
        view.text = showMobs ? "Totals" : "Kills by mob";
        previous.visible = next.visible = showMobs;
        previous.active = page > 0;
        next.active = (page + 1) * rowsPerPage() < mobRows.size();
    }

    private PlayerStats selectedStats() {
        return showGlobal
                ? global
                : minecraft.player != null
                                && minecraft.level != null
                                && !minecraft.level.isNetworkMode()
                        ? minecraft.player.worldStats()
                        : null;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        drawFadingBox(0, 0, width, height, 0x60000000, 0xA0000000);
        drawCenteredString(fontRenderer, "Statistics", width / 2, 40, 0xFFFFFF);
        PlayerStats stats = selectedStats();
        if (stats == null) {
            drawCenteredString(
                    fontRenderer,
                    showGlobal
                            ? "Global statistics could not be read"
                            : "World statistics unavailable",
                    width / 2,
                    114,
                    0xFFFFFF);
        } else if (showMobs) {
            if (mobRows.isEmpty()) {
                drawCenteredString(
                        fontRenderer, "No kills recorded by mob yet", width / 2, 105, 0xFFFFFF);
            }
            int start = page * rowsPerPage();
            int end = Math.min(mobRows.size(), start + rowsPerPage());
            for (int i = start; i < end; i++) {
                var row = mobRows.get(i);
                drawStat(PlayerStats.mobName(row.getKey()), row.getValue(), 94 + (i - start) * 15);
            }
        } else {
            drawStat("Mobs killed", stats.kills(), 94);
            drawStat("Deaths", stats.deaths(), 110);
            drawStat("MineCoins earned", stats.coins(), 126);
            drawStat("Score earned", stats.score(), 142);
        }
        super.render(mouseX, mouseY);
    }

    private void drawStat(String label, long value, int y) {
        String total = numbers.format(value);
        fontRenderer.render(label, width / 2 - 100, y, 0xFFFFFF);
        fontRenderer.render(total, width / 2 + 100 - fontRenderer.getWidth(total), y, 0xFFFFFF);
    }
}
