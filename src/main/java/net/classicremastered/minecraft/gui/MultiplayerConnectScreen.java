package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.CreativeInventoryBlocks;
import net.classicremastered.minecraft.net.NetworkManager;
import net.classicremastered.minecraft.level.Level;

public final class MultiplayerConnectScreen extends GuiScreen {
    private final GuiScreen parent;
    private String username;
    private String ip;
    private String port;
    private int focusedField = 1; // 0 = Username, 1 = Server IP, 2 = Port
    private int counter = 0;

    public MultiplayerConnectScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);

        // Load persisted settings
        this.username = this.minecraft.settings.mpUsername;
        if (this.username == null || this.username.trim().isEmpty()) {
            this.username = "Player";
        }
        this.ip = this.minecraft.settings.lastServerIp;
        if (this.ip == null || this.ip.trim().isEmpty()) {
            this.ip = "127.0.0.1";
        }
        this.port = String.valueOf(this.minecraft.settings.lastServerPort);
        if (this.port == null || this.port.trim().isEmpty() || this.port.equals("0")) {
            this.port = "25565";
        }

        // Add action buttons
        this.buttons.add(new Button(0, this.width / 2 - 100, this.height / 4 + 125, "Connect"));
        this.buttons.add(new Button(1, this.width / 2 - 100, this.height / 4 + 150, "Cancel"));

        updateConnectButtonActive();
    }

    private void updateConnectButtonActive() {
        boolean isValid = this.username.trim().length() > 0 
                && this.ip.trim().length() > 0 
                && this.port.trim().length() > 0;
        if (this.buttons.size() > 0) {
            ((Button) this.buttons.get(0)).active = isValid;
        }
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

        if (b.id == 0) { // Connect
            // Save settings
            this.minecraft.settings.mpUsername = this.username.trim();
            this.minecraft.settings.lastServerIp = this.ip.trim();
            try {
                this.minecraft.settings.lastServerPort = Integer.parseInt(this.port.trim());
            } catch (NumberFormatException e) {
                this.minecraft.settings.lastServerPort = 25565;
            }
            this.minecraft.settings.save();

            // Set up connection on minecraft instance
            this.minecraft.online = true;
            this.minecraft.server = this.minecraft.settings.lastServerIp;
            this.minecraft.port = this.minecraft.settings.lastServerPort;
            this.minecraft.session = new CreativeInventoryBlocks(this.minecraft.settings.mpUsername, "");
            this.minecraft.session.mppass = "";

            if (this.minecraft.networkManager != null) {
                try {
                    this.minecraft.networkManager.netHandler.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            // Close screen and transition
            this.minecraft.setCurrentScreen(null);

            // Re-create a minimal network level so the renderer doesn't crash while loading chunks
            Level tinyNetLevel = new Level();
            tinyNetLevel.setData(8, 8, 8, new byte[512]);
            this.minecraft.setLevel(tinyNetLevel);

            // Connect
            this.minecraft.networkManager = new NetworkManager(
                this.minecraft, 
                this.minecraft.server, 
                this.minecraft.port, 
                this.minecraft.session.username, 
                this.minecraft.session.mppass
            );
            this.minecraft.networkManager.parentScreen = this;
        }

        if (b.id == 1) { // Cancel
            this.minecraft.setCurrentScreen(this.parent);
        }
    }

    @Override
    protected void onKeyPress(char c, int key) {
        if (key == Keyboard.KEY_TAB) {
            // Cycle fields: Shift+TAB goes backwards, TAB goes forwards
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (shift) {
                focusedField = (focusedField + 2) % 3;
            } else {
                focusedField = (focusedField + 1) % 3;
            }
            return;
        }

        String activeText = getActiveText();

        if (key == Keyboard.KEY_BACK && activeText.length() > 0) {
            setActiveText(activeText.substring(0, activeText.length() - 1));
        }

        // Allowed characters depending on focus
        String allowed;
        if (focusedField == 0) {
            // Username allowed characters
            allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-";
        } else if (focusedField == 1) {
            // IP / Hostname allowed characters
            allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_";
        } else {
            // Port allowed characters (numbers only)
            allowed = "0123456789";
        }

        if (allowed.indexOf(c) >= 0 && activeText.length() < 40) {
            setActiveText(activeText + c);
        }

        updateConnectButtonActive();
    }

    private String getActiveText() {
        if (focusedField == 0) return username;
        if (focusedField == 1) return ip;
        return port;
    }

    private void setActiveText(String val) {
        if (focusedField == 0) username = val;
        else if (focusedField == 1) ip = val;
        else port = val;
    }

    @Override
    public void render(int mx, int my) {
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

        // Draw darker screen overlay
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);

        drawCenteredString(this.fontRenderer, "Multiplayer Server Connection", this.width / 2, 20, 0xFFFFFF);

        int baseX = this.width / 2 - 100;
        int startY = 45;

        // Render fields: Username, IP, Port
        for (int i = 0; i < 3; i++) {
            String label = "";
            String val = "";
            if (i == 0) {
                label = "Username:";
                val = username;
            } else if (i == 1) {
                label = "Server IP / Host:";
                val = ip;
            } else {
                label = "Port:";
                val = port;
            }

            int fieldY = startY + i * 25;
            drawString(this.fontRenderer, label, baseX, fieldY + 4, 0x808080);

            // Draw field box
            int boxX = baseX + 80;
            int boxY = fieldY;
            int boxW = 120;
            int boxH = 16;

            boolean isFocused = (focusedField == i);
            int borderColor = isFocused ? 0xFFFFFF00 : 0xFF808080; // Yellow for focused, gray otherwise
            int bgColor = 0xFF000000;

            drawBox(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, borderColor);
            drawBox(boxX, boxY, boxX + boxW, boxY + boxH, bgColor);

            String cursor = (isFocused && (this.counter / 6 % 2 == 0) ? "_" : "");
            drawString(this.fontRenderer, val + cursor, boxX + 4, boxY + 4, 0xE0E0E0);
        }

        super.render(mx, my);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        super.onMouseClick(mx, my, button);

        if (button == 0) { // Left click
            int baseX = this.width / 2 - 100;
            int startY = 45;

            for (int i = 0; i < 3; i++) {
                int fieldY = startY + i * 25;
                int boxX = baseX + 80;
                int boxY = fieldY;
                int boxW = 120;
                int boxH = 16;

                if (mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) {
                    focusedField = i;
                    break;
                }
            }
        }
    }
}
