package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.generator.FlatLevelGenerator;
import net.classicremastered.minecraft.level.generator.LevelGenerator;

public final class GenerateLevelScreen extends GuiScreen {

    public interface Receiver {
        void onGenerated(Level level);
    }

    private final GuiScreen parent;
    private final Receiver receiver;

    // === Options ===
    private static final String[] SIZE_NAMES = {"Small", "Normal", "Huge"};
    private int sizeIndex = 0;
    private boolean flat = false;
    private boolean creative = false;

    private boolean infinite;
    private boolean biomes = true;

    private String seedString = "";
    private GuiTextField seedField;

    public GenerateLevelScreen(GuiScreen parent) {
        this(parent, null);
    }

    public GenerateLevelScreen(GuiScreen parent, Receiver receiver) {
        super(parent);
        this.parent = parent;
        this.receiver = receiver;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        int rows = 7 + (!infinite ? 1 : 0);
        int y = Math.max(8, (this.height - rows * 24 + 4) / 2);
        int cx = this.width / 2;

        // World type selector
        this.buttons.add(
                new Button(2, cx - 100, y, "World Type: " + (infinite ? "Infinite" : "Finite")));
        int line = 1;

        this.buttons.add(
                new Button(
                        4,
                        cx - 100,
                        y + 24 * line++,
                        "Terrain: " + (biomes ? "Biomes" : "Classic")));
        if (!infinite) {
            this.buttons.add(
                    new Button(0, cx - 100, y + 24 * line, "World Size: " + SIZE_NAMES[sizeIndex]));
            line++;
        }
        this.buttons.add(
                new Button(1, cx - 100, y + 24 * line++, "Flat World: " + (flat ? "ON" : "OFF")));

        this.buttons.add(
                new Button(
                        3,
                        cx - 100,
                        y + 24 * line,
                        "Mode: " + (creative ? "CREATIVE" : "SURVIVAL")));
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

        if (b.id == 2) { // World Type toggle
            infinite = !infinite;
            this.onOpen(); // rebuild buttons for current mode
            return;
        }

        if (b.id == 4) {
            biomes = !biomes;
            this.onOpen();
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

            String creator =
                    this.minecraft.session != null ? this.minecraft.session.username : "anonymous";
            int size = 128 << sizeIndex;
            Level level;
            if (infinite) {
                level =
                        flat && !biomes
                                ? new LevelInfiniteFlat(seed, 64)
                                : new LevelInfiniteTerrain(seed, 64, biomes, flat);
                level.name =
                        biomes
                                ? (flat ? "Infinite Flat Biomes" : "Infinite Biomes")
                                : flat ? "Infinite Flat" : "Infinite Terrain";
            } else if (biomes) {
                level = new BiomeLevelGenerator(seed).generate(creator, size, flat);
            } else if (flat) {
                level = FlatLevelGenerator.makeFlatLevel(sizeIndex);
            } else {
                level =
                        new LevelGenerator(this.minecraft.progressBar)
                                .generate(creator, size, size, 64);
            }
            level.creator =
                    this.minecraft.session != null ? this.minecraft.session.username : "anonymous";
            applyGamemode(level);
        }
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
        if (this.minecraft.level == null) drawMenuBackground();
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        drawCenteredString(
                this.fontRenderer,
                "Generate new level",
                this.width / 2,
                Math.max(0, ((Button) this.buttons.get(0)).y - 14),
                0xFFFFFF);

        if (seedField != null) seedField.drawTextBox();

        super.render(mx, my);
    }
}
