package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;

public class Item {
    public static Item[] items = new Item[256];

    public final int id;
    public final String name;
    protected String texture; // <- made non-final so subclasses can swap textures

    /** Stack cap per item (tools set this to 1). */
    public final int maxStackSize;

    // Handy handles (filled by ItemRegistry.bootstrap())
    public static Item EMPTY, APPLE, SWORD, MINING_AXE, SHOVEL;
    public static Item BEETROOT;
    public static Item RAW_PORKCHOP, RAW_BEEF;
    public static Item BOW;
    public static Item FLINT_AND_STEEL;
    public static final int REDSTONE_DUST_ID = 44;
    public static final int COAL_ID = 45;

    public static final int LEVER_ICON_ID = 46;
    static final int TORCH_ICON_ID = 74;
    static final int REDSTONE_TORCH_ICON_ID = 75;
    static final int REPEATER_ICON_ID = 76;
    static final int COMPARATOR_ICON_ID = 77;
    static final int LADDER_ICON_ID = 78;

    public static int stackLimit(int raw) {
        int id = inventoryId(raw) - 256;
        return id >= 0 && id < items.length && items[id] != null
                ? Math.min(64, items[id].maxStackSize)
                : 64;
    }

    public static int iconId(int id) {
        if (id == Block.LEVER.id) return LEVER_ICON_ID + 256;
        if (id == Block.TORCH.id) return TORCH_ICON_ID + 256;
        if (id == Block.REDSTONE_TORCH.id) return REDSTONE_TORCH_ICON_ID + 256;
        if (id == Block.REPEATER.id) return REPEATER_ICON_ID + 256;
        if (id == Block.COMPARATOR.id) return COMPARATOR_ICON_ID + 256;
        if (net.classicremastered.minecraft.level.tile.LadderBlock.isLadder(id))
            return LADDER_ICON_ID + 256;
        return inventoryId(id);
    }

    public static boolean isBlockIcon(int itemId) {
        return itemId == LEVER_ICON_ID || itemId >= TORCH_ICON_ID && itemId <= LADDER_ICON_ID;
    }

    public static int inventoryId(int id) {
        return id == net.classicremastered.minecraft.level.tile.Block.REDSTONE_WIRE.id
                ? REDSTONE_DUST_ID + 256
                : id;
    }

    public static FeatherItem FEATHER;

    public static Item BUCKET_EMPTY, BUCKET_WATER, BUCKET_LAVA;
    public static GravityGunItem GRAVITYGUN;
    public static PhysicsGunItem PHYSICS_GUN;
    public static Item SNOWBALL, OAK_SIGN;
    public static Item BAG_OF_SALT,
            HEALTH_DRINK,
            DAMAGE_BOTTLE,
            GRAPPLING_HOOK,
            GLASS_LOOKER,
            ENDER_PEARL;

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

    protected final void consumeSelected(Player player, Level level) {
        if (player == null || level == null || level.creativeMode || level.isNetworkMode()) return;
        int slot = player.inventory.selected;
        if (player.inventory.getId(slot) == id + 256 && player.inventory.getCount(slot) > 0) {
            int count = player.inventory.getCount(slot) - 1;
            player.inventory.setIdCount(slot, count > 0 ? id + 256 : -1, count);
        }
    }

    /** Called every tick while item is being held. */
    public void tick(Player player, Level level) {}

    /** Called when player releases right-click (for bows, charging, etc). */
    public void releaseUse(Player player, Level level) {}

    /** Override if item wants to show dynamic texture (e.g., bow draw stages). */
    public String getTexture() {
        return this.texture;
    }

    public float getModelTextureU0() {
        return 0;
    }

    public float getModelTextureU1() {
        return 1;
    }

    public int getModelTint() {
        return 0xffffff;
    }

    /** Whether a dropped stack of this item survives fire and lava. */
    public boolean isFireResistantDrop() {
        return false;
    }

    /** Convenience: non-stackable tools report true. */
    public boolean isTool() {
        return this.maxStackSize == 1;
    }

    // --- Legacy entry point: now delegates to the registry ---
    public static void initItems() {
        ItemRegistry.bootstrap();
    }
}
