package net.classicremastered.minecraft.gui;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Runs on the Swing event thread; stale results are discarded by the screen. */
final class LevelDialog implements Runnable {
    private final LoadLevelScreen screen;
    private final Object request;

    LevelDialog(LoadLevelScreen screen, Object request) {
        this.screen = screen;
        this.request = request;
    }

    @Override
    public void run() {
        File selected = null;
        try {
            JFileChooser dialog = new JFileChooser();
            if (!screen.attachDialog(request, dialog)) return;
            dialog.setFileFilter(new FileNameExtensionFilter("Minecraft levels", "mine", "lvl", "gz"));
            dialog.setMultiSelectionEnabled(false);
            int result = screen.saving ? dialog.showSaveDialog(screen.minecraft.canvas)
                    : dialog.showOpenDialog(screen.minecraft.canvas);
            if (result == JFileChooser.APPROVE_OPTION) selected = dialog.getSelectedFile();
        } finally {
            screen.finishDialog(request, selected);
        }
    }
}
