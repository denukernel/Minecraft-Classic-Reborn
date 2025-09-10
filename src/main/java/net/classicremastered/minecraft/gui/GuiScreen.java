package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class GuiScreen extends Screen {

    protected Minecraft minecraft;
    public int width;
    public int height;
    protected List buttons = new ArrayList();
    public boolean grabsMouse = false;
    protected FontRenderer fontRenderer;

    public void render(int var1, int var2) {
        for (int var3 = 0; var3 < this.buttons.size(); ++var3) {
            Button btn = (Button) this.buttons.get(var3);
            if (!btn.visible) continue;

            FontRenderer fr = this.minecraft.fontRenderer;
            GL11.glBindTexture(3553, this.minecraft.textureManager.load("/gui/gui.png"));
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            byte state = 1;
            boolean hover = var1 >= btn.x && var2 >= btn.y && var1 < btn.x + btn.width && var2 < btn.y + btn.height;
            if (!btn.active) {
                state = 0;
            } else if (hover) {
                state = 2;
            }

            btn.drawImage(btn.x, btn.y, 0, 46 + state * 20, btn.width / 2, btn.height);
            btn.drawImage(btn.x + btn.width / 2, btn.y, 200 - btn.width / 2, 46 + state * 20, btn.width / 2, btn.height);

            int color;
            if (!btn.active) {
                color = -6250336;
            } else if (hover) {
                color = 16777120;
            } else {
                color = 14737632;
            }

            drawCenteredString(fr, btn.text, btn.x + btn.width / 2, btn.y + (btn.height - 8) / 2, color);
        }
    }

    protected void onKeyPress(char var1, int var2) {
        if (var2 == 1) {
            this.minecraft.setCurrentScreen(null);
            this.minecraft.grabMouse();
        }
    }

    // handle button clicks
    protected void onMouseClick(int mx, int my, int button) {
        if (button == 0) {
            for (int i = 0; i < this.buttons.size(); ++i) {
                Button btn = (Button) this.buttons.get(i);
                boolean over = btn.active && mx >= btn.x && my >= btn.y && mx < btn.x + btn.width && my < btn.y + btn.height;
                if (over) {
                    try {
                        if (minecraft != null && minecraft.settings != null && minecraft.settings.sound && minecraft.soundPC != null) {
                            if (minecraft.soundPC.farlandsActive) {
                                // corrupted: random pitch clicks
                                float randomPitch = 0.2f + minecraft.soundPC.rng.nextFloat() * 2.5f;
                                minecraft.soundPC.playSoundClean("random/click", 0.8f, randomPitch);
                                } else {
                                // normal click
                                minecraft.soundPC.playSoundClean("random/click", 0.8f, 1.0f);
                            }
                        }
                    } catch (Throwable ignored) {}

                    this.onButtonClick(btn);
                }
            }
        }
    }

    protected void onButtonClick(Button btn) {
    }

    public final void open(Minecraft mc, int w, int h) {
        this.minecraft = mc;
        this.fontRenderer = mc.fontRenderer;
        this.width = w;
        this.height = h;
        this.onOpen();
    }

    public void onOpen() {
    }

    public final void doInput() {
        while (Mouse.next()) this.mouseEvent();
        while (Keyboard.next()) this.keyboardEvent();
    }

    public final void mouseEvent() {
        if (Mouse.getEventButtonState()) {
            int x = Mouse.getEventX() * this.width / this.minecraft.width;
            int y = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
            this.onMouseClick(x, y, Mouse.getEventButton());
        }
    }

    public final void keyboardEvent() {
        if (Keyboard.getEventKeyState()) {
            this.onKeyPress(Keyboard.getEventCharacter(), Keyboard.getEventKey());
        }
    }

    public void tick() {
    }

    public void onClose() {
    }
}
