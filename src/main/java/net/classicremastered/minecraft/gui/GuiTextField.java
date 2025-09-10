package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;

public class GuiTextField {
    private final FontRenderer fontRenderer;
    private final int xPos, yPos, width, height;
    private String text = "";
    private int maxLength = 64;
    private int cursorPos = 0;
    private int cursorCounter = 0;
    private boolean focused = false;

    public GuiTextField(FontRenderer fontRenderer, int x, int y, int width, int height) {
        this.fontRenderer = fontRenderer;
        this.xPos = x;
        this.yPos = y;
        this.width = width;
        this.height = height;
    }

    public void setText(String text) {
        if (text == null) text = "";
        this.text = filterNumeric(text);
        if (this.text.length() > maxLength) this.text = this.text.substring(0, maxLength);
        this.cursorPos = this.text.length();
    }

    public String getText() {
        return this.text;
    }

    public void setMaxLength(int len) {
        this.maxLength = len;
    }

    public void setFocused(boolean focus) {
        this.focused = focus;
    }

    public void updateCursorCounter() {
        this.cursorCounter++;
    }

    public void drawTextBox() {
        GuiScreen.drawBox(xPos - 1, yPos - 1, xPos + width + 1, yPos + height + 1, 0xFF000000);
        GuiScreen.drawBox(xPos, yPos, xPos + width, yPos + height, 0xFF202020);

        String shown = this.text;
        int textWidth = fontRenderer.getWidth(shown);
        while (textWidth > (width - 6) && shown.length() > 1) {
            shown = shown.substring(1);
            textWidth = fontRenderer.getWidth(shown);
        }

        fontRenderer.render(shown, xPos + 4, yPos + (height - 8) / 2, 0xE0E0E0);

        if (this.focused && (this.cursorCounter / 6) % 2 == 0) {
            int cx = xPos + 4 + fontRenderer.getWidth(shown.substring(0, cursorPos));
            GuiScreen.drawBox(cx, yPos + 2, cx + 1, yPos + height - 2, 0xFFD0D0D0);
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0) {
            boolean inside = mx >= xPos && mx < xPos + width && my >= yPos && my < yPos + height;
            setFocused(inside);
            return inside;
        }
        return false;
    }

    public boolean textboxKeyTyped(char c, int key) {
        if (!focused) return false;

        if (key == Keyboard.KEY_BACK && text.length() > 0 && cursorPos > 0) {
            text = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
            cursorPos--;
            return true;
        }

        if (key == Keyboard.KEY_DELETE && cursorPos < text.length()) {
            text = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
            return true;
        }

        if (key == Keyboard.KEY_LEFT && cursorPos > 0) {
            cursorPos--;
            return true;
        }

        if (key == Keyboard.KEY_RIGHT && cursorPos < text.length()) {
            cursorPos++;
            return true;
        }

        // Numeric-only input
        if ((c >= '0' && c <= '9') || (c == '-' && cursorPos == 0 && !text.contains("-"))) {
            if (text.length() < maxLength) {
                text = text.substring(0, cursorPos) + c + text.substring(cursorPos);
                cursorPos++;
            }
            return true;
        }

        return false;
    }

    /** Ensure only digits and one leading minus survive */
    private String filterNumeric(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                out.append(ch);
            } else if (ch == '-' && i == 0 && out.indexOf("-") == -1) {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
