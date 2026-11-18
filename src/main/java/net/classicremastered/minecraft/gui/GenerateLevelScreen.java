package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelInfiniteFlat;
import net.classicremastered.minecraft.level.LevelInfiniteTerrain;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.generator.FlatLevelGenerator;
import net.classicremastered.minecraft.level.generator.LevelGenerator;

import java.util.Map;

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
    private String worldName = "New World";
    private Map<String, Boolean> rules = GameRulesScreen.values(null);
    private GuiTextField seedField;
    private GuiTextField nameField;

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
        buttons.clear();
        int y = Math.max(24, (height - 174) / 2);
        int left = width / 2 - 108;
        int right = width / 2 + 2;
        nameField = new GuiTextField(fontRenderer, left, y + 12, 216, 20, false);
        nameField.setMaxLength(64);
        nameField.setText(worldName);
        nameField.setFocused(true);
        seedField = new GuiTextField(fontRenderer, left, y + 48, 216, 20);
        seedField.setMaxLength(64);
        seedField.setText(seedString);
        buttons.add(
                new Button(2, left, y + 76, 106, 20, infinite ? "Infinite World" : "Finite World"));
        buttons.add(new Button(4, right, y + 76, 106, 20, biomes ? "Biomes" : "Classic Terrain"));
        Button size = new Button(0, left, y + 100, 106, 20, "Size: " + SIZE_NAMES[sizeIndex]);
        size.active = !infinite;
        buttons.add(size);
        buttons.add(new Button(1, right, y + 100, 106, 20, "Flat: " + (flat ? "ON" : "OFF")));
        buttons.add(new Button(3, left, y + 124, 106, 20, creative ? "Creative" : "Survival"));
        buttons.add(new Button(5, right, y + 124, 106, 20, "Game Rules..."));
        buttons.add(new Button(10, left, y + 152, 106, 20, "Create World"));
        buttons.add(new Button(11, right, y + 152, 106, 20, "Cancel"));
    }

    @Override
    public void tick() {
        if (seedField != null) seedField.updateCursorCounter();
        if (nameField != null) nameField.updateCursorCounter();
    }

    @Override
    protected void onKeyPress(char c, int key) {
        if (nameField != null && nameField.textboxKeyTyped(c, key)) {
            worldName = nameField.getText();
            return;
        }
        if (seedField != null && seedField.textboxKeyTyped(c, key)) {
            seedString = seedField.getText();
            return;
        }
        super.onKeyPress(c, key);
    }

    @Override
    protected void onMouseClick(int x, int y, int button) {
        if (seedField != null) seedField.mouseClicked(x, y, button);
        if (nameField != null) nameField.mouseClicked(x, y, button);
        super.onMouseClick(x, y, button);
    }

    @Override
    protected void onButtonClick(Button b) {
        if (b.id == 11) { // Cancel
            this.minecraft.setCurrentScreen(this.parent);
            return;
        }

        if (b.id == 0) { // World Size
            sizeIndex = (sizeIndex + 1) % SIZE_NAMES.length;
            b.text = "Size: " + SIZE_NAMES[sizeIndex];
            return;
        }

        if (b.id == 1) { // Flat toggle (finite only)
            flat = !flat;
            b.text = "Flat: " + (flat ? "ON" : "OFF");
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
            b.text = creative ? "Creative" : "Survival";
            return;
        }

        if (b.id == 5) {
            minecraft.setCurrentScreen(new GameRulesScreen(this, rules, values -> rules = values));
            return;
        }

        if (b.id == 10) { // Generate
            if (receiver == null && !minecraft.saveCurrentLevel()) {
                minecraft.setCurrentScreen(
                        new ErrorScreen("Save failed", "The current world is still open.", this));
                return;
            }
            seedString = seedField.getText().trim();
            long seed = resolveSeed();

            String creator =
                    this.minecraft.session != null ? this.minecraft.session.username : "anonymous";
            int size = 128 << sizeIndex;
            Level level;
            if (infinite) {
                this.minecraft.progressBar.setTitle("Loading infinite terrain");
                this.minecraft.progressBar.setText("Finding spawn...");
                this.minecraft.progressBar.setProgress(0);
                level =
                        flat && !biomes
                                ? LevelInfiniteFlat.createOverworld(seed)
                                : LevelInfiniteTerrain.createOverworld(seed, biomes, flat);
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
            level.name = worldName.isBlank() ? "New World" : worldName.strip();
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

        this.minecraft.gamemode.apply(level);
        GameRulesScreen.apply(level, rules);
        this.minecraft.gamemode.prepareLevel(level);

        if (receiver != null) {
            receiver.onGenerated(level);
            return;
        }

        this.minecraft.setLevel(level);
        if (!minecraft.saveCurrentLevel()) {
            minecraft.setCurrentScreen(
                    new ErrorScreen(
                            "Save failed",
                            "The new world is open. Save again to retry.",
                            new PauseScreen()));
            return;
        }
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
                Math.max(0, Math.max(24, (height - 174) / 2) - 17),
                0xFFFFFF);

        int y = Math.max(24, (height - 174) / 2);
        drawString(fontRenderer, "World Name", width / 2 - 108, y, 0xDDDDDD);
        drawString(fontRenderer, "Seed (optional)", width / 2 - 108, y + 36, 0xDDDDDD);
        nameField.drawTextBox();
        seedField.drawTextBox();

        super.render(mx, my);
    }
}
