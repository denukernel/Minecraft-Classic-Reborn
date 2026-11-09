package net.classicremastered.minecraft.gui;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import net.classicremastered.minecraft.level.Level;

/** Local file loading replaces the retired Minecraft Classic online map service. */
public class LoadLevelScreen extends GuiScreen implements Runnable {
    protected GuiScreen parent;
    protected String title = "Load level";
    volatile boolean frozen;
    volatile JFileChooser chooser;
    protected boolean saving;
    protected volatile File selectedFile;
    private volatile Object dialogRequest;

    public LoadLevelScreen(GuiScreen parent) { super(parent); this.parent = parent; }

    /** Kept for callers of the old screen API; no remote map lookup is needed. */
    @Override public void run() {}

    @Override public void onOpen() {
        cancelDialog();
        buttons.clear();
        int y = height / 2 - 10;
        if (!saving) buttons.add(new Button(7, width / 2 - 100, y - 24, "Saved levels..."));
        buttons.add(new Button(5, width / 2 - 100, y, saving ? "Save file..." : "Load file..."));
        buttons.add(new Button(6, width / 2 - 100, y + 36, "Cancel"));
    }

    @Override protected final void onButtonClick(Button button) {
        if (frozen || !button.active) return;
        if (button.id == 5) {
            frozen = true;
            Object request = new Object();
            dialogRequest = request;
            SwingUtilities.invokeLater(new LevelDialog(this, request));
        } else if (button.id == 6) {
            minecraft.setCurrentScreen(parent);
        } else if (button.id == 7 && !saving) {
            minecraft.setCurrentScreen(new SelectLevelScreen(this));
        }
    }

    protected void openLevel(File file) {
        Level level = minecraft.levelIo.load(file);
        if (level != null) {
            minecraft.setLevel(level);
            minecraft.setCurrentScreen(null);
            minecraft.grabMouse();
        } else {
            minecraft.setCurrentScreen(new ErrorScreen("Failed to load", "Corrupt or unreadable level file.", this));
        }
    }

    @Override public void render(int mouseX, int mouseY) {
        if (minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, width, height, 1610941696, -1607454624);
        drawCenteredString(fontRenderer, title, width / 2, 20, 16777215);
        drawCenteredString(fontRenderer, frozen ? "Selecting file..." :
                (saving ? "Save a level file on this computer." : "Choose a saved level or a local level file."),
                width / 2, height / 2 - 48, 16777215);
        if (!frozen) super.render(mouseX, mouseY);
    }

    @Override public final void onClose() {
        super.onClose();
        cancelDialog();
    }

    private synchronized void cancelDialog() {
        dialogRequest = null;
        selectedFile = null;
        frozen = false;
        JFileChooser pending = chooser;
        chooser = null;
        if (pending != null) SwingUtilities.invokeLater(pending::cancelSelection);
    }

    synchronized boolean attachDialog(Object request, JFileChooser dialog) {
        if (dialogRequest != request) return false;
        chooser = dialog;
        return true;
    }

    synchronized void finishDialog(Object request, File file) {
        if (dialogRequest != request) return;
        selectedFile = file;
        chooser = null;
        dialogRequest = null;
        frozen = false;
    }

    @Override public final void tick() {
        super.tick();
        if (minecraft.currentScreen != this) return;
        File file = selectedFile;
        if (file != null) {
            selectedFile = null;
            openLevel(file);
        }
    }
}
