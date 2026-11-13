package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.GameSettings;
import org.lwjgl.input.Mouse;

final class SettingsCategoryScreen extends GuiScreen {
    enum Page {
        VIDEO(
                "Video Settings",
                15,
                4,
                14,
                5,
                3,
                7,
                8,
                9,
                6,
                13,
                GameSettings.MOB_SHADOWS,
                GameSettings.PLAYER_SHADOWS),
        SOUND("Sound", 0, 1, 12),
        GAMEPLAY("Gameplay", 10, 11, 2, 16, 17);

        final String label;
        final int[] options;

        Page(String label, int... options) {
            this.label = label;
            this.options = options;
        }
    }

    private final GuiScreen parent;
    private final GameSettings settings;
    private final Page page;
    private int sliderX, sliderY, sliderWidth;
    private boolean dragging;

    SettingsCategoryScreen(GuiScreen parent, GameSettings settings, Page page) {
        super(parent);
        this.parent = parent;
        this.settings = settings;
        this.page = page;
    }

    @Override
    public void onOpen() {
        buttons.clear();
        int column = Math.min(150, (width - 24) / 2);
        for (int i = 0; i < page.options.length; i++) {
            int id = page.options[i];
            int x = i % 2 == 0 ? width / 2 - column - 4 : width / 2 + 4;
            Button button = new OptionButton(id, x, 39 + i / 2 * 23, settings.getSetting(id));
            button.width = column;
            buttons.add(button);
        }
        sliderX = width / 2 - column - 4;
        sliderY = 39 + (page.options.length + 1) / 2 * 23;
        sliderWidth = column * 2 + 8;
        buttons.add(new Button(200, width / 2 - 100, height - 32, "Done"));
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 200) minecraft.setCurrentScreen(parent);
        else {
            settings.toggleSetting(button.id, 1);
            button.text = settings.getSetting(button.id);
        }
    }

    @Override
    protected void onMouseClick(int x, int y, int button) {
        if (page == Page.VIDEO
                && button == 0
                && x >= sliderX
                && x < sliderX + sliderWidth
                && y >= sliderY
                && y < sliderY + 18) {
            dragging = true;
            updateBrightness(x);
        } else super.onMouseClick(x, y, button);
    }

    private void updateBrightness(int x) {
        float value = Math.max(0, Math.min(1, (x - sliderX - 4f) / (sliderWidth - 8f)));
        if (Math.abs(value - settings.brightness) > .005f) settings.setBrightness(value);
    }

    @Override
    public void onClose() {
        dragging = false;
    }

    @Override
    public void render(int x, int y) {
        drawOptionsBackground();
        drawCenteredString(fontRenderer, page.label, width / 2, 20, 0xFFFFFF);
        if (page == Page.VIDEO) {
            ((Button) buttons.get(0)).text = settings.getSetting(15);
            if (dragging) {
                if (Mouse.isCreated() && Mouse.isButtonDown(0)) updateBrightness(x);
                else dragging = false;
            }
            drawBox(sliderX, sliderY, sliderX + sliderWidth, sliderY + 18, 0xFF404040);
            int thumb = sliderX + Math.round(settings.brightness * (sliderWidth - 8));
            drawBox(thumb, sliderY, thumb + 8, sliderY + 18, 0xFFAAAAAA);
            drawCenteredString(
                    fontRenderer,
                    "Brightness: " + Math.round(settings.brightness * 100) + "%",
                    width / 2,
                    sliderY + 5,
                    0xFFFFFF);
        }
        super.render(x, y);
    }
}
