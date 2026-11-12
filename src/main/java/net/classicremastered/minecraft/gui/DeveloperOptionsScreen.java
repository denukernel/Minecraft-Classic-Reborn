package net.classicremastered.minecraft.gui;

public final class DeveloperOptionsScreen extends GuiScreen {
    private final GuiScreen parent;

    public DeveloperOptionsScreen(GuiScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        buttons.clear();
        if (minecraft.player != null
                && minecraft.player.developerPowers().active(minecraft.player)) {
            buttons.add(new Button(0, width / 2 - 100, height / 2 - 25, healthLabel()));
        }
        buttons.add(new Button(1, width / 2 - 100, height / 2 + 10, "Done"));
    }

    private String healthLabel() {
        return "Show mob health bar: "
                + (minecraft.player.developerPowers().showMobHealthBar() ? "ON" : "OFF");
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 0
                && minecraft.player != null
                && minecraft.player.developerPowers().active(minecraft.player)) {
            minecraft.player.developerPowers().toggleMobHealthBar();
            button.text = healthLabel();
        } else if (button.id == 1) {
            minecraft.setCurrentScreen(parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY) {
        drawOptionsBackground();
        drawCenteredString(fontRenderer, "Developer Options", width / 2, 20, 0xffffff);
        super.render(mouseX, mouseY);
    }
}
