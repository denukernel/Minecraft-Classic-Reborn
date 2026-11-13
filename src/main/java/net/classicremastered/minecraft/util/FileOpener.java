package net.classicremastered.minecraft.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public final class FileOpener {
    private FileOpener() {}

    public static boolean openImage(File file) {
        if (file == null || !file.isFile()) return false;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
                return true;
            }
        } catch (IOException | UnsupportedOperationException exception) {
            exception.printStackTrace();
        }
        return false;
    }

    public static boolean editImage(File file) {
        if (file == null || !file.isFile()) return false;
        try {
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                new ProcessBuilder("mspaint.exe", file.getAbsolutePath()).start();
                return true;
            }
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(file);
                return true;
            }
        } catch (IOException | UnsupportedOperationException exception) {
            exception.printStackTrace();
        }
        return false;
    }

    public static boolean openDirectory(File dir) {
        if (dir == null || !dir.exists()) return false;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
