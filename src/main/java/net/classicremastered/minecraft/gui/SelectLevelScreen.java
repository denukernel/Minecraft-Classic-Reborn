package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.WorldStorage;
import net.classicremastered.minecraft.level.dimensions.StickmansHouse;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

public final class SelectLevelScreen extends GuiScreen {
    private static final int LOAD = 10, CREATE = 11, BACK = 12, DELETE = 13, RENAME = 14;
    private static final int PREVIOUS = 15, NEXT = 16, SELECT_ALL = 17, ROW = 100;
    private static final int ROWS = 5;

    private final GuiScreen parent;
    private final Set<File> selected = new LinkedHashSet<>();
    private final Map<File, Integer> previewTextures = new HashMap<>();
    private List<File> worlds = List.of();
    private int page;
    private boolean confirmDelete;

    public SelectLevelScreen(GuiScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        releasePreviews();
        worlds = WorldStorage.list(Minecraft.getMinecraftDir());
        selected.retainAll(worlds);
        page = Math.min(page, Math.max(0, (worlds.size() - 1) / ROWS));
        layout();
    }

    private void layout() {
        buttons.clear();
        int x = width / 2 - 130;
        int y = Math.max(38, height / 2 - 78);
        int start = page * ROWS;
        int end = Math.min(start + ROWS, worlds.size());
        SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (int i = start; i < end; i++) {
            File world = worlds.get(i);
            String label =
                    WorldStorage.displayName(world)
                            + "  "
                            + stamp.format(new Date(world.lastModified()));
            while (label.length() > 1 && fontRenderer.getWidth(label) > 202)
                label = label.substring(0, label.length() - 1);
            int row = i - start;
            buttons.add(new Button(ROW + row, x, y + row * 24, 260, 20, label));
        }
        int nav = y + ROWS * 24;
        add(PREVIOUS, x, nav, 128, "Previous", page > 0);
        add(NEXT, x + 132, nav, 128, "Next", end < worlds.size());
        add(LOAD, x, nav + 24, 62, "Play", selected.size() == 1);
        add(CREATE, x + 66, nav + 24, 62, "New", true);
        add(RENAME, x + 132, nav + 24, 62, "Rename", selected.size() == 1);
        add(
                DELETE,
                x + 198,
                nav + 24,
                62,
                "Delete",
                !selected.isEmpty() && !selected.contains(activeFile()));
        add(BACK, 6, 6, 60, "Back", true);
        add(
                SELECT_ALL,
                width - 96,
                6,
                90,
                selected.size() == worlds.size() && !worlds.isEmpty()
                        ? "Deselect All"
                        : "Select All",
                !worlds.isEmpty());
    }

    private void add(int id, int x, int y, int width, String label, boolean enabled) {
        Button button = new Button(id, x, y, width, 20, label);
        button.active = enabled;
        buttons.add(button);
    }

    private File activeFile() {
        Level home = StickmansHouse.homeLevel(minecraft.level);
        return home == null ? null : home.saveFile;
    }

    @Override
    protected void onButtonClick(Button button) {
        int id = button.id;
        if (id >= ROW && id < ROW + ROWS) {
            int index = page * ROWS + id - ROW;
            if (index < worlds.size()) {
                selected.clear();
                selected.add(worlds.get(index));
                confirmDelete = false;
                layout();
            }
            return;
        }
        if (id != DELETE) confirmDelete = false;
        switch (id) {
            case BACK -> minecraft.setCurrentScreen(parent);
            case PREVIOUS, NEXT -> {
                page += id == PREVIOUS ? -1 : 1;
                releasePreviews();
                layout();
            }
            case SELECT_ALL -> {
                boolean all = selected.size() == worlds.size();
                selected.clear();
                if (!all) selected.addAll(worlds);
                layout();
            }
            case CREATE -> minecraft.setCurrentScreen(new GenerateLevelScreen(this));
            case LOAD -> load(selected.iterator().next());
            case RENAME -> rename(selected.iterator().next());
            case DELETE -> deleteSelected();
            default -> {}
        }
    }

    private void load(File world) {
        if (!minecraft.saveCurrentLevel()) {
            error("Save failed", "The current world is still open.");
            return;
        }
        Level next = minecraft.levelIo.load(world);
        if (next == null) {
            error("Load failed", "The world file is unreadable.");
            return;
        }
        minecraft.setLevel(next);
        minecraft.setCurrentScreen(null);
        minecraft.grabMouse();
    }

    private void rename(File world) {
        minecraft.setCurrentScreen(
                new LevelNameScreen(
                        this,
                        WorldStorage.displayName(world),
                        name -> {
                            try {
                                WorldStorage.rename(world, name);
                                if (world.getAbsoluteFile().equals(activeFile()))
                                    StickmansHouse.homeLevel(minecraft.level).name = name;
                                minecraft.setCurrentScreen(this);
                            } catch (IOException failure) {
                                error("Rename failed", failure.getMessage());
                            }
                        }));
    }

    private void deleteSelected() {
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        for (File world : List.copyOf(selected)) {
            try {
                WorldStorage.delete(world);
            } catch (IOException failure) {
                error("Delete failed", failure.getMessage());
                return;
            }
        }
        confirmDelete = false;
        selected.clear();
        onOpen();
    }

    private void error(String title, String message) {
        minecraft.setCurrentScreen(new ErrorScreen(title, message, this));
    }

    @Override
    protected void onKeyPress(char c, int key) {
        if (key == 1 && confirmDelete) {
            confirmDelete = false;
            return;
        }
        super.onKeyPress(c, key);
    }

    @Override
    public void onClose() {
        confirmDelete = false;
        releasePreviews();
    }

    private void releasePreviews() {
        for (int texture : previewTextures.values()) {
            if (texture == 0) continue;
            GL11.glDeleteTextures(texture);
            minecraft.textureManager.textureImages.remove(texture);
        }
        previewTextures.clear();
    }

    @Override
    public void render(int mx, int my) {
        if (minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, width, height, 0x80202020, 0xFF000000);
        drawCenteredString(fontRenderer, "Select World", width / 2, 25, 0xFFFFFF);
        super.render(mx, my);
        for (Object item : buttons) {
            Button button = (Button) item;
            if (button.id < ROW || button.id >= ROW + ROWS) continue;
            File world = worlds.get(page * ROWS + button.id - ROW);
            if (selected.contains(world)) {
                drawBox(button.x, button.y - 1, button.x + button.width, button.y, 0xFF76A9EC);
                drawBox(
                        button.x,
                        button.y + 20,
                        button.x + button.width,
                        button.y + 21,
                        0xFF76A9EC);
            }
            drawPreview(world, button.x + 2, button.y + 1);
        }
        if (confirmDelete)
            drawCenteredString(
                    fontRenderer,
                    "Click Delete again to remove " + selected.size() + " selected world(s).",
                    width / 2,
                    height - 12,
                    0xFFFF7777);
    }

    private void drawPreview(File world, int x, int y) {
        int texture = previewTextures.computeIfAbsent(world, this::loadPreview);
        if (texture == 0) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + 18);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + 18, y + 18);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + 18, y);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }

    private int loadPreview(File world) {
        var path = WorldStorage.icon(world);
        if (path == null || !path.toFile().isFile()) return 0;
        try {
            var image = ImageIO.read(path.toFile());
            return image == null ? 0 : minecraft.textureManager.load(image);
        } catch (IOException | RuntimeException invalidIcon) {
            return 0;
        }
    }
}
