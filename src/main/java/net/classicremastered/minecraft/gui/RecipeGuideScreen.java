package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.crafting.*;
import net.classicremastered.minecraft.level.itemstack.Equipment;

public final class RecipeGuideScreen extends InventoryScreen {
    private int page;
    private final GuiScreen previous;
    public RecipeGuideScreen(GuiScreen parent) {
        previous = parent;
    }

    @Override
    public void onOpen() {
        super.onOpen();
    }

    @Override
    public void render(int mx, int my) {
        var recipes = CraftingManager.getInstance().getRecipes();
        var r = recipes.get(page);
        int x = width / 2 - 116, y = height / 2 - 102;
        drawFadingBox(x, y, x + 232, y + 204, 0xF0303030, 0xF0505050);
        drawCenteredString(fontRenderer, "Recipes " + (page + 1) + "/" + recipes.size(), width / 2, y + 9, 0xFFFFFF);
        drawCenteredString(fontRenderer, BlockSelectScreen.displayName(r.resultId) + " x" + r.resultCount, width / 2,
                y + 27, 0xFFFF80);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                int sx = x + 16 + col * 20, sy = y + 47 + row * 20;
                drawBox(sx, sy, sx + 18, sy + 18, 0xFF222222);
                if (col < r.width && row < r.height && r.ingredients[row * r.width + col] > 0)
                    drawIdIcon(r.ingredients[row * r.width + col], sx + 1, sy + 1);
            }
        drawString(fontRenderer, ">", x + 92, y + 71, 0xFFFFFF);
        drawIdIcon(r.resultId, x + 115, y + 67);
        drawString(fontRenderer, Equipment.description(r.resultId), x + 11, y + 118, 0xFFFFFF);
        java.util.LinkedHashSet<Integer> mats = new java.util.LinkedHashSet<>();
        for (int id : r.ingredients)
            if (id > 0)
                mats.add(id);
        int yy = y + 134;
        for (int id : mats) {
            drawString(fontRenderer, BlockSelectScreen.displayName(id), x + 11, yy, 0xDDDDDD);
            yy += 11;
        }
        drawString(fontRenderer, "Craft at a workbench", x + 11, y + 171, 0xFFFFFF);
        drawString(fontRenderer, "Previous", x + 10, y + 190, 0xFFFF80);
        drawString(fontRenderer, "Back", x + 103, y + 190, 0xFFFF80);
        drawString(fontRenderer, "Next", x + 193, y + 190, 0xFFFF80);
    }

    @Override
    protected void onMouseClick(int mx, int my, int button) {
        int x = width / 2 - 116, y = height / 2 - 102;
        if (button != 0 || my < y + 184 || my >= y + 204)
            return;
        int n = CraftingManager.getInstance().getRecipes().size();
        if (mx >= x && mx < x + 75)
            page = (page + n - 1) % n;
        else if (mx >= x + 175 && mx < x + 232)
            page = (page + 1) % n;
        else if (mx >= x + 85 && mx < x + 150)
            minecraft.setCurrentScreen(previous);
    }

    @Override
    protected void onKeyPress(char ch, int key) {
        if (key == 1)
            minecraft.setCurrentScreen(previous);
        else if (key == minecraft.settings.inventoryKey.key)
            minecraft.setCurrentScreen(null);
    }
}
