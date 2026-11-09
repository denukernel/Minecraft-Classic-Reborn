package net.classicremastered.minecraft.gui;

import java.io.File;
import java.util.Locale;

/** Local saves do not require an account or the retired premium map service. */
public final class SaveLevelScreen extends LoadLevelScreen {
    public SaveLevelScreen(GuiScreen parent) {
        super(parent);
        title = "Save level";
        saving = true;
    }

    @Override protected void openLevel(File file) {
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".mine")) {
            file = new File(file.getParentFile(), file.getName() + ".mine");
        }
        if (minecraft.levelIo.save(minecraft.level, file)) minecraft.setCurrentScreen(parent);
        else minecraft.setCurrentScreen(new ErrorScreen("Failed to save", "Could not save the level file.", this));
    }
}
