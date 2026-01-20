package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.errors.CrashReportException;
import net.classicremastered.minecraft.util.Screenshot;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

public final class ErrorScreen extends GuiScreen {

    private String title;
    private String text;
    private boolean screenshotTaken = false;
    private final String githubUrl = "https://github.com/denukernel/Minecraft-Classic-Reborn/issues";

    public ErrorScreen(String title, String text) {
        this.title = title;
        this.text = text;
    }

    public ErrorScreen(String title, Throwable error) {
        this.title = title;
        Throwable cause = (error instanceof CrashReportException && error.getCause() != null)
                ? error.getCause()
                : error;

        String message = cause.getClass().getName();
        if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            message += ": " + cause.getMessage();
        }
        this.text = message;
    }

    public final void onOpen() {
        if (!screenshotTaken) {
            try {
                Screenshot.take(Minecraft.mcDir);
                System.out.println("[ErrorScreen] Screenshot captured for debugging.");
            } catch (Throwable t) {
                System.err.println("[ErrorScreen] Screenshot failed: " + t);
            }
            screenshotTaken = true;
        }
    }

    private String[] wrapText(String text, int maxLen) {
        if (text == null) return new String[]{"(no details)"};
        text = text.replace("\r", "").replace("\n", " ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        while (text.length() > maxLen) {
            int space = text.lastIndexOf(' ', maxLen);
            if (space <= 0) space = maxLen;
            lines.add(text.substring(0, space));
            text = text.substring(space).trim();
        }
        if (!text.isEmpty()) lines.add(text);
        return lines.toArray(new String[0]);
    }

    private String getCrashPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "%APPDATA%\\.mcraft\\client\\crash_screenshots";
        } else if (os.contains("mac")) {
            return "~/Library/Application Support/.mcraft/client/crash_screenshots";
        } else {
            return "~/.mcraft/client/crash_screenshots";
        }
    }

    private String getLogPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "%APPDATA%\\.mcraft\\client\\crash_log.txt";
        } else if (os.contains("mac")) {
            return "~/Library/Application Support/.mcraft/client/crash_log.txt";
        } else {
            return "~/.mcraft/client/crash_log.txt";
        }
    }

    public final void render(int mouseX, int mouseY) {
        drawFadingBox(0, 0, this.width, this.height, -12574688, -11530224);

        GL11.glPushMatrix();
        GL11.glScalef(0.9F, 0.9F, 1.0F);
        drawCenteredString(this.fontRenderer, this.title,
            (int)(this.width / 1.8F), (int)(95 / 0.9F), 0xFFFFFF);
        GL11.glPopMatrix();

        // Exception text
        String[] lines = wrapText(this.text, 60);
        int y = 110;
        for (String line : lines) {
            drawCenteredString(this.fontRenderer, line, this.width / 2, y, 0xFFFFFF);
            y += 10;
        }

        y += 8;
        drawCenteredString(this.fontRenderer,
            "A screenshot was saved automatically for debugging.",
            this.width / 2, y, 0xAAAAAA);
        y += 10;
        drawCenteredString(this.fontRenderer,
            "(Read the advice below)",
            this.width / 2, y, 0x55FFFF);

        y += 18;
        drawCenteredString(this.fontRenderer,
            "If this issue keeps happening, please report it on GitHub issues.",
            this.width / 2, y, 0xAAAAAA);
        y += 10;
        drawCenteredString(this.fontRenderer,
            "Include these files when reporting the bug:",
            this.width / 2, y, 0xAAAAAA);

        y += 10;
        drawCenteredString(this.fontRenderer, getCrashPath(), this.width / 2, y, 0x55FFFF);
        y += 10;
        drawCenteredString(this.fontRenderer, getLogPath(), this.width / 2, y, 0x55FFFF);

        y += 18;
        drawCenteredString(this.fontRenderer, "Developer: denukernel",
            this.width / 2, y, 0x777777);
        y += 10;

        // --- Clickable GitHub link ---
        int linkY = y;
        String githubText = "GitHub: " + githubUrl;
        int textWidth = this.fontRenderer.getWidth(githubText);
        int textX = (this.width / 2) - (textWidth / 2);
        int color = isMouseOverLink(mouseX, mouseY, textX, linkY, textWidth, 10) ? 0x66FFFF : 0x777777;

        drawCenteredString(this.fontRenderer, githubText, this.width / 2, linkY, color);

        GL11.glColor4f(1, 1, 1, 1);
        super.render(mouseX, mouseY);
    }

    private boolean isMouseOverLink(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void mouseEvent() {
        if (!Mouse.isCreated()) return;

        while (Mouse.next()) {
            if (Mouse.getEventButtonState()) {
                int mouseX = Mouse.getEventX() * this.width / Display.getWidth();
                int mouseY = this.height - Mouse.getEventY() * this.height / Display.getHeight() - 1;

                // Get link bounds dynamically
                String githubText = "GitHub: " + githubUrl;
                int textWidth = this.fontRenderer.getWidth(githubText);
                int textX = (this.width / 2) - (textWidth / 2);
                int linkY = 267; // same Y position used in render()

                if (isMouseOverLink(mouseX, mouseY, textX, linkY, textWidth, 10)) {
                    openLinkInBrowser(githubUrl);
                }
            }
        }
    }


    private void openLinkInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
            }
            System.out.println("[ErrorScreen] Opened " + url);
        } catch (Exception e) {
            System.err.println("[ErrorScreen] Failed to open link: " + e);
        }
    }
}
