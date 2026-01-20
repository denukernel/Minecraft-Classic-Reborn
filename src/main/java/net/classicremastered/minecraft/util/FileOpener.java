package net.classicremastered.minecraft.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public final class FileOpener {
    private FileOpener() {}

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
