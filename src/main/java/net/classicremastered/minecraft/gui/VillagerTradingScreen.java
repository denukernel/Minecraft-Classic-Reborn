package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.VillagerTrades;
import net.classicremastered.minecraft.mob.Villager;

import org.lwjgl.opengl.GL11;

public final class VillagerTradingScreen extends InventoryScreen {
    private static final int PANEL_WIDTH = 276;
    private static final int TRADE_X = 5;
    private static final int TRADE_Y = 18;
    private static final int TRADE_WIDTH = 88;
    private static final int TRADE_HEIGHT = 20;
    private static final int INVENTORY_X = 108;
    private static final int TEXT_COLOR = 0x404040;

    private final Villager villager;
    private String message = "";

    public VillagerTradingScreen(Villager villager) {
        this.villager = villager;
    }

    @Override
    protected int panelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    @Override
    protected int inventoryGridX() {
        return INVENTORY_X;
    }

    @Override
    public void tick() {
        if (!VillagerTrades.available(villager, minecraft.player)) {
            minecraft.setCurrentScreen(null);
            return;
        }
        super.tick();
    }

    private int offerAt(int mx, int my, int count) {
        int x = panelLeft() + TRADE_X, y = panelTop() + TRADE_Y;
        return inside(mx, my, x, y, TRADE_WIDTH, count * TRADE_HEIGHT)
                ? (my - y) / TRADE_HEIGHT
                : -1;
    }

    @Override
    protected void onMouseRelease(int x, int y, int button) {
        int target = inventorySlotAt(x, y);
        if (finishStackDrag(target, button)
                && VillagerTrades.available(villager, minecraft.player)) {
            placeCarriedStackInSlot(target);
        }
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        if (button == 0) beginStackDrag(-1);
        if (!VillagerTrades.available(villager, minecraft.player)) return;
        if (button == 0) {
            var offers = VillagerTrades.offers(villager, minecraft.player);
            int index = offerAt(mx, my, offers.size());
            if (index >= 0) {
                if (minecraft.player.coins < offers.get(index).price()) {
                    message = "Not enough Minecoins.";
                } else {
                    message =
                            VillagerTrades.purchase(villager, minecraft.player, index)
                                    ? "Trade complete!"
                                    : "Inventory is full.";
                }
                return;
            }
            if (clickInventoryPage(mx, my)) return;
        }
        int source = button == 0 && !hasCarriedStack() ? inventorySlotAt(mx, my) : -1;
        clickPlayerInventorySlot(mx, my, button);
        if (source >= 0 && hasCarriedStack()) beginStackDrag(source);
    }

    @Override
    public void render(int mx, int my) {
        int x = panelLeft(), y = panelTop();
        var player = minecraft.player;
        var offers = VillagerTrades.offers(villager, player);
        int hoverOffer = offerAt(mx, my, offers.size());

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            drawBox(0, 0, width, height, 0x90000000);
            drawSprite("/gui/villager-trading.png", x, y, PANEL_WIDTH, CRAFTING_PANEL_HEIGHT);
            fontRenderer.renderNoShadow("Trades", x + 32, y + 6, TEXT_COLOR);
            fontRenderer.renderNoShadow("Villager", x + INVENTORY_X, y + 6, TEXT_COLOR);
            drawMinecoinIcon(minecraft.textureManager, x + INVENTORY_X, y + 25);
            fontRenderer.renderNoShadow(
                    "Minecoins: " + player.coins, x + INVENTORY_X + 11, y + 26, TEXT_COLOR);
            fontRenderer.renderNoShadow(
                    "Reputation: " + player.villagerReputation,
                    x + INVENTORY_X,
                    y + 38,
                    TEXT_COLOR);
            fontRenderer.renderNoShadow(message, x + INVENTORY_X, y + 53, TEXT_COLOR);
            fontRenderer.renderNoShadow("Inventory", x + INVENTORY_X, y + 72, TEXT_COLOR);
            drawInventoryPageButtons();
            drawSprite("/gui/villager-scroller_disabled.png", x + 94, y + 18, 6, 27);

            for (int i = 0; i < offers.size(); i++) {
                var offer = offers.get(i);
                int rowY = y + TRADE_Y + i * TRADE_HEIGHT;
                drawOffer(x + TRADE_X, rowY, offer, i == hoverOffer, player.coins >= offer.price());
            }

            int hoverSlot = renderPlayerInventory(mx, my);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (!hasCarriedStack()) {
                if (hoverOffer >= 0) {
                    ItemTooltip.draw(this, offers.get(hoverOffer).raw(), null, mx, my);
                } else if (hoverSlot >= 0 && player.inventory.getId(hoverSlot) > 0) {
                    ItemTooltip.draw(
                            this,
                            player.inventory.getId(hoverSlot),
                            player.inventory.crate(hoverSlot),
                            mx,
                            my);
                }
            }
            renderCarriedStack(mx, my);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawOffer(
            int x, int y, VillagerTrades.Offer offer, boolean hovered, boolean affordable) {
        drawBox(x, y, x + TRADE_WIDTH, y + TRADE_HEIGHT, 0xFF555555);
        int background = hovered ? 0xFFD8D8D8 : 0xFFB4B4B4;
        drawBox(x + 1, y + 1, x + TRADE_WIDTH - 1, y + TRADE_HEIGHT - 1, background);
        fontRenderer.renderNoShadow(
                offer.price() + " coins", x + 4, y + 6, affordable ? 0xFF654819 : 0xFF6B6B6B);
        drawSprite("/gui/villager-trade_arrow.png", x + 48, y + 5, 10, 9);
        drawRecipeItem(offer.raw(), offer.count(), x + 65, y + 2);
    }
}
