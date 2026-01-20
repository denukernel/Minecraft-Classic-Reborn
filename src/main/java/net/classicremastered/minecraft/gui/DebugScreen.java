package net.classicremastered.minecraft.gui;

import java.io.File;

import net.classicremastered.minecraft.Minecraft;

public final class DebugScreen extends GuiScreen {

    private static final int ID_CLEAR_LOGS = 0;
    private static final int ID_CLEAR_SHOTS = 1;
    private static final int ID_BACK = 2;

    @Override
    public void onOpen() {
        this.buttons.clear();
        int x = this.width / 2 - 100;
        int y = this.height / 4;

        this.buttons.add(new Button(ID_CLEAR_LOGS, x, y + 0, "Clear Crash Logs"));
        this.buttons.add(new Button(ID_CLEAR_SHOTS, x, y + 24, "Clear Screenshots"));
        this.buttons.add(new Button(ID_BACK, x, y + 60, "Back"));
    }

    @Override
    protected void onButtonClick(Button b) {
        switch (b.id) {
        case ID_CLEAR_LOGS:
            File logFile = new File(Minecraft.mcDir, "crash_log.txt");
            if (logFile.exists()) {
                if (logFile.delete()) {
                    println("Crash logs cleared successfully.");
                } else {
                    println("Failed to delete crash logs.");
                }
            } else {
                println("No crash log found to delete.");
            }
            return;

        case ID_CLEAR_SHOTS:
            File shotDir = new File(Minecraft.mcDir, "screenshots");
            if (shotDir.exists() && shotDir.isDirectory()) {
                int deleted = 0;
                for (File f : shotDir.listFiles()) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                        if (f.delete()) deleted++;
                    }
                }
                println("Deleted " + deleted + " screenshots.");
            } else {
                println("No screenshots folder found.");
            }
            return;

        case ID_BACK:
            this.minecraft.setCurrentScreen(new MainMenuScreen());
            return;
        }
    }

    private void println(String msg) {
        // Show in HUD if available, and always print to console.
        if (this.minecraft != null && this.minecraft.hud != null) {
            this.minecraft.hud.addChat(msg);
        }
        System.out.println("[DebugScreen] " + msg.replace('&', '§'));
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x80000000, 0xA0000000);
        drawCenteredString(this.fontRenderer, "Debug Utilities", this.width / 2, 40, 0xFFFFFF);
        super.render(mx, my);
    }
}
