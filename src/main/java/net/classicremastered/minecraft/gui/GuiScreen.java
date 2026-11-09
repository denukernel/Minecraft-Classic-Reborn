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
    private final GuiScreen parentScreen;

    public GuiScreen() { this(null); }

    protected GuiScreen(GuiScreen parent) { this.parentScreen = parent; }

    /** Start menu rendering with explicit state instead of inheriting world fog or lighting. */
    protected final void drawMenuBackground() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        this.minecraft.renderer.enableGuiMode();
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.minecraft.textureManager.load("/dirt.png"));
        float light = this.minecraft.settings != null ? this.minecraft.settings.menuLighting : .6f;
        GL11.glColor4f(light, light, light, 1);
        float scroll = (System.currentTimeMillis() % 20000L) / 5000f;
        float u = this.width / 32f, v = this.height / 32f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(scroll, v + scroll); GL11.glVertex2f(0, this.height);
        GL11.glTexCoord2f(u + scroll, v + scroll); GL11.glVertex2f(this.width, this.height);
        GL11.glTexCoord2f(u + scroll, scroll); GL11.glVertex2f(this.width, 0);
        GL11.glTexCoord2f(scroll, scroll); GL11.glVertex2f(0, 0);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
    }

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
            this.minecraft.setCurrentScreen(this.parentScreen);
        }
    }

    // handle button clicks
    protected void onMouseClick(int mx, int my, int button) {
        if (button == 0) {
            for (int i = 0; i < this.buttons.size(); ++i) {
                Button btn = (Button) this.buttons.get(i);
                boolean over = btn.visible && btn.active && mx >= btn.x && my >= btn.y && mx < btn.x + btn.width && my < btn.y + btn.height;
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
                    return;
                }
            }
        }
    }

    protected void onButtonClick(Button btn) {
    }
    public boolean grabsMouse() {
        return false;
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
        while (this.minecraft.currentScreen == this && Mouse.next()) this.mouseEvent();
        while (this.minecraft.currentScreen == this && Keyboard.next()) this.keyboardEvent();
    }

    public void mouseEvent() {
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
