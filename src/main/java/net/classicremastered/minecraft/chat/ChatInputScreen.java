package net.classicremastered.minecraft.chat;

import net.classicremastered.minecraft.chat.commands.CommandManager;
import net.classicremastered.minecraft.gui.GuiScreen;
import net.classicremastered.minecraft.net.PacketType;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;
import java.util.List;

public class ChatInputScreen extends GuiScreen {

    protected String message = "";
    private int counter = 0;
    private java.util.List<
                    net.classicremastered.minecraft.chat.commands.CommandSuggestions.Suggestion>
            suggestions = java.util.List.of();
    private String suggestionInput = "";
    private int suggestionCaret = -1, selectedSuggestion = 0, suggestionScroll = 0;
    private boolean suggestionsDismissed;

    private void refreshSuggestions() {
        if (closeHandler != null) {
            suggestions = java.util.List.of();
            return;
        }
        if (message.equals(suggestionInput) && caret == suggestionCaret) return;
        suggestionInput = message;
        suggestionCaret = caret;
        suggestionsDismissed = false;
        selectedSuggestion = 0;
        suggestionScroll = 0;
        CommandManager.ensureBootstrapped(minecraft);
        suggestions =
                net.classicremastered.minecraft.chat.commands.CommandSuggestions.complete(
                        minecraft, minecraft.player, message, caret);
    }

    private boolean suggestionPanel() {
        return !suggestionsDismissed && !suggestions.isEmpty() && closeHandler == null;
    }

    private void selectSuggestion(int delta) {
        selectedSuggestion = Math.floorMod(selectedSuggestion + delta, suggestions.size());
        suggestionScroll =
                Math.max(
                        0,
                        Math.min(
                                selectedSuggestion,
                                Math.max(suggestionScroll, selectedSuggestion - 7)));
    }

    private void acceptSuggestion() {
        var s = suggestions.get(selectedSuggestion);
        String suffix = message.substring(s.end());
        String replacement = s.text() + (suffix.isEmpty() && !s.text().endsWith("=") ? " " : "");
        if (message.length() - (s.end() - s.start()) + replacement.length() > maxLen()) return;
        message = message.substring(0, s.start()) + replacement + suffix;
        caret = s.start() + replacement.length();
        selAnchor = -1;
        refreshSuggestions();
    }

    private int panelTop() {
        return height - 28 - Math.min(8, suggestions.size()) * 12;
    }

    private int panelWidth() {
        return Math.max(1, Math.min(width - 8, 320));
    }

    private String fitSuggestion(String text) {
        text = text.replace('\n', ' ').replace('\r', ' ');
        while (!text.isEmpty() && fontRenderer.getWidth(text) > panelWidth() - 8)
            text = text.substring(0, text.length() - 1);
        return text;
    }

    private int caret = 0;
    private int selAnchor = -1;
    private boolean dragging = false;

    // Chat history
    private static final List<String> history = new ArrayList<>();
    private int historyIndex = -1; // -1 = no history selected

    // Limits
    private static final int MAX_CHAT_LEN = 256;
    private static final int HISTORY_LIMIT = 100;

    private static final String ALLOWED =
            " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789,.:-_'*!\"#%/()=+?[]{}<>@|$;";

    // Optional callback for seed input etc.
    public interface CloseHandler {
        void onClosed(String text);
    }

    private CloseHandler closeHandler;

    public void setCloseHandler(CloseHandler handler) {
        this.closeHandler = handler;
    }

    @Override
    public void onOpen() {
        Keyboard.enableRepeatEvents(true);
        historyIndex = -1;
    }

    @Override
    protected void onResize() {
        // Text and history state belong to the current chat session.
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
        dragging = false;
        selAnchor = -1;
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
        clampCarets();

        refreshSuggestions();
        if (key == Keyboard.KEY_ESCAPE && suggestionPanel()) {
            suggestionsDismissed = true;
            return;
        }
        if (suggestionPanel()) {
            if (key == Keyboard.KEY_UP) {
                selectSuggestion(-1);
                return;
            }
            if (key == Keyboard.KEY_DOWN) {
                selectSuggestion(1);
                return;
            }
            if (key == Keyboard.KEY_TAB) {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) selectSuggestion(-1);
                else acceptSuggestion();
                return;
            }
        }
        // Exit
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.setCurrentScreen(null);
            return;
        }

        // Submit
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            String msg = message.trim();
            if (closeHandler != null) {
                this.minecraft.setCurrentScreen(null);
                closeHandler.onClosed(msg);
                return;
            }
            if (!msg.isEmpty()) {
                addHistory(msg);

                if (msg.startsWith("/")) {
                    if (minecraft != null && minecraft.player != null) {
                        CommandManager.handleCommand(minecraft, minecraft.player, msg);
                    } else if (minecraft != null && minecraft.hud != null) {
                        minecraft.hud.addChat("&c(No player available for command)");
                    }
                } else {
                    String prefix;
                    if (minecraft != null && minecraft.developer) {
                        prefix = "&bdeveloper: ";
                    } else {
                        prefix = getUsernameSafe() + ": ";
                    }

                    if (minecraft != null
                            && minecraft.networkManager != null
                            && minecraft.networkManager.netHandler != null
                            && minecraft.player != null) {
                        // Send to server only if player exists
                        minecraft.networkManager.netHandler.send(
                                PacketType.CHAT_MESSAGE, new Object[] {-1, msg});
                    } else if (minecraft != null && minecraft.hud != null) {
                        minecraft.hud.addChat(prefix + msg);
                    }
                }
            }
            this.minecraft.setCurrentScreen(null);
            return;
        }

        boolean ctrlDown =
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                        || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shiftDown =
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        // History browse
        if (key == Keyboard.KEY_UP) {
            if (!history.isEmpty()) {
                if (historyIndex == -1) historyIndex = history.size();
                if (historyIndex > 0) historyIndex--;
                setMessage(history.get(historyIndex));
            }
            return;
        }
        if (key == Keyboard.KEY_DOWN) {
            if (historyIndex != -1) {
                historyIndex++;
                if (historyIndex >= history.size()) {
                    historyIndex = -1;
                    setMessage("");
                } else {
                    setMessage(history.get(historyIndex));
                }
            }
            return;
        }

        // Clipboard
        if (key == Keyboard.KEY_A && ctrlDown) {
            selAnchor = 0;
            caret = message.length();
            return;
        }
        if (key == Keyboard.KEY_C && ctrlDown) {
            String sel = getSelectedText();
            if (!sel.isEmpty()) setClipboard(sel);
            return;
        }
        if (key == Keyboard.KEY_X && ctrlDown) {
            if (hasSelection()) {
                setClipboard(getSelectedText());
                deleteSelection();
            }
            return;
        }
        if (key == Keyboard.KEY_V && ctrlDown) {
            String clip = getClipboard();
            if (!clip.isEmpty()) insertText(filterAllowed(clip));
            return;
        }

        // Navigation
        if (key == Keyboard.KEY_LEFT) {
            moveCaretLeft(ctrlDown, shiftDown);
            return;
        }
        if (key == Keyboard.KEY_RIGHT) {
            moveCaretRight(ctrlDown, shiftDown);
            return;
        }
        if (key == Keyboard.KEY_HOME) {
            setCaret(0, shiftDown);
            return;
        }
        if (key == Keyboard.KEY_END) {
            setCaret(message.length(), shiftDown);
            return;
        }

        // Editing
        if (key == Keyboard.KEY_BACK) {
            if (hasSelection()) deleteSelection();
            else if (caret > 0) {
                message = message.substring(0, caret - 1) + message.substring(caret);
                caret--;
            }
            return;
        }
        if (key == Keyboard.KEY_DELETE) {
            if (hasSelection()) deleteSelection();
            else if (caret < message.length()) {
                message = message.substring(0, caret) + message.substring(caret + 1);
            }
            return;
        }

        // Text input
        if (ALLOWED.indexOf(c) >= 0) insertText(String.valueOf(c));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        clampCarets();
        refreshSuggestions();
        if (suggestionPanel()) {
            int top = panelTop(), rows = Math.min(8, suggestions.size());
            drawBox(4, top, 4 + panelWidth(), top + rows * 12, 0xE0202020);
            for (int row = 0; row < rows; row++) {
                int index = suggestionScroll + row;
                if (index >= suggestions.size()) break;
                if (index == selectedSuggestion)
                    drawBox(4, top + row * 12, 4 + panelWidth(), top + row * 12 + 12, 0xFF505060);
                drawString(
                        fontRenderer,
                        fitSuggestion(suggestions.get(index).text()),
                        8,
                        top + row * 12 + 2,
                        index == selectedSuggestion ? 0xFFFF80 : 0xFFFFFF);
            }
        }
        if (closeHandler == null && message.startsWith("/")) {
            String usage =
                    net.classicremastered.minecraft.chat.commands.CommandSuggestions.usage(message);
            if (suggestionPanel()) usage = suggestions.get(selectedSuggestion).usage();
            if (!usage.isEmpty())
                drawString(fontRenderer, fitSuggestion(usage), 4, height - 25, 0xAAAAAA);
        }
        drawBox(2, height - 14, width - 2, height - 2, Integer.MIN_VALUE);

        final String prefix = "> ";
        final int baseX = 4, baseY = height - 12;
        final int len = (message == null) ? 0 : message.length();

        if (hasSelection() && len > 0) {
            int a = Math.min(caret, selAnchor);
            int b = Math.max(caret, selAnchor);
            String left = message.substring(0, a);
            String mid = message.substring(a, b);
            int x0 = baseX + fontRenderer.getWidth(prefix) + fontRenderer.getWidth(left);
            int x1 = x0 + fontRenderer.getWidth(mid);
            drawBox(x0, baseY - 2, x1, baseY + 10, 0x80FFFFFF);
        }

        String before = (len == 0) ? "" : message.substring(0, caret);
        String after = (len == 0) ? "" : message.substring(caret);
        String caretGlyph = (counter / 6 % 2 == 0 ? "_" : " ");
        drawString(fontRenderer, prefix + before + caretGlyph + after, baseX, baseY, 0xE0E0E0);
    }

    // === helpers ===
    private void clampCarets() {
        int len = (message == null) ? 0 : message.length();
        if (caret < 0) caret = 0;
        if (caret > len) caret = len;
        if (selAnchor < -1) selAnchor = -1;
        if (selAnchor > len) selAnchor = len;
    }

    private boolean hasSelection() {
        return selAnchor >= 0 && selAnchor != caret;
    }

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

    public void insertText(String txt) {
        if (txt == null || txt.isEmpty()) return;
        if (hasSelection()) deleteSelection();
        int room = maxLen() - message.length();
        if (room <= 0) return;
        if (txt.length() > room) txt = txt.substring(0, room);
        message = message.substring(0, caret) + txt + message.substring(caret);
        caret += txt.length();
    }

    private int maxLen() {
        int max = MAX_CHAT_LEN;
        max -= (getUsernameSafe().length() + 2);
        return Math.max(0, max);
    }

    private void setCaret(int pos, boolean extend) {
        int len = (message == null) ? 0 : message.length();
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
            int d = Math.abs(xi - mx);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        setCaret(best, extend);
    }

    private String filterAllowed(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ALLOWED.indexOf(ch) >= 0) out.append(ch);
        }
        return out.toString();
    }

    private static void setClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (Exception ignored) {
        }
    }

    private static String getClipboard() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) cb.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @Override
    protected void onMouseClick(int x, int y, int button) {
        if (minecraft.hud != null && minecraft.hud.onChatClick(x, y, button)) return;
        refreshSuggestions();
        if (button == 0
                && suggestionPanel()
                && x >= 4
                && x < 4 + panelWidth()
                && y >= panelTop()
                && y < panelTop() + Math.min(8, suggestions.size()) * 12) {
            selectedSuggestion = suggestionScroll + (y - panelTop()) / 12;
            if (selectedSuggestion < suggestions.size()) acceptSuggestion();
            return;
        }
        if (button == 0) {
            boolean shift =
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (!shift) {
                selAnchor = caret;
                setCaretFromMouse(x, false);
            } else {
                setCaretFromMouse(x, true);
            }
            dragging = true;
        }
        if (button == 1) {
            String clip = getClipboard();
            if (!clip.isEmpty()) insertText(filterAllowed(clip));
        }
    }

    private void addHistory(String msg) {
        if (msg == null || msg.isEmpty()) return;
        if (!history.isEmpty() && history.get(history.size() - 1).equals(msg)) return;

        history.add(msg);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    public void setText(String text) {
        if (text == null) text = "";
        this.message = text;
        this.caret = message.length();
        this.selAnchor = -1;
    }

    private String getUsernameSafe() {
        if (minecraft != null && minecraft.session != null && minecraft.session.username != null) {
            return minecraft.session.username;
        }
        return "unknown";
    }

    private void setMessage(String newMsg) {
        this.message = newMsg;
        this.caret = message.length();
        this.selAnchor = -1;
    }
}
