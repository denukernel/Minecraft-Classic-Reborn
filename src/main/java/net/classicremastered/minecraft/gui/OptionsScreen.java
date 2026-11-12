package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.GameSettings;

public final class OptionsScreen extends GuiScreen {
    private static final int SCREENSHOT_TYPE = 7;
    private final GuiScreen parent;
    private final GameSettings settings;

    public OptionsScreen(GuiScreen parent, GameSettings settings) {
        super(parent);
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    public void onOpen() {
        buttons.clear();
        String[] labels = {
            "Video Settings...", "Sound...", "Controls...", "Gameplay...", "Language..."
        };
        for (int i = 0; i < labels.length; i++) addPage(i, labels[i]);
        if (minecraft.player != null
                && minecraft.player.developerPowers().active(minecraft.player)) {
            addPage(5, "Developer Options...");
        }
        buttons.add(new Button(SCREENSHOT_TYPE, width / 2 - 100, 117, screenshotTypeLabel()));
        buttons.add(new Button(6, width / 2 - 100, height - 32, "Done"));
    }

    private void addPage(int id, String label) {
        int column = Math.min(150, (width - 24) / 2);
        int x = id % 2 == 0 ? width / 2 - column - 4 : width / 2 + 4;
        Button button = new Button(id, x, 45 + id / 2 * 24, label);
        button.width = column;
        buttons.add(button);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == SCREENSHOT_TYPE) {
            settings.toggleScreenshotType();
            button.text = screenshotTypeLabel();
            return;
        }
        minecraft.setCurrentScreen(
                switch (button.id) {
                    case 0 ->
                            new SettingsCategoryScreen(
                                    this, settings, SettingsCategoryScreen.Page.VIDEO);
                    case 1 ->
                            new SettingsCategoryScreen(
                                    this, settings, SettingsCategoryScreen.Page.SOUND);
                    case 2 -> new ControlsScreen(this, settings);
                    case 3 ->
                            new SettingsCategoryScreen(
                                    this, settings, SettingsCategoryScreen.Page.GAMEPLAY);
                    case 4 -> new LanguageScreen(this, minecraft.lang);
                    case 5 -> new DeveloperOptionsScreen(this);
                    default -> parent;
                });
    }

    private String screenshotTypeLabel() {
        return "Screenshot Type: " + settings.screenshotType.label;
    }

    @Override
    public void render(int x, int y) {
        drawOptionsBackground();
        drawCenteredString(fontRenderer, "Options", width / 2, 20, 0xFFFFFF);
        super.render(x, y);
    }
}
