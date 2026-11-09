// File: src/com/mojang/minecraft/gui/OptionsScreen.java
package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.lang.LanguageManager;

public final class OptionsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final GameSettings settings;
    private final boolean compact; // smaller layout when true
    private String title = "Options";
    private int sliderX, sliderY, sliderW;
    private boolean draggingBrightness;

    @Override
    protected void onMouseClick(int x, int y, int button) {
        if (button == 0
                && x >= sliderX
                && x < sliderX + sliderW
                && y >= sliderY
                && y < sliderY + 18) {
            draggingBrightness = true;
            updateBrightness(x);
            return;
        }
        super.onMouseClick(x, y, button);
    }

    private void updateBrightness(int x) {
        float value = Math.max(0, Math.min(1, (x - sliderX - 4f) / (sliderW - 8f)));
        if (Math.abs(value - settings.brightness) > .005f) settings.setBrightness(value);
    }

    @Override
    public void onClose() {
        draggingBrightness = false;
    }

    // Back-compat: default compact=false
    public OptionsScreen(GuiScreen parent, GameSettings settings) {
        this(parent, settings, false);
    }

    public OptionsScreen(GuiScreen parent, GameSettings settings, boolean compact) {
        super(parent);
        this.parent = parent;
        this.settings = settings;
        this.compact = compact;
    }

    @Override
    public final void onOpen() {
        this.buttons.clear();

        int btnW = compact ? 120 : 150;
        int btnH = compact ? 16 : 18;
        int startY = Math.max(26, this.height / 6 - 12);
        int rowSpacing = 22;
        boolean developerOptions =
                minecraft.player != null
                        && minecraft.player.developerPowers().active(minecraft.player);
        rowSpacing =
                Math.min(
                        rowSpacing,
                        Math.max(18, (this.height - startY - 22) / (developerOptions ? 11 : 10)));

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;

        // Row 0: Music & Sound
        addOptionButton(0, leftX, startY + rowSpacing * 0, btnW, btnH);
        addOptionButton(1, rightX, startY + rowSpacing * 0, btnW, btnH);

        // Row 1: Allow Mob Sounds & Invert Mouse
        addOptionButton(12, leftX, startY + rowSpacing * 1, btnW, btnH);
        addOptionButton(2, rightX, startY + rowSpacing * 1, btnW, btnH);

        // Row 2: Show FPS & Limit Framerate
        addOptionButton(3, leftX, startY + rowSpacing * 2, btnW, btnH);
        addOptionButton(7, rightX, startY + rowSpacing * 2, btnW, btnH);

        // Row 3: Render Distance & View Bobbing
        addOptionButton(4, leftX, startY + rowSpacing * 3, btnW, btnH);
        addOptionButton(5, rightX, startY + rowSpacing * 3, btnW, btnH);

        // Row 4: 3D Anaglyph & Smoothing
        addOptionButton(6, leftX, startY + rowSpacing * 4, btnW, btnH);
        addOptionButton(8, rightX, startY + rowSpacing * 4, btnW, btnH);

        // Row 5: Anisotropic & Finite Liquids
        addOptionButton(9, leftX, startY + rowSpacing * 5, btnW, btnH);
        addOptionButton(11, rightX, startY + rowSpacing * 5, btnW, btnH);

        // Row 6: Difficulty & Multithreading
        addOptionButton(10, leftX, startY + rowSpacing * 6, btnW, btnH);
        addOptionButton(13, rightX, startY + rowSpacing * 6, btnW, btnH);

        addOptionButton(14, leftX, startY + rowSpacing * 7, btnW, btnH);
        sliderX = leftX;
        sliderY = startY + rowSpacing * 8;
        sliderW = rightX + btnW - leftX;
        if (developerOptions) {
            Button developer =
                    new Button(102, leftX, startY + rowSpacing * 9 + 2, "Developer Options...");
            developer.width = sliderW;
            developer.height = btnH;
            buttons.add(developer);
        }
        int bottomRow = developerOptions ? 10 : 9;
        Button controls =
                new Button(100, leftX, startY + rowSpacing * bottomRow + 2, "Controls...");
        controls.width = btnW;
        controls.height = btnH;
        this.buttons.add(controls);

        Button done = new Button(200, rightX, startY + rowSpacing * bottomRow + 2, "Done");
        done.width = btnW;
        done.height = btnH;
        this.buttons.add(done);
    }

    private void addOptionButton(int id, int x, int y, int w, int h) {
        OptionButton b = new OptionButton(id, x, y, this.settings.getSetting(id));
        b.width = w;
        b.height = h;
        this.buttons.add(b);
    }

    @Override
    protected final void onButtonClick(Button b) {
        if (!b.active) return;

        if (b.id < 100) {
            this.settings.toggleSetting(b.id, 1);
            b.text = this.settings.getSetting(b.id);
        }

        if (b.id == 100) {
            this.minecraft.setCurrentScreen(new ControlsScreen(this, this.settings));
        }

        if (b.id == 102
                && minecraft.player != null
                && minecraft.player.developerPowers().active(minecraft.player)) {
            minecraft.setCurrentScreen(new DeveloperOptionsScreen(this));
        }

        if (b.id == 101) {
            // Open the language selector
            LanguageManager langMgr = this.minecraft.lang;
            this.minecraft.setCurrentScreen(new LanguageScreen(this, langMgr));
        }

        if (b.id == 200) {
            this.minecraft.setCurrentScreen(this.parent);
        }
    }

    @Override
    public final void render(int mouseX, int mouseY) {
        if (this.minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA1000000);
        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        super.render(mouseX, mouseY);
        if (draggingBrightness) {
            if (org.lwjgl.input.Mouse.isCreated() && org.lwjgl.input.Mouse.isButtonDown(0))
                updateBrightness(mouseX);
            else draggingBrightness = false;
        }
        drawBox(sliderX, sliderY, sliderX + sliderW, sliderY + 18, 0xff404040);
        int thumb = sliderX + Math.round(settings.brightness * (sliderW - 8));
        drawBox(thumb, sliderY, thumb + 8, sliderY + 18, 0xffaaaaaa);
        drawCenteredString(
                fontRenderer,
                "Brightness: " + Math.round(settings.brightness * 100) + "%",
                sliderX + sliderW / 2,
                sliderY + 5,
                0xffffff);
    }
}
