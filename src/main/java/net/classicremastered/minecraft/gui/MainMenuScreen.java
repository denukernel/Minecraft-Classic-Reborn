package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.Minecraft;

import java.util.Random;

public final class MainMenuScreen extends GuiScreen {

    // Button ids
    private static final int ID_SINGLEPLAYER = 0;
    private static final int ID_OPTIONS = 2;
    private static final int ID_QUIT = 3;
    private static final int ID_SELECTLEVEL = 4;
    private static final int ID_MODS = 5; // added
    private int mascotTex = -1; // added

    private String title = "Minecraft Classic Improved";
    private String splash = "";
    private static final String[] SPLASHES = new String[] { "Zombie Builder builds blocks!",
            "Iron Monster hates players!", "Bees were added in Classic!", "2009 Continued!", "Play old versions!",
            "Minecoins drop from mobs!", "ClassicCube should expand like this!",
            "Don't support the bad future of minecraft!", "Minecraft Live is a mess!", "No Microsoft purification!",
            "Notch’s Dream lives on!", "Marketplace mobs are free!", "Classic forever!", "Infinite terrain is real!",
            "Placeholder mobs are the best!", "C418 > all other music!", "Mob Vote? All mobs included!" };

    @Override
    public void onOpen() {
        this.buttons.clear();
        mascotTex = this.minecraft.textureManager.load("/gui/stickman.png"); // added

        int btnW = 200;
        int btnH = 20;
        int x = (this.width - btnW) / 2;
        int y = this.height / 4 + 24;
        String singleplayer = "Singleplayer";
        this.buttons.add(new Button(ID_SELECTLEVEL, x, y + 1 * 24, btnW, btnH, singleplayer));
        String options = this.minecraft.lang.tr("menu.options");

        this.buttons.add(new Button(ID_OPTIONS, x, y + 2 * 24, btnW, btnH, options));
        this.buttons.add(new Button(ID_MODS, x, y + 3 * 24, btnW, btnH, "Mods"));
        this.buttons.add(new Button(6, x, y + 4 * 24, btnW, btnH, "Debug Tools"));
        // Pick a new random splash
        java.util.Random rand = new java.util.Random();
        splash = SPLASHES[rand.nextInt(SPLASHES.length)];

        // --- FIX: Reset GL state so background dirt renders properly ---
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        this.grabsMouse = false;
    }

    @Override
    public void render(int mouseX, int mouseY) {
        // Always start with a clean screen
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // --- Dirt background ---
        int tex = this.minecraft.textureManager.load("/dirt.png");
        if (tex != 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);

            float mult = (this.minecraft.settings != null ? this.minecraft.settings.menuLighting : 0.6f);
            GL11.glColor4f(mult, mult, mult, 1f);

            long sysTime = System.currentTimeMillis();
            float scroll = (sysTime % 20000L) / 1000.0F;

            float u = this.width / 32.0F;
            float v = this.height / 32.0F;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F + scroll, 0.0F + scroll);
            GL11.glVertex2f(0, 0);
            GL11.glTexCoord2f(u + scroll, 0.0F + scroll);
            GL11.glVertex2f(this.width, 0);
            GL11.glTexCoord2f(u + scroll, v + scroll);
            GL11.glVertex2f(this.width, this.height);
            GL11.glTexCoord2f(0.0F + scroll, v + scroll);
            GL11.glVertex2f(0, this.height);
            GL11.glEnd();
        }

        // Reset color for text
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Title + version
        drawCenteredString(this.fontRenderer, asciiOnly(title), this.width / 2, this.height / 8, 0xFFFFFF);
        String version = "Minecraft Classic 0.30 Mod";
        drawCenteredString(this.fontRenderer, asciiOnly(version), this.width / 2, this.height / 8 + 12, 0xAAAAAA);

        // --- splash ---
        if (splash != null && !splash.isEmpty()) {
            long t = System.currentTimeMillis();
            float anim = (t % 1000L) / 1000.0F;
            float scale = 0.8F + 0.2F * (float) Math.sin(anim * Math.PI * 2.0F);
            float tilt = -0.0F;

            int color = 0xFFFF00;
            String text = asciiOnly(splash);
            int w = this.fontRenderer.getWidth(text);

            GL11.glPushMatrix();
            GL11.glTranslatef(this.width / 2.0F, this.height / 8.0F + 40.0F, 0.0F);
            GL11.glRotatef(tilt, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(scale, scale, 1.0F);

            this.fontRenderer.renderNoShadow(text, -w / 2, -8, color);
            GL11.glPopMatrix();
        }
     // --- spinning text ---
        {
            String spinText = "Classic Reborn";
            float spinAngle = (System.currentTimeMillis() % 5000L) / 5000.0F * 360.0F; // full rotation every 5s

            int color = 0x00FFFF; // cyan
            int w = this.fontRenderer.getWidth(spinText);

            GL11.glPushMatrix();
            GL11.glTranslatef(this.width / 2.0F, this.height / 8.0F + 60.0F, 0.0F);
            GL11.glRotatef(spinAngle, 0.0F, 0.0F, 1.0F);
            this.fontRenderer.renderNoShadow(spinText, -w / 2, -4, color);
            GL11.glPopMatrix();
        }

        // --- mascot (draw after background/splash) ---
        if (mascotTex != 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mascotTex);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            int size = 64;
            int x = this.width - size - 20;
            int y = this.height - size - 40;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex2f(x + size, y);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex2f(x + size, y + size);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex2f(x, y + size);
            GL11.glEnd();
        }

        // Draw buttons
        super.render(mouseX, mouseY);

        // Copyright
        String copy = "Copyright Mojang Specifications. Do not distribute!";
        int copyW = this.fontRenderer.getWidth(copy);
        this.fontRenderer.renderNoShadow(copy, this.width - copyW - 2, this.height - 10, 0xFFFFFF);
    }

    @Override
    protected void onButtonClick(Button b) {
        switch (b.id) {
        case ID_SINGLEPLAYER: {
            this.minecraft.setCurrentScreen(new GenerateLevelScreen(this));
            break;
        }
        case ID_OPTIONS: {
            this.minecraft.setCurrentScreen(new OptionsScreen(this, this.minecraft.settings));
            break;
        }
        case ID_SELECTLEVEL: {
            this.minecraft.setCurrentScreen(new SelectLevelScreen(this));
            break;
        }
        case ID_MODS: {
            this.minecraft.setCurrentScreen(new ModsScreen(this));
            break;
        }
        case 6:
            this.minecraft.setCurrentScreen(new DebugScreen());
            break;

        }
    }

    private static String asciiOnly(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c <= 255 ? c : ' ');
        }
        return sb.toString();
    }

    @Override
    protected void onKeyPress(char ch, int key) {
        // swallow ESC
    }
}
