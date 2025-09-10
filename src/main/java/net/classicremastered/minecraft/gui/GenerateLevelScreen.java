package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.generator.FlatLevelGenerator;
import net.classicremastered.minecraft.level.generator.LevelGenerator;

public final class GenerateLevelScreen extends GuiScreen {

    public interface Receiver {
        void onGenerated(Level level);
    }

    private final GuiScreen parent;
    private final Receiver receiver;

    // === Options ===
    private static final String[] SIZE_NAMES = { "Small", "Normal", "Huge" };
    private int sizeIndex = 0;
    private boolean flat = false;
    private boolean creative = false;

    // Infinite Flat fully disabled
    private enum WorldType { FINITE, INFINITE_TERRAIN }
    private WorldType worldType = WorldType.FINITE;

    private String seedString = "";
    private GuiTextField seedField;

    public GenerateLevelScreen(GuiScreen parent) {
        this(parent, null);
    }

    public GenerateLevelScreen(GuiScreen parent, Receiver receiver) {
        this.parent = parent;
        this.receiver = receiver;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        int y = this.height / 4;
        int cx = this.width / 2;

        // World type selector
        this.buttons.add(new Button(2, cx - 100, y, "World Type: " + getWorldTypeName()));
        int line = 1;

        if (worldType == WorldType.FINITE) {
            this.buttons.add(new Button(0, cx - 100, y + 24 * line, "World Size: " + SIZE_NAMES[sizeIndex]));
            line++;
            this.buttons.add(new Button(1, cx - 100, y + 24 * line, "Flat World: " + (flat ? "ON" : "OFF")));
            line++;
        }

        this.buttons.add(new Button(3, cx - 100, y + 24 * line, "Mode: " + (creative ? "CREATIVE" : "SURVIVAL")));
        line++;

        // Seed text field
        this.seedField = new GuiTextField(this.fontRenderer, cx - 100, y + 24 * line, 200, 20);
        this.seedField.setMaxLength(64);
        this.seedField.setText(seedString != null ? seedString : "");
        line++;

        this.buttons.add(new Button(10, cx - 100, y + 24 * line, "Generate"));
        this.buttons.add(new Button(11, cx - 100, y + 24 * (line + 1), "Cancel"));
    }

    @Override
    public void tick() {
        if (seedField != null) seedField.updateCursorCounter();
    }

    @Override
    protected void onKeyPress(char c, int key) {
        if (seedField != null && seedField.textboxKeyTyped(c, key)) {
            seedString = seedField.getText();
            return;
        }
        super.onKeyPress(c, key);
    }

    @Override
    protected void onMouseClick(int x, int y, int button) {
        super.onMouseClick(x, y, button);
        if (seedField != null) seedField.mouseClicked(x, y, button);
    }

    @Override
    protected void onButtonClick(Button b) {
        if (b.id == 11) { // Cancel
            this.minecraft.setCurrentScreen(this.parent);
            return;
        }

        if (b.id == 0) { // World Size
            sizeIndex = (sizeIndex + 1) % SIZE_NAMES.length;
            b.text = "World Size: " + SIZE_NAMES[sizeIndex];
            return;
        }

        if (b.id == 1) { // Flat toggle (finite only)
            flat = !flat;
            b.text = "Flat World: " + (flat ? "ON" : "OFF");
            return;
        }

        if (b.id == 2) { // World Type toggle (Finite <-> Infinite Terrain)
            worldType = (worldType == WorldType.FINITE)
                ? WorldType.INFINITE_TERRAIN
                : WorldType.FINITE;
            this.onOpen(); // rebuild buttons for current mode
            return;
        }

        if (b.id == 3) { // Mode toggle
            creative = !creative;
            b.text = "Mode: " + (creative ? "CREATIVE" : "SURVIVAL");
            return;
        }

        if (b.id == 10) { // Generate
            seedString = seedField.getText().trim();
            long seed = resolveSeed();

            Level level;
            switch (worldType) {
                case INFINITE_TERRAIN: {
                    level = new LevelInfiniteTerrain(seed, 64);
                    level.name = "Infinite Terrain";
                    break;
                }
                default: { // FINITE
                    if (flat) {
                        level = FlatLevelGenerator.makeFlatLevel(sizeIndex);
                    } else {
                        String creator = this.minecraft.session != null ? this.minecraft.session.username : "anonymous";
                        level = new LevelGenerator(this.minecraft.progressBar)
                                .generate(creator, 128 << sizeIndex, 128 << sizeIndex, 64);
                    }
                    break;
                }
            }
            level.creator = this.minecraft.session != null ? this.minecraft.session.username : "anonymous";
            applyGamemode(level);
        }
    }

    private String getWorldTypeName() {
        return (worldType == WorldType.INFINITE_TERRAIN) ? "Infinite Terrain" : "Finite";
    }

    private long resolveSeed() {
        if (seedString == null || seedString.isEmpty()) {
            long nano = System.nanoTime();
            return (nano ^ (nano << 21)) * 0x9E3779B97F4A7C15L;
        }

        try {
            long val = Long.parseLong(seedString);
            if (val < Integer.MIN_VALUE) val = Integer.MIN_VALUE;
            if (val > Integer.MAX_VALUE) val = Integer.MAX_VALUE;
            return val;
        } catch (NumberFormatException e) {
            return seedString.hashCode(); // fallback for non-numeric
        }
    }

    private void applyGamemode(Level level) {
        if (creative) {
            this.minecraft.gamemode = new CreativeGameMode(this.minecraft);
            level.creativeMode = true;
        } else {
            this.minecraft.gamemode = new SurvivalGameMode(this.minecraft);
            level.creativeMode = false;
        }

        this.minecraft.gamemode.prepareLevel(level);
        this.minecraft.gamemode.apply(level);

        if (receiver != null) receiver.onGenerated(level);

        this.minecraft.setLevel(level);
        this.minecraft.setCurrentScreen(null);
        this.minecraft.grabMouse();
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        drawCenteredString(this.fontRenderer, "Generate new level", this.width / 2, 40, 0xFFFFFF);

        if (seedField != null) seedField.drawTextBox();

        super.render(mx, my);
    }
}
