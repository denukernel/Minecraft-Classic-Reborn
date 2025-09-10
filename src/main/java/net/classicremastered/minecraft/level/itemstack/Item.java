package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

public class Item {
    public static Item[] items = new Item[256];

    public final int id;
    public final String name;
    protected String texture;  // <- made non-final so subclasses can swap textures

    /** Stack cap per item (tools set this to 1). */
    public final int maxStackSize;

    // Handy handles (filled by ItemRegistry.bootstrap())
    public static Item EMPTY, APPLE, SWORD, MINING_AXE, SHOVEL;
    public static Item BEETROOT;
    public static Item BOW;
    public static Item FLINT_AND_STEEL;

	public static FeatherItem FEATHER;

    public static GravityGunItem GRAVITYGUN;

    // --- Constructors ---
    public Item(int id, String name, String texture) {
        this(id, name, texture, 64); // default cap
    }

    public Item(int id, String name, String texture, int maxStackSize) {
        this.id = id;
        this.name = name;
        this.texture = texture;
        this.maxStackSize = Math.max(1, maxStackSize);
        if (id >= 0 && id < items.length) items[id] = this;
    }

    // --- Rendering ---
    public void renderIcon(TextureManager tm, ShapeRenderer sr) {
        String tex = getTexture(); // delegate to overridable method
        if (tex == null) return;
        int texId = tm.load(tex);
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, texId);
        sr.begin();
        sr.vertexUV(0, 16, 0, 0, 1);
        sr.vertexUV(16, 16, 0, 1, 1);
        sr.vertexUV(16, 0, 0, 1, 0);
        sr.vertexUV(0, 0, 0, 0, 0);
        sr.end();
    }

    // --- Behavior hooks ---
    /** Right-click action (food, bow draw, etc). */
    public void use(Player player, Level level) {}

    /** Called every tick while item is being held. */
    public void tick(Player player, Level level) {}

    /** Called when player releases right-click (for bows, charging, etc). */
    public void releaseUse(Player player, Level level) {}

    /** Override if item wants to show dynamic texture (e.g., bow draw stages). */
    public String getTexture() {
        return this.texture;
    }

    /** Convenience: non-stackable tools report true. */
    public boolean isTool() { return this.maxStackSize == 1; }

    // --- Legacy entry point: now delegates to the registry ---
    public static void initItems() {
        ItemRegistry.bootstrap();
    }
}
