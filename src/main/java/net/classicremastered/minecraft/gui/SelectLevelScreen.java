package net.classicremastered.minecraft.gui;

import java.io.File;
import java.util.Date;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class SelectLevelScreen extends GuiScreen {

    // Button IDs
    // Button IDs
    private static final int ID_LOAD = 10;
    private static final int ID_NEW = 11;
    private static final int ID_BACK = 12;
    private static final int ID_DELETE = 13; // <-- NEW
    private static final int ID_SLOTBASE = 100; // 100..105

    // Confirmation state
    private boolean confirmDelete = false; // <-- NEW

    private final GuiScreen parent;
    private File levelsDir;
    private final File[] slotFile = new File[6];
    private final String[] label = new String[6];
    private int selected = -1;

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

        // Layout: center the 6 slots, keep action buttons below with padding
        int rowH = 22;
        int listH = 6 * rowH;
        int startY = (this.height - listH) / 2; // centered list

        for (int i = 0; i < 6; i++) {
            slotFile[i] = new File(levelsDir, "slot" + (i + 1) + ".lvl.gz");
            label[i] = makeLabel(i);
            this.buttons.add(new Button(ID_SLOTBASE + i, this.width / 2 - 100, startY + i * rowH, 200, 20, label[i]));
        }

        int actionY = startY + listH + 18; // gap below list
        this.buttons.add(new Button(ID_LOAD, this.width / 2 - 100, actionY, 64, 20, "Load"));
        this.buttons.add(new Button(ID_NEW, this.width / 2 - 100 + 66, actionY, 92, 20, "New/Overwrite"));
        this.buttons.add(new Button(ID_DELETE, this.width / 2 + 66, actionY, 64, 20, "Delete")); // NEW
        this.buttons.add(new Button(ID_BACK, 6, 6, 60, 20, "Back"));

        updateActionStates();
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, width, height, 0x80202020, 0xFF000000);
        drawCenteredString(fontRenderer, "Select Level (6 slots)", width / 2, 28, 0xFFFFFF);

        if (selected >= 0) {
            Button b = (Button) this.buttons.get(indexForSlotButton(selected));
            int pad = 2;
            drawFadingBox(b.x - pad, b.y - pad, b.x + b.width + pad, b.y + b.height + pad, 0x4040A0FF, 0x402060C0);
        }

        super.render(mx, my);

        if (selected >= 0) {
            // Position near the bottom of the screen, safe below action buttons
            int y = this.height - 12; // 12px margin from bottom edge
            drawCenteredString(fontRenderer, "Selected: " + label[selected], this.width / 2, y, 0xA0FFFFFF);
        }


        // Confirmation warning
        if (confirmDelete) {
            String warn1 = "Are you sure to delete this level?";
            String warn2 = "This cannot be undone! Click Delete again to confirm.";
            drawCenteredString(fontRenderer, warn1, width / 2, height - 56, 0xFFFF5555);
            drawCenteredString(fontRenderer, warn2, width / 2, height - 44, 0xFFFF5555);
        }
    }

    @Override
    protected void onButtonClick(Button b) {
        int id = b.id;

        // Slot clicked
        if (id >= ID_SLOTBASE && id < ID_SLOTBASE + 6) {
            selected = id - ID_SLOTBASE;
            confirmDelete = false; // cancel any pending confirmation
            updateActionStates();

            // If empty slot → open generator immediately
            File f = slotFile[selected];
            if (!f.exists()) {
                openGeneratorFor(f, selected);
            }
            return;
        }

        // Back
        if (id == ID_BACK) {
            minecraft.setCurrentScreen(parent);
            return;
        }

        // Nothing selected yet
        if (selected < 0)
            return;

        File f = slotFile[selected];

        if (id == ID_LOAD) {
            confirmDelete = false;
            if (f.exists()) {
                try {
                    Level L = minecraft.levelIo.load(f);
                    if (L != null)
                        startWith(L);
                    else
                        minecraft.setCurrentScreen(new ErrorScreen("Failed to load", "Corrupt or unreadable level."));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    minecraft.setCurrentScreen(new ErrorScreen("Failed to load", ex.toString()));
                }
            }
            return;
        }

        if (id == ID_NEW) {
            confirmDelete = false;
            openGeneratorFor(f, selected);
            return;
        }

        if (id == ID_DELETE) {
            if (!f.exists()) {
                confirmDelete = false;
                updateActionStates();
                return;
            }
            if (!confirmDelete) {
                // First press → show confirmation warning
                confirmDelete = true;
                return;
            }
            // Second press → delete
            boolean ok = false;
            try {
                ok = f.delete();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            confirmDelete = false;
            if (!ok && f.exists()) {
                minecraft.setCurrentScreen(new ErrorScreen("Delete failed", "Could not delete:\n" + f.getName()));
                return;
            }
            // Refresh UI state
            refreshSlot(selected);
            // Optional: deselect slot if you prefer
            // selected = -1;
            updateActionStates();
            return;
        }
    }

    // ---- Helpers ----

    private void openGeneratorFor(final File target, final int slotIdx) {
        // Open GenerateLevelScreen with a receiver that saves to this slot and starts
        // it
        minecraft.setCurrentScreen(new GenerateLevelScreen(this, new GenerateLevelScreen.Receiver() {
            @Override
            public void onGenerated(Level level) {
                try {
                    minecraft.levelIo.save(level, target);
                    refreshSlot(slotIdx);
                    startWith(level);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    minecraft.setCurrentScreen(new ErrorScreen("Failed to save", ex.toString()));
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

    private String makeLabel(int idx) {
        File f = slotFile[idx];
        if (f != null && f.exists()) {
            return "Slot " + (idx + 1) + " - " + f.getName() + " (" + new Date(f.lastModified()) + ")";
        }
        return "Slot " + (idx + 1) + " - Empty";
    }

    private void refreshSlot(int idx) {
        label[idx] = makeLabel(idx);
        ((Button) this.buttons.get(indexForSlotButton(idx))).text = label[idx];
        updateActionStates();
    }

    private int indexForSlotButton(int slotIdx) {
        return slotIdx;
    } // first 6 buttons are slots

    private void updateActionStates() {
        Button load = findButton(ID_LOAD);
        if (load != null)
            load.active = (selected >= 0 && slotFile[selected].exists());

        Button mk = findButton(ID_NEW);
        if (mk != null)
            mk.active = (selected >= 0);

        Button del = findButton(ID_DELETE);
        if (del != null)
            del.active = (selected >= 0 && slotFile[selected].exists());
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
