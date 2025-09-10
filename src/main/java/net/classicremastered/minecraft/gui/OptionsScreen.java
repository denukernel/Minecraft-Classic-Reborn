// File: src/com/mojang/minecraft/gui/OptionsScreen.java
package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.lang.LanguageManager;

public final class OptionsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final GameSettings settings;
    private final boolean compact; // smaller layout when true
    private String title = "Options";

    // Back-compat: default compact=false
    public OptionsScreen(GuiScreen parent, GameSettings settings) {
        this(parent, settings, false);
    }

    public OptionsScreen(GuiScreen parent, GameSettings settings, boolean compact) {
        this.parent = parent;
        this.settings = settings;
        this.compact = compact;
    }
    @Override
    public final void onOpen() {
        this.buttons.clear();

        int btnW = compact ? 120 : 150;
        int btnH = compact ? 16 : 18;

        // Settings toggles (all except difficulty → handled separately below)
        for (int i = 0; i < this.settings.settingCount; ++i) {
            if (i == 10) continue; // skip difficulty here
            int x = this.width / 2 - 155 + i % 2 * 160;
            int y = this.height / 6 + 24 * (i >> 1);
            OptionButton b = new OptionButton(i, x, y, this.settings.getSetting(i));
            b.width = btnW;
            b.height = btnH;
            this.buttons.add(b);
        }

        int baseY = this.height / 6 + 120;

        // Difficulty (bottom row, left)
        OptionButton diff = new OptionButton(10, this.width / 2 - 155, baseY, this.settings.getSetting(10));
        diff.width = btnW;
        diff.height = btnH;
        this.buttons.add(diff);

        // Controls (bottom row, right)
        Button controls = new Button(100, this.width / 2 - 155 + 160, baseY, "Controls...");
        controls.width = btnW;
        controls.height = btnH;
        this.buttons.add(controls);

        // Language (row below, center)
        Button lang = new Button(101, this.width / 2 - btnW / 2, baseY + 24, "Language...");
        lang.width = btnW;
        lang.height = btnH;
        this.buttons.add(lang);

        // Done (last row, center)
        Button done = new Button(200, this.width / 2 - btnW / 2, baseY + 24 * 2, "Done");
        done.width = btnW;
        done.height = btnH;
        this.buttons.add(done);
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
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA1000000);
        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(mouseX, mouseY);
    }

}
