package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;

public final class MainMenuScreen extends GuiScreen {

    // Button ids
    private static final int ID_SINGLEPLAYER = 0;
    private static final int ID_OPTIONS = 2;
    private static final int ID_QUIT = 3;
    private static final int ID_SELECTLEVEL = 4;
    private static final int ID_MODS = 5; // added
    private static final int ID_MINIGAMES = 8;
    private int mascotTex = -1; // added

    private String title = "Minecraft Classic Improved";
    private String splash = "";
    private static final String[] SPLASHES = new String[] { "This is now a year old" };

    @Override
    public void onOpen() {
        this.buttons.clear();

        int btnW = 200;
        int btnH = 20;
        int x = (this.width - btnW) / 2;
        int y = this.height / 4 + 16;
        String singleplayer = "Singleplayer";
        this.buttons.add(new Button(ID_SELECTLEVEL, x, y, btnW, btnH, singleplayer));
        this.buttons.add(new Button(7, x, y + 24, btnW, btnH, "Multiplayer"));
        this.buttons.add(new Button(ID_MINIGAMES, x, y + 2 * 24, btnW, btnH, "Minigames"));
        String options = this.minecraft.lang.tr("menu.options");

        this.buttons.add(new Button(ID_OPTIONS, x, y + 3 * 24, btnW, btnH, options));
        this.buttons.add(new Button(ID_MODS, x, y + 4 * 24, btnW, btnH, "Mods"));
        this.buttons.add(new Button(6, x, y + 5 * 24, btnW, btnH, "Debug Tools"));
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
        drawMenuBackground();

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
        case ID_MINIGAMES: {
            this.minecraft.setCurrentScreen(new MinigamesScreen(this));
            break;
        }
        case 6:
            this.minecraft.setCurrentScreen(new DebugScreen(this));
            break;
        case 7:
            this.minecraft.setCurrentScreen(new MultiplayerConnectScreen(this));
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
