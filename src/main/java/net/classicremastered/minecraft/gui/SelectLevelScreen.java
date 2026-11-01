package net.classicremastered.minecraft.gui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class SelectLevelScreen extends GuiScreen {

    // Button IDs
    private static final int ID_LOAD = 10;
    private static final int ID_NEW = 11;
    private static final int ID_BACK = 12;
    private static final int ID_DELETE = 13;
    private static final int ID_RENAME = 14;
    private static final int ID_PREV = 15;
    private static final int ID_NEXT = 16;
    private static final int ID_SELECT_ALL = 17;
    private static final int ID_SLOTBASE = 100; // 100..104

    private static final int LEVELS_PER_PAGE = 5;

    // Confirmation state
    private boolean confirmDelete = false;

    private final GuiScreen parent;
    private File levelsDir;
    private final List<File> levelFiles = new ArrayList<>();
    private final List<Integer> selectedIndices = new ArrayList<>();
    private int page = 0;
    private int selected = -1; // global index in levelFiles

    public SelectLevelScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();

        // Ensure directory
        levelsDir = new File(Minecraft.getMinecraftDir(), "levels");
        if (!levelsDir.exists())
            levelsDir.mkdirs();

        // Scan files
        File[] files = levelsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".lvl.gz"));
        levelFiles.clear();
        if (files != null) {
            // Sort by last modified DESCENDING (most recent first)
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            levelFiles.addAll(Arrays.asList(files));
        }

        // Validate selectedIndices
        selectedIndices.removeIf(idx -> idx < 0 || idx >= levelFiles.size());
        if (selectedIndices.isEmpty()) {
            selected = -1;
        } else {
            selected = selectedIndices.get(0);
        }

        // Clamp page index
        int maxPage = Math.max(0, (levelFiles.size() - 1) / LEVELS_PER_PAGE);
        if (page > maxPage) {
            page = maxPage;
        }

        // Layout: center the slots vertically
        int rowH = 22;
        int listH = LEVELS_PER_PAGE * rowH;
        int startY = (this.height - listH) / 2 - 10;

        int start = page * LEVELS_PER_PAGE;
        int end = Math.min(start + LEVELS_PER_PAGE, levelFiles.size());

        for (int i = start; i < end; i++) {
            int pageIdx = i - start;
            File f = levelFiles.get(i);
            String labelText = makeLabel(f);
            this.buttons.add(new Button(ID_SLOTBASE + pageIdx, this.width / 2 - 130, startY + pageIdx * rowH, 260, 20, labelText));
        }

        // Navigation row
        int navY = startY + LEVELS_PER_PAGE * rowH + 4;
        Button prevBtn = new Button(ID_PREV, this.width / 2 - 130, navY, 128, 20, "< Prev Page");
        Button nextBtn = new Button(ID_NEXT, this.width / 2 + 2, navY, 128, 20, "Next Page >");
        prevBtn.active = (page > 0);
        nextBtn.active = (end < levelFiles.size());
        this.buttons.add(prevBtn);
        this.buttons.add(nextBtn);

        // Action row
        int actionY = navY + 26;
        int cx = this.width / 2;
        this.buttons.add(new Button(ID_LOAD, cx - 125, actionY, 60, 20, "Load"));
        this.buttons.add(new Button(ID_NEW, cx - 60, actionY, 60, 20, "New"));
        this.buttons.add(new Button(ID_RENAME, cx + 5, actionY, 60, 20, "Rename"));
        this.buttons.add(new Button(ID_DELETE, cx + 70, actionY, 60, 20, "Delete"));
        this.buttons.add(new Button(ID_BACK, 6, 6, 60, 20, "Back"));

        // Select All button (top right)
        boolean allSelected = !levelFiles.isEmpty() && selectedIndices.size() == levelFiles.size();
        String selectAllLabel = allSelected ? "Deselect All" : "Select All";
        this.buttons.add(new Button(ID_SELECT_ALL, this.width - 96, 6, 90, 20, selectAllLabel));

        updateActionStates();
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, width, height, 0x80202020, 0xFF000000);
        drawCenteredString(fontRenderer, "Select Level (Page " + (page + 1) + ")", width / 2, 28, 0xFFFFFF);

        int start = page * LEVELS_PER_PAGE;
        int end = Math.min(start + LEVELS_PER_PAGE, levelFiles.size());
        for (int i = start; i < end; i++) {
            if (selectedIndices.contains(i)) {
                int pageIdx = i - start;
                Button b = findButton(ID_SLOTBASE + pageIdx);
                if (b != null) {
                    int pad = 2;
                    drawFadingBox(b.x - pad, b.y - pad, b.x + b.width + pad, b.y + b.height + pad, 0x4040A0FF, 0x402060C0);
                }
            }
        }

        super.render(mx, my);

        if (selectedIndices.size() == 1) {
            int sel = selectedIndices.get(0);
            int y = this.height - 12; // 12px margin from bottom edge
            File f = levelFiles.get(sel);
            String name = f.getName();
            if (name.toLowerCase().endsWith(".lvl.gz")) {
                name = name.substring(0, name.length() - 7);
            }
            drawCenteredString(fontRenderer, "Selected: " + name, this.width / 2, y, 0xA0FFFFFF);
        } else if (selectedIndices.size() > 1) {
            int y = this.height - 12;
            drawCenteredString(fontRenderer, "Selected: " + selectedIndices.size() + " levels", this.width / 2, y, 0xA0FFFFFF);
        }

        // Confirmation warning
        if (confirmDelete) {
            String warn1 = (selectedIndices.size() > 1) 
                ? "Are you sure to delete " + selectedIndices.size() + " levels?" 
                : "Are you sure to delete this level?";
            String warn2 = "This cannot be undone! Click Delete again to confirm.";
            drawCenteredString(fontRenderer, warn1, width / 2, height - 56, 0xFFFF5555);
            drawCenteredString(fontRenderer, warn2, width / 2, height - 44, 0xFFFF5555);
        }
    }

    @Override
    protected void onButtonClick(Button b) {
        int id = b.id;

        // Slot clicked
        if (id >= ID_SLOTBASE && id < ID_SLOTBASE + LEVELS_PER_PAGE) {
            int clickedGlobalIdx = page * LEVELS_PER_PAGE + (id - ID_SLOTBASE);
            if (clickedGlobalIdx < levelFiles.size()) {
                selectedIndices.clear();
                selectedIndices.add(clickedGlobalIdx);
                selected = clickedGlobalIdx;
                confirmDelete = false; // cancel any pending confirmation
                onOpen();
            }
            return;
        }

        // Back
        if (id == ID_BACK) {
            minecraft.setCurrentScreen(parent);
            return;
        }

        // Pagination
        if (id == ID_PREV) {
            if (page > 0) {
                page--;
                confirmDelete = false;
                onOpen();
            }
            return;
        }

        if (id == ID_NEXT) {
            int maxPage = (levelFiles.size() - 1) / LEVELS_PER_PAGE;
            if (page < maxPage) {
                page++;
                confirmDelete = false;
                onOpen();
            }
            return;
        }

        // Select All / Deselect All
        if (id == ID_SELECT_ALL) {
            confirmDelete = false;
            boolean allSelected = !levelFiles.isEmpty() && selectedIndices.size() == levelFiles.size();
            selectedIndices.clear();
            if (!allSelected) {
                for (int i = 0; i < levelFiles.size(); i++) {
                    selectedIndices.add(i);
                }
                selected = 0;
            } else {
                selected = -1;
            }
            onOpen();
            return;
        }

        // New Level
        if (id == ID_NEW) {
            confirmDelete = false;
            File newFile = findNextAvailableFile();
            openGeneratorFor(newFile);
            return;
        }

        // Nothing selected yet below
        if (selectedIndices.isEmpty())
            return;

        // Load (single select only)
        if (id == ID_LOAD && selectedIndices.size() == 1) {
            confirmDelete = false;
            File f = levelFiles.get(selectedIndices.get(0));
            if (f.exists()) {
                try {
                    Level L = minecraft.levelIo.load(f);
                    if (L != null)
                        startWith(L);
                    else
                        minecraft.setCurrentScreen(new ErrorScreen("Failed to load", "Corrupt or unreadable level.", this));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    minecraft.setCurrentScreen(new ErrorScreen("Failed to load", ex.toString(), this));
                }
            }
            return;
        }

        // Rename (single select only)
        if (id == ID_RENAME && selectedIndices.size() == 1) {
            confirmDelete = false;
            final File f = levelFiles.get(selectedIndices.get(0));
            String currentName = f.getName();
            if (currentName.toLowerCase().endsWith(".lvl.gz")) {
                currentName = currentName.substring(0, currentName.length() - 7);
            }
            minecraft.setCurrentScreen(new LevelNameScreen(this, currentName, new LevelNameScreen.Receiver() {
                @Override
                public void onNameEntered(String newName) {
                    newName = newName.trim();
                    File newFile = new File(levelsDir, newName + ".lvl.gz");
                    if (newFile.exists()) {
                        minecraft.setCurrentScreen(new ErrorScreen("Rename failed", "A level with that name already exists.", SelectLevelScreen.this));
                        return;
                    }
                    boolean ok = f.renameTo(newFile);
                    if (!ok) {
                        minecraft.setCurrentScreen(new ErrorScreen("Rename failed", "Could not rename file.\nIt may be in use.", SelectLevelScreen.this));
                        return;
                    }
                    minecraft.setCurrentScreen(SelectLevelScreen.this);
                    selectedIndices.clear();
                    onOpen();
                    // Select renamed file
                    for (int idx = 0; idx < levelFiles.size(); idx++) {
                        if (levelFiles.get(idx).getName().equals(newFile.getName())) {
                            selectedIndices.add(idx);
                            selected = idx;
                            page = idx / LEVELS_PER_PAGE;
                            break;
                        }
                    }
                    onOpen();
                }
            }));
            return;
        }

        // Delete (single or multiple)
        if (id == ID_DELETE) {
            // Check if files exist
            boolean anyExist = false;
            for (int idx : selectedIndices) {
                if (idx >= 0 && idx < levelFiles.size() && levelFiles.get(idx).exists()) {
                    anyExist = true;
                    break;
                }
            }
            if (!anyExist) {
                confirmDelete = false;
                selectedIndices.clear();
                selected = -1;
                onOpen();
                return;
            }

            if (!confirmDelete) {
                confirmDelete = true;
                return;
            }
            
            // Delete confirmed
            confirmDelete = false;
            List<File> failedDeletes = new ArrayList<>();
            for (int idx : selectedIndices) {
                if (idx >= 0 && idx < levelFiles.size()) {
                    File f = levelFiles.get(idx);
                    if (f.exists()) {
                        try {
                            boolean ok = f.delete();
                            if (!ok) failedDeletes.add(f);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            failedDeletes.add(f);
                        }
                    }
                }
            }

            if (!failedDeletes.isEmpty()) {
                StringBuilder sb = new StringBuilder("Could not delete:\n");
                for (File f : failedDeletes) {
                    sb.append(f.getName()).append("\n");
                }
                sb.append("\nFiles may be in use.");
                minecraft.setCurrentScreen(new ErrorScreen("Delete failed", sb.toString(), this));
            }
            
            selectedIndices.clear();
            selected = -1;
            onOpen();
            return;
        }
    }

    // ---- Helpers ----

    private File findNextAvailableFile() {
        int i = 1;
        while (true) {
            File f = new File(levelsDir, "World " + i + ".lvl.gz");
            if (!f.exists()) {
                return f;
            }
            i++;
        }
    }

    private void openGeneratorFor(final File target) {
        minecraft.setCurrentScreen(new GenerateLevelScreen(this, new GenerateLevelScreen.Receiver() {
            @Override
            public void onGenerated(Level level) {
                try {
                    startWith(level);
                    boolean saved = minecraft.levelIo.save(level, target);
                    if (!saved) {
                        minecraft.setCurrentScreen(new ErrorScreen("Save failed", "Could not save level.\n\nFile may be in use by another process.", SelectLevelScreen.this));
                        return;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    minecraft.setCurrentScreen(new ErrorScreen("Failed to save", ex.toString(), SelectLevelScreen.this));
                }
            }
        }));
    }

    private void startWith(Level level) {
        minecraft.gamemode.prepareLevel(level);
        minecraft.setLevel(level);
        minecraft.setCurrentScreen(null);
        minecraft.grabMouse();
    }

    private String makeLabel(File f) {
        if (f != null && f.exists()) {
            String name = f.getName();
            if (name.toLowerCase().endsWith(".lvl.gz")) {
                name = name.substring(0, name.length() - 7);
            }
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()));
            return name + " (" + dateStr + ")";
        }
        return "Unknown File";
    }

    private void updateActionStates() {
        boolean hasSelection = !selectedIndices.isEmpty();
        boolean singleSelection = selectedIndices.size() == 1;

        Button load = findButton(ID_LOAD);
        if (load != null)
            load.active = singleSelection;

        Button rename = findButton(ID_RENAME);
        if (rename != null)
            rename.active = singleSelection;

        Button del = findButton(ID_DELETE);
        if (del != null)
            del.active = hasSelection;
            
        Button selectAll = findButton(ID_SELECT_ALL);
        if (selectAll != null) {
            boolean allSelected = !levelFiles.isEmpty() && selectedIndices.size() == levelFiles.size();
            selectAll.text = allSelected ? "Deselect All" : "Select All";
            selectAll.active = !levelFiles.isEmpty();
        }
    }

    private Button findButton(int id) {
        for (int i = 0; i < this.buttons.size(); i++) {
            Button b = (Button) this.buttons.get(i);
            if (b.id == id)
                return b;
        }
        return null;
    }
}
