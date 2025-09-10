package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;

import net.classicremastered.minecraft.Minecraft;

import java.io.File;

public final class LevelNameScreen extends GuiScreen {

    private final GuiScreen parent;
    private final String title = "Enter level name:";
    private final int id;   // slot id or save slot index
    private String name;
    private int counter = 0;

    public LevelNameScreen(GuiScreen parent, String currentName, int id) {
        this.parent = parent;
        this.id = id;
        this.name = currentName;
        if (this.name.equals("-")) {
            this.name = "";
        }
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        this.buttons.add(new Button(0, this.width / 2 - 100, this.height / 4 + 120, "Save"));
        this.buttons.add(new Button(1, this.width / 2 - 100, this.height / 4 + 144, "Cancel"));
        ((Button) this.buttons.get(0)).active = this.name.trim().length() > 1;
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        ++this.counter;
    }

    @Override
    protected void onButtonClick(Button b) {
        if (!b.active) return;

        if (b.id == 0 && this.name.trim().length() > 1) {
            // --- Save locally ---
            String safeName = this.name.trim();
            this.minecraft.level.name = safeName;

            try {
                File savesDir = new File("saves");
                if (!savesDir.exists()) savesDir.mkdirs();

                File saveFile = new File(savesDir, "level" + id + ".lvl");
                boolean ok = this.minecraft.levelIo.save(this.minecraft.level, saveFile);
                if (!ok) {
                    System.err.println("Failed to save level " + safeName);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
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
        if (key == Keyboard.KEY_BACK && this.name.length() > 0) {
            this.name = this.name.substring(0, this.name.length() - 1);
        }

        String allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ,.:-_'*!\"#%/()=+?[]{}<>";
        if (allowed.indexOf(c) >= 0 && this.name.length() < 64) {
            this.name = this.name + c;
        }

        ((Button) this.buttons.get(0)).active = this.name.trim().length() > 1;
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);

        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 40, 0xFFFFFF);

        int boxX = this.width / 2 - 100;
        int boxY = this.height / 2 - 10;

        drawBox(boxX - 1, boxY - 1, boxX + 201, boxY + 21, 0xFF999999);
        drawBox(boxX, boxY, boxX + 200, boxY + 20, 0xFF000000);

        String cursor = (this.counter / 6 % 2 == 0 ? "_" : "");
        drawString(this.fontRenderer, this.name + cursor, boxX + 4, boxY + 6, 0xE0E0E0);

        super.render(mx, my);
    }
}
