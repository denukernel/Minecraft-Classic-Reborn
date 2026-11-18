package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;

import java.io.File;

public final class LevelNameScreen extends GuiScreen {

    public interface Receiver {
        void onNameEntered(String name);
    }

    private final GuiScreen parent;
    private final String title = "Enter level name:";
    private final int id; // slot id or save slot index
    private String name;
    private GuiTextField nameField;
    private Receiver receiver;

    public LevelNameScreen(GuiScreen parent, String currentName, int id) {
        super(parent);
        this.parent = parent;
        this.id = id;
        this.name = currentName == null ? "" : currentName;
        if (this.name.equals("-")) {
            this.name = "";
        }
    }

    public LevelNameScreen(GuiScreen parent, String currentName, Receiver receiver) {
        super(parent);
        this.parent = parent;
        this.id = -1;
        this.name = currentName == null ? "" : currentName;
        this.receiver = receiver;
        if (this.name.equals("-")) {
            this.name = "";
        }
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        int x = width / 2 - 100;
        int y = height / 2 - 10;
        nameField = new GuiTextField(fontRenderer, x, y, 200, 20, false);
        nameField.setText(name);
        nameField.setFocused(true);
        this.buttons.add(new Button(0, x, y + 32, 98, 20, "Save"));
        this.buttons.add(new Button(1, x + 102, y + 32, 98, 20, "Cancel"));
        ((Button) this.buttons.get(0)).active = !name.isBlank();
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        nameField.updateCursorCounter();
    }

    @Override
    protected void onButtonClick(Button b) {
        if (!b.active) return;

        if (b.id == 0 && !name.isBlank()) {
            String safeName = name.strip();
            if (this.receiver != null) {
                this.receiver.onNameEntered(safeName);
                return;
            }

            // --- Save locally ---
            if (this.minecraft.level == null) {
                this.minecraft.setCurrentScreen(
                        new ErrorScreen("Failed to save", "No level is loaded.", this));
                return;
            }
            net.classicremastered.minecraft.level.dimensions.StickmansHouse.homeLevel(
                                    this.minecraft.level)
                            .name =
                    safeName;

            try {
                File savesDir = new File("saves");
                if (!savesDir.exists()) savesDir.mkdirs();

                File saveFile = new File(savesDir, "level" + id + ".lvl");
                boolean ok = this.minecraft.levelIo.save(this.minecraft.level, saveFile);
                if (!ok) {
                    this.minecraft.setCurrentScreen(
                            new ErrorScreen(
                                    "Failed to save", "Could not save the level file.", this));
                    return;
                }
            } catch (Exception ex) {
                this.minecraft.setCurrentScreen(
                        new ErrorScreen("Failed to save", ex.toString(), this));
                return;
            }

            // return to game
            this.minecraft.setCurrentScreen(null);
            this.minecraft.grabMouse();
        }

        if (b.id == 1) { // Cancel
            this.minecraft.setCurrentScreen(this.parent);
        }
    }

    @Override
    protected void onKeyPress(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.setCurrentScreen(this.parent);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            onButtonClick((Button) this.buttons.get(0));
            return;
        }
        if (nameField.textboxKeyTyped(c, key)) {
            name = nameField.getText();
            ((Button) this.buttons.get(0)).active = !name.isBlank();
        }
    }

    @Override
    public void render(int mx, int my) {
        if (this.minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);

        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 40, 0xFFFFFF);

        nameField.drawTextBox();

        super.render(mx, my);
    }
}
