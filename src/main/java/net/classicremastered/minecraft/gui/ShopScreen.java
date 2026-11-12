package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.*;
import net.classicremastered.minecraft.player.Player;

import java.util.*;

public final class ShopScreen extends GuiScreen {
    private final GuiScreen parent;

    private record Offer(int raw, int count, int price, String name) {}

    private final List<Offer> offers = new ArrayList<>();
    private int page;

    public ShopScreen() {
        this(new PauseScreen());
    }

    public ShopScreen(GuiScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        if (minecraft.level.creativeMode) {
            minecraft.setCurrentScreen(parent);
            return;
        }
        ItemRegistry.bootstrap();
        offers.clear();
        offers.add(new Offer(-78, 1, 30, "Defender"));
        offers.add(new Offer(Item.BOW.id + 256, 1, 20, "Bow"));
        offers.add(new Offer(Equipment.ARROW.id + 256, 5, 5, "5 Arrows"));
        for (Equipment.Gear gear : Equipment.GEAR.values())
            offers.add(new Offer(gear.id + 256, 1, gear.price, gear.name));
        offers.add(new Offer(Item.APPLE.id + 256, 1, 4, "Apple"));
        offers.add(new Offer(46, 10, 20, "10 TNT"));
        offers.add(new Offer(Item.FLINT_AND_STEEL.id + 256, 1, 5, "Flint & Steel"));
        offers.add(new Offer(Item.PHYSICS_GUN.id + 256, 1, 50, "Physics Gun"));
        offers.add(new Offer(Item.GRAVITYGUN.id + 256, 1, 75, "Gravity Gun"));
        offers.add(new Offer(Item.GRAPPLING_HOOK.id + 256, 1, 30, "Grappling Hook"));
        refresh();
    }

    private void refresh() {
        buttons.clear();
        int x = width / 2 - 100, y = height / 2 - 78;
        for (int i = 0; i < 5; i++) {
            int n = page * 5 + i;
            if (n >= offers.size()) break;
            Offer o = offers.get(n);
            buttons.add(new Button(i, x, y + i * 24, o.name + " (" + o.price + " coins)"));
        }
        buttons.add(new Button(10, x, height - 51, 96, 20, "Previous"));
        buttons.add(new Button(11, x + 104, height - 51, 96, 20, "Next"));
        buttons.add(new Button(12, x, height - 27, "Back"));
    }

    public static boolean purchase(Player p, int raw, int count, int price) {
        if (p == null || price < 0 || count <= 0 || p.coins < price) return false;
        if (raw <= 0
                || raw >= 256 && (raw - 256 >= Item.items.length || Item.items[raw - 256] == null))
            return false;
        if (raw == TurretItems.PACKED + 256) return false;
        synchronized (p.inventory) {
            int remaining = count;
            int[] additions = new int[p.inventory.totalSize()];
            int cap = Item.stackLimit(raw);
            for (int pass = 0; pass < 2; pass++) {
                for (int slot = 0; slot < additions.length && remaining > 0; slot++) {
                    int id = p.inventory.getId(slot), existing = p.inventory.getCount(slot);
                    boolean empty = id <= 0 || existing <= 0;
                    if (pass == 0 ? empty || id != raw : !empty) continue;
                    int amount = Math.min(remaining, Math.max(0, cap - (empty ? 0 : existing)));
                    additions[slot] = amount;
                    remaining -= amount;
                }
            }
            if (remaining > 0) return false;
            for (int slot = 0; slot < additions.length; slot++) {
                if (additions[slot] == 0) continue;
                int existing = p.inventory.getId(slot) == raw ? p.inventory.getCount(slot) : 0;
                p.inventory.setIdCount(slot, raw, Math.max(0, existing) + additions[slot]);
            }
            p.coins -= price;
            return true;
        }
    }

    @Override
    protected void onButtonClick(Button b) {
        int pages = (offers.size() + 4) / 5;
        if (b.id == 10) {
            page = (page + pages - 1) % pages;
            refresh();
            return;
        }
        if (b.id == 11) {
            page = (page + 1) % pages;
            refresh();
            return;
        }
        if (b.id == 12) {
            minecraft.setCurrentScreen(parent);
            return;
        }
        int n = page * 5 + b.id;
        if (n < 0 || n >= offers.size()) return;
        Offer o = offers.get(n);
        minecraft.hud.addChat(
                (o.raw == -78
                                ? net.classicremastered.minecraft.mob.Defender.purchase(
                                        minecraft.player)
                                : purchase(minecraft.player, o.raw, o.count, o.price))
                        ? "&aPurchased " + o.name
                        : "&cNot enough coins, inventory space, or safe ground nearby.");
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, width, height, 0xA0202020, 0xD0404040);
        String heading = "Marketplace " + (page + 1) + "/" + ((offers.size() + 4) / 5);
        String balance = "Minecoins: " + minecraft.player.coins;
        int headingWidth = fontRenderer.getWidth(heading);
        int left = (width - headingWidth - 18 - fontRenderer.getWidth(balance)) / 2;
        int top = height / 2 - 99;
        drawString(fontRenderer, heading, left, top, 0xFFFFFF);
        drawMinecoinIcon(minecraft.textureManager, left + headingWidth + 7, top - 1);
        drawString(fontRenderer, balance, left + headingWidth + 18, top, 0xFFFFFF);
        super.render(mx, my);
        int y = height / 2 - 78;
        for (int i = 0; i < 5; i++)
            if (my >= y + i * 24
                    && my < y + i * 24 + 20
                    && mx >= width / 2 - 100
                    && mx < width / 2 + 100) {
                int n = page * 5 + i;
                if (n < offers.size()) {
                    String detail = Equipment.description(offers.get(n).raw);
                    drawCenteredString(fontRenderer, detail, width / 2, height - 65, 0xFFFF80);
                }
            }
    }
}
