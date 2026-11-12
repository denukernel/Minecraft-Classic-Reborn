package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.GameSettings;

public final class ControlsScreen extends GuiScreen {

    private GuiScreen parent;
    private String title = "Controls";
    private GameSettings settings;
    private int selected = -1;

    public ControlsScreen(GuiScreen var1, GameSettings var2) {
        super(var1);
        this.parent = var1;
        this.settings = var2;
    }

    public final void onOpen() {
        this.buttons.clear();
        this.selected = -1;
        int column = Math.min(150, (width - 24) / 2);
        for (int i = 0; i < settings.bindings.length; i++) {
            int x = i % 2 == 0 ? width / 2 - column - 4 : width / 2 + 4;
            Button button =
                    new OptionButton(i, x, height / 6 + 24 * (i / 2), settings.getBinding(i));
            button.width = column;
            buttons.add(button);
        }

        this.buttons.add(new Button(200, this.width / 2 - 100, this.height / 6 + 168, "Done"));
    }

    protected final void onButtonClick(Button var1) {
        for (int var2 = 0; var2 < this.settings.bindings.length; ++var2) {
            ((Button) this.buttons.get(var2)).text = this.settings.getBinding(var2);
        }

        if (var1.id == 200) {
            this.minecraft.setCurrentScreen(this.parent);
        } else {
            this.selected = var1.id;
            var1.text = "> " + this.settings.getBinding(var1.id) + " <";
        }
    }

    protected final void onKeyPress(char var1, int var2) {
        if (this.selected >= 0) {
            if (var2 != 1) this.settings.setBinding(this.selected, var2);
            ((Button) this.buttons.get(this.selected)).text =
                    this.settings.getBinding(this.selected);
            this.selected = -1;
        } else {
            super.onKeyPress(var1, var2);
        }
    }

    public final void render(int var1, int var2) {
        drawOptionsBackground();
        drawFadingBox(0, 0, this.width, this.height, 1610941696, -1607454624);
        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 20, 16777215);
        super.render(var1, var2);
    }
}
