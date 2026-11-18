package net.classicremastered.minecraft.gui;

public final class PauseScreen extends GuiScreen {

    @Override
    public boolean pausesLocalWorld() {
        return true;
    }

    private static final int ID_OPTIONS = 0;
    private static final int ID_SELECTLEVEL = 1;
    private static final int ID_MARKETPLACE = 2;
    private static final int ID_STATS = 3;
    private static final int ID_BACK = 4;
    private static final int ID_QUITMENU = 5;

    @Override
    public void onOpen() {
        buttons.clear();
        int x = width / 2 - 100;
        int y = menuTop();
        buttons.add(new Button(ID_BACK, x, y, "Back to Game"));
        buttons.add(new Button(ID_MARKETPLACE, x, y + 24, 98, 20, "Marketplace"));
        buttons.add(new Button(ID_STATS, x + 102, y + 24, 98, 20, "Statistics"));
        buttons.add(new Button(ID_OPTIONS, x, y + 48, 98, 20, "Options..."));
        Button worlds = new Button(ID_SELECTLEVEL, x + 102, y + 48, 98, 20, "Select World...");
        worlds.active = !minecraft.isOnline();
        buttons.add(worlds);
        buttons.add(
                new Button(
                        ID_QUITMENU,
                        x,
                        y + 72,
                        worlds.active ? "Save and Quit to Title" : "Disconnect"));
    }

    private int menuTop() {
        return Math.max(30, height / 2 - 40);
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
                this.minecraft.setCurrentScreen(new ShopScreen(this));
                return;

            case ID_STATS:
                this.minecraft.setCurrentScreen(new StatsScreen(this));
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
        if (!this.minecraft.saveCurrentLevel()) {
            this.minecraft.setCurrentScreen(
                    new ErrorScreen(
                            "Failed to save",
                            "Could not save the level. The level is still open.",
                            this));
            return;
        }
        if (this.minecraft.networkManager != null) {
            try {
                this.minecraft.networkManager.netHandler.close();
            } catch (Throwable ignored) {
            }
            this.minecraft.networkManager = null;
            this.minecraft.online = false;
        }

        this.minecraft.setLevel(null);
        this.minecraft.setCurrentScreen(new MainMenuScreen());
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        drawCenteredString(fontRenderer, "Game Menu", width / 2, menuTop() - 24, 0xFFFFFF);
        super.render(mx, my);
    }
}
