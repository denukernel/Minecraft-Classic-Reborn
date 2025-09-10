package net.classicremastered.minecraft.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;

/**
 * Simple single-line numeric text input GUI (safe for menus).
 * Used for seed entry – only allows digits and one leading minus sign.
 */
public class TextInputScreen extends GuiScreen {

    private String message = "";
    private int counter = 0;

    private int caret = 0;
    private int selAnchor = -1;
    private boolean dragging = false;

    /** Callback handler */
    public interface CloseHandler {
        void onClosed(String text);
    }
    private CloseHandler closeHandler;

    public void setCloseHandler(CloseHandler handler) {
        this.closeHandler = handler;
    }

    public TextInputScreen() {}
    public TextInputScreen(String defaultText) {
        setText(defaultText);
    }

    @Override
    public void onOpen() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
        dragging = false;
        selAnchor = -1;
        if (closeHandler != null) {
            closeHandler.onClosed(message.trim());
        }
    }

    @Override
    public void tick() {
        ++counter;
        if (dragging && Mouse.isButtonDown(0)) {
            int mx = Mouse.getX() * this.width / this.minecraft.width;
            setCaretFromMouse(mx, true);
        } else {
            dragging = false;
        }
    }

    @Override
    protected void onKeyPress(char c, int key) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        clampCarets();

        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.setCurrentScreen(null);
            return;
        }

        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.minecraft.setCurrentScreen(null); // will trigger onClose()
            return;
        }

        // Clipboard
        if (ctrlDown && key == Keyboard.KEY_A) { selAnchor = 0; caret = message.length(); return; }
        if (ctrlDown && key == Keyboard.KEY_C) { String sel = getSelectedText(); if (!sel.isEmpty()) setClipboard(sel); return; }
        if (ctrlDown && key == Keyboard.KEY_X) { if (hasSelection()) { setClipboard(getSelectedText()); deleteSelection(); } return; }
        if (ctrlDown && key == Keyboard.KEY_V) { String clip = getClipboard(); if (!clip.isEmpty()) insertText(filterAllowed(clip)); return; }

        // Navigation
        if (key == Keyboard.KEY_LEFT)  { moveCaretLeft(ctrlDown, shiftDown); return; }
        if (key == Keyboard.KEY_RIGHT) { moveCaretRight(ctrlDown, shiftDown); return; }
        if (key == Keyboard.KEY_HOME)  { setCaret(0, shiftDown); return; }
        if (key == Keyboard.KEY_END)   { setCaret(message.length(), shiftDown); return; }

        // Editing
        if (key == Keyboard.KEY_BACK) {
            if (hasSelection()) deleteSelection();
            else if (caret > 0) { message = message.substring(0, caret - 1) + message.substring(caret); caret--; }
            return;
        }
        if (key == Keyboard.KEY_DELETE) {
            if (hasSelection()) deleteSelection();
            else if (caret < message.length()) { message = message.substring(0, caret) + message.substring(caret + 1); }
            return;
        }

        // Text input (digits and one leading minus only)
        if ((c >= '0' && c <= '9') || (c == '-' && caret == 0 && !message.contains("-"))) {
            insertText(String.valueOf(c));
        }
    }

    @Override
    public void render(int mouseX, int mouseY) {
        clampCarets();
        drawBox(2, height - 14, width - 2, height - 2, Integer.MIN_VALUE);

        final String prefix = "> ";
        final int baseX = 4, baseY = height - 12;
        final int len = message.length();

        if (hasSelection() && len > 0) {
            int a = Math.min(caret, selAnchor);
            int b = Math.max(caret, selAnchor);
            String left = message.substring(0, a);
            String mid  = message.substring(a, b);
            int x0 = baseX + fontRenderer.getWidth(prefix) + fontRenderer.getWidth(left);
            int x1 = x0 + fontRenderer.getWidth(mid);
            drawBox(x0, baseY - 2, x1, baseY + 10, 0x80FFFFFF);
        }

        String before = (len == 0) ? "" : message.substring(0, caret);
        String after  = (len == 0) ? "" : message.substring(caret);
        String caretGlyph = (counter / 6 % 2 == 0 ? "_" : " ");
        drawString(fontRenderer, prefix + before + caretGlyph + after, baseX, baseY, 0xE0E0E0);
    }

    // ==== Helpers ====
    private void clampCarets() {
        int len = message.length();
        if (caret < 0) caret = 0;
        if (caret > len) caret = len;
        if (selAnchor < -1) selAnchor = -1;
        if (selAnchor > len) selAnchor = len;
    }

    private boolean hasSelection() { return selAnchor >= 0 && selAnchor != caret; }

    private String getSelectedText() {
        if (!hasSelection()) return "";
        int a = Math.min(caret, selAnchor), b = Math.max(caret, selAnchor);
        return message.substring(a, b);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int a = Math.min(caret, selAnchor), b = Math.max(caret, selAnchor);
        message = message.substring(0, a) + message.substring(b);
        caret = a;
        selAnchor = -1;
    }

    private void insertText(String txt) {
        if (txt == null || txt.isEmpty()) return;
        if (hasSelection()) deleteSelection();
        message = message.substring(0, caret) + txt + message.substring(caret);
        caret += txt.length();
    }

    private void setCaret(int pos, boolean extend) {
        int len = message.length();
        if (pos < 0) pos = 0;
        if (pos > len) pos = len;
        if (extend) {
            if (selAnchor < 0) selAnchor = caret;
        } else {
            selAnchor = -1;
        }
        caret = pos;
    }

    private void moveCaretLeft(boolean word, boolean extend) {
        int i = caret;
        if (!word) i--;
        else {
            while (i > 0 && message.charAt(i - 1) == ' ') i--;
            while (i > 0 && message.charAt(i - 1) != ' ') i--;
        }
        setCaret(i, extend);
    }

    private void moveCaretRight(boolean word, boolean extend) {
        int i = caret;
        if (!word) i++;
        else {
            while (i < message.length() && message.charAt(i) == ' ') i++;
            while (i < message.length() && message.charAt(i) != ' ') i++;
        }
        setCaret(i, extend);
    }

    private void setCaretFromMouse(int mx, boolean extend) {
        int baseX = 4;
        String prefix = "> ";
        int px = baseX + fontRenderer.getWidth(prefix);

        int best = 0, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= message.length(); i++) {
            int xi = px + fontRenderer.getWidth(message.substring(0, i));
            int d  = Math.abs(xi - mx);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        setCaret(best, extend);
    }

    private String filterAllowed(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= '0' && ch <= '9') || (ch == '-' && i == 0 && !s.substring(1).contains("-"))) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    // === Clipboard ===
    private static void setClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }

    private static String getClipboard() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return (String) cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {}
        return "";
    }

    // === Prefill ===
    public void setText(String text) {
        if (text == null) text = "";
        this.message = filterAllowed(text);
        this.caret = message.length();
        this.selAnchor = -1;
    }

    public String getText() {
        return message;
    }
}
