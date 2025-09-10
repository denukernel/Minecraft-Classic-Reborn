package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.player.Player;

public final class ShopScreen extends GuiScreen {

    private int page = 1; // track current shop page

    // Button IDs
    private static final int ID_BUY_BOW = 0;
    private static final int ID_BUY_ARROWS = 1;
    private static final int ID_BUY_SWORD = 2;
    private static final int ID_NEXT_PAGE = 3;

    private static final int ID_BUY_APPLE = 4;
    private static final int ID_BUY_TNT = 5;
    private static final int ID_BUY_FLINT = 6;
    private static final int ID_PREV_PAGE = 7;

    private static final int ID_BUY_PHYSICS_GUN = 8; // added
    private static final int ID_BACK = 9;

    @Override
    public void onOpen() {
        // If creative mode, disable shop
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.creativeMode) {
            if (this.minecraft.hud != null) {
                this.minecraft.hud.addChat("&cShop is unavailable in Creative Mode.");
            }
            this.minecraft.setCurrentScreen(new PauseScreen()); // bounce back to pause
            return;
        }

        refreshButtons();
    }


    private void refreshButtons() {
        this.buttons.clear();
        int x = this.width / 2 - 100;
        int y = this.height / 4;

        if (page == 1) {
            this.buttons.add(new Button(ID_BUY_BOW, x, y + 0, "Buy Bow (20 coins)"));
            this.buttons.add(new Button(ID_BUY_ARROWS, x, y + 24, "Buy 5 Arrows (5 coins)"));
            this.buttons.add(new Button(ID_BUY_SWORD, x, y + 48, "Buy Sword (15 coins)"));
            this.buttons.add(new Button(ID_NEXT_PAGE, x, y + 72, "Next Page →"));
            this.buttons.add(new Button(ID_BACK, x, y + 120, "Back"));
        } else if (page == 2) {
            this.buttons.add(new Button(ID_BUY_APPLE, x, y + 0, "Buy Apple (4 coins)"));
            this.buttons.add(new Button(ID_BUY_TNT, x, y + 24, "Buy 10 TNT (20 coins)"));
            this.buttons.add(new Button(ID_BUY_FLINT, x, y + 48, "Buy Flint & Steel (5 coins)"));
            this.buttons.add(new Button(ID_BUY_PHYSICS_GUN, x, y + 72, "Buy Physics Gun (50 coins)")); // added
            this.buttons.add(new Button(ID_PREV_PAGE, x, y + 96, "← Prev Page"));
            this.buttons.add(new Button(ID_BACK, x, y + 144, "Back"));
        }
    }

    @Override
    protected void onButtonClick(Button b) {
        Player p = this.minecraft.player;
        if (p == null) return;

        switch (b.id) {
        case ID_BUY_BOW:
            if (p.coins >= 20) {
                p.coins -= 20;
                p.inventory.addResource(Item.BOW.id + 256);
                this.minecraft.hud.addChat("&aPurchased Bow!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_ARROWS:
            if (p.coins >= 5) {
                p.coins -= 5;
                p.arrows = Math.min(Player.MAX_ARROWS, p.arrows + 5);
                this.minecraft.hud.addChat("&aPurchased 5 arrows!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_SWORD:
            if (p.coins >= 15) {
                p.coins -= 15;
                p.inventory.addResource(Item.SWORD.id + 256);
                this.minecraft.hud.addChat("&aPurchased Sword!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_APPLE:
            if (p.coins >= 4) {
                p.coins -= 4;
                p.inventory.addResource(Item.APPLE.id + 256);
                this.minecraft.hud.addChat("&aPurchased Apple!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_TNT:
            if (p.coins >= 20) {
                p.coins -= 20;
                for (int i = 0; i < 10; i++) {
                    p.inventory.addResource(net.classicremastered.minecraft.level.tile.Block.TNT.id);
                }
                this.minecraft.hud.addChat("&aPurchased 10 TNT!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_FLINT:
            if (p.coins >= 5) {
                p.coins -= 5;
                p.inventory.addResource(Item.FLINT_AND_STEEL.id + 256);
                this.minecraft.hud.addChat("&aPurchased Flint & Steel!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_BUY_PHYSICS_GUN: // added
            if (p.coins >= 50) {
                p.coins -= 50;
                // ID 11 is your Physics Gun
                p.inventory.addResource(Item.items[11].id + 256);
                this.minecraft.hud.addChat("&aPurchased Physics Gun!");
            } else {
                this.minecraft.hud.addChat("&cNot enough coins!");
            }
            return;

        case ID_NEXT_PAGE:
            page = 2;
            refreshButtons();
            return;

        case ID_PREV_PAGE:
            page = 1;
            refreshButtons();
            return;

        case ID_BACK:
            this.minecraft.setCurrentScreen(new PauseScreen());
            return;
        }
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, this.width, this.height, 0x60000000, 0xA0000000);
        String title = (page == 1) ? "Marketplace (Page 1)" : "Marketplace (Page 2)";
        drawCenteredString(this.fontRenderer, title, this.width / 2, 40, 0xFFFFFF);
        super.render(mx, my);

        Player p = this.minecraft.player;
        if (p != null) {
            String coins = "Coins: " + p.coins;
            this.fontRenderer.render(coins, this.width / 2 - this.fontRenderer.getWidth(coins) / 2,
                    this.height - 40, 0xFFFF55);
        }
    }
}
