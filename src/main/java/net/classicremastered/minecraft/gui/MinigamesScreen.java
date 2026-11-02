package net.classicremastered.minecraft.gui;

/** Empty menu reserved for future minigames. */
public final class MinigamesScreen extends GuiScreen {
    private final GuiScreen parent;

    public MinigamesScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        this.buttons.add(new Button(0, (this.width - 200) / 2, this.height - 40, 200, 20, "Back"));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        drawBox(0, 0, this.width, this.height, 0xFF202020);
        drawCenteredString(this.fontRenderer, "Minigames", this.width / 2, 20, 0xFFFFFF);
        drawCenteredString(this.fontRenderer, "No minigames available yet.", this.width / 2,
                this.height / 2 - 4, 0xAAAAAA);
        super.render(mouseX, mouseY);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 0) {
            this.minecraft.setCurrentScreen(this.parent);
        }
    }

    @Override
    protected void onKeyPress(char ch, int key) {
        if (key == 1) {
            this.minecraft.setCurrentScreen(this.parent);
        }
    }
}
