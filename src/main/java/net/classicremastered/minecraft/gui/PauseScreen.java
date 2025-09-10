package net.classicremastered.minecraft.gui;

import java.io.File;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class PauseScreen extends GuiScreen {

    // IDs
    private static final int ID_OPTIONS = 0;
    private static final int ID_SELECTLEVEL = 1;
    private static final int ID_MARKETPLACE = 2; // NEW
    private static final int ID_BACK = 4;
    private static final int ID_QUITMENU = 5;

    @Override
    public void onOpen() {
        this.buttons.clear();
        int x = this.width / 2 - 100;
        int y = this.height / 4;

        this.buttons.add(new Button(ID_OPTIONS, x, y + 0, "Options..."));
        this.buttons.add(new Button(ID_SELECTLEVEL, x, y + 24, "Select Level..."));
        this.buttons.add(new Button(ID_MARKETPLACE, x, y + 48, "Marketplace (Parody)")); // NEW
        this.buttons.add(new Button(ID_QUITMENU, x, y + 72, "Quit to Main Menu"));
        this.buttons.add(new Button(ID_BACK, x, y + 96, "Back to game"));

        // Disable Select Level in multiplayer
        if (this.minecraft.networkManager != null) {
            ((Button) this.buttons.get(1)).active = false; // Select Level...
        }
    }

    @Override
    protected void onButtonClick(Button b) {
        switch (b.id) {
        case ID_OPTIONS:
            this.minecraft.setCurrentScreen(new OptionsScreen(this, this.minecraft.settings));
            return;

        case ID_SELECTLEVEL:
            this.minecraft.setCurrentScreen(new SelectLevelScreen(this));
            return;

        case ID_MARKETPLACE:
            this.minecraft.setCurrentScreen(new ShopScreen()); // NEW
            return;

        case ID_BACK:
            this.minecraft.setCurrentScreen((GuiScreen) null);
            this.minecraft.grabMouse();
            return;

        case ID_QUITMENU:
            quitToMainMenu();
            return;
        }
    }

    private void quitToMainMenu() {
        if (this.minecraft.networkManager != null) {
            try {
                this.minecraft.networkManager.netHandler.close();
            } catch (Throwable ignored) {
            }
            this.minecraft.networkManager = null;
            this.minecraft.online = false;
        }

        if (this.minecraft.level != null) {
            try {
                File levelsDir = new File(Minecraft.getMinecraftDir(), "levels");
                if (!levelsDir.exists())
                    levelsDir.mkdirs();
                File autosave = new File(levelsDir, "autosave.lvl.gz");
                this.minecraft.levelIo.save(this.minecraft.level, autosave);
            } catch (Throwable ignored) {
            }
        }

        this.minecraft.setLevel(null);
        this.minecraft.setCurrentScreen(new MainMenuScreen());
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        drawCenteredString(this.fontRenderer, "Game menu", this.width / 2, 40, 0xFFFFFF);
        super.render(mx, my);
    }
}
