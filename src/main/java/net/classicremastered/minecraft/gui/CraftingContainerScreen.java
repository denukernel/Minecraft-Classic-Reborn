package net.classicremastered.minecraft.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.classicremastered.minecraft.crafting.CraftingManager;
import net.classicremastered.minecraft.crafting.Recipe;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.OreMaterials;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.render.ShapeRenderer;
import org.lwjgl.opengl.GL11;

abstract class CraftingContainerScreen extends GuiScreen {
    protected static final int CRAFTING_PANEL_WIDTH = 176;
    protected static final int CRAFTING_PANEL_HEIGHT = 166;
    protected static final int CELL = 18;
    private static final int BOOK_WIDTH = 86;
    private static final int RECIPES_PER_PAGE = 24;
    private static final int PAGE_Y = 135;
    private static final int PREVIOUS_X = 8;
    private static final int NEXT_X = BOOK_WIDTH - 16;

    private boolean recipeBookOpen;
    private boolean craftableOnly;
    private int recipePage;
    private int dragSource = -1;
    private Recipe requirementPreview;

    protected final void beginStackDrag(int source) {
        dragSource = source;
    }

    protected final boolean finishStackDrag(int target, int button) {
        if (button != 0) return false;
        int source = dragSource;
        dragSource = -1;
        return source >= 0 && target >= 0 && source != target;
    }

    @Override
    public void onClose() {
        beginStackDrag(-1);
        requirementPreview = null;
        super.onClose();
    }

    protected final void drawStackCount(int count, int x, int y) {
        if (count <= 1) return;
        String text = Integer.toString(count);
        int textWidth = fontRenderer.getWidth(text);
        float scale = Math.min(1.0F, 16.0F / (textWidth + 1));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glTranslatef(x + 16, y + 16, 0);
            GL11.glScalef(scale, scale, 1);
            fontRenderer.render(text, -textWidth - 1, -9, 0xFFFFFF);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    protected final int craftingPanelLeft() {
        int shift = recipeBookOpen && bookFitsBesidePanel() ? BOOK_WIDTH + 4 : 0;
        return (width - CRAFTING_PANEL_WIDTH + shift) / 2;
    }

    private boolean bookFitsBesidePanel() {
        return width >= CRAFTING_PANEL_WIDTH + BOOK_WIDTH + 8;
    }

    private int bookLeft(int gridSize) {
        int panelLeft = craftingPanelLeft();
        if (bookFitsBesidePanel()) return panelLeft - BOOK_WIDTH - 4;
        return gridSize == 3 ? panelLeft + CRAFTING_PANEL_WIDTH - BOOK_WIDTH : panelLeft;
    }

    protected static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected final void drawSprite(String resource, int x, int y, int w, int h) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load(resource));
            ShapeRenderer shape = ShapeRenderer.instance;
            shape.begin();
            shape.vertexUV(x, y + h, 0, 0, 1);
            shape.vertexUV(x + w, y + h, 0, 1, 1);
            shape.vertexUV(x + w, y, 0, 1, 0);
            shape.vertexUV(x, y, 0, 0, 0);
            shape.end();
        } finally {
            GL11.glPopAttrib();
        }
    }

    protected final void drawRecipeBookButton(int mx, int my, int x, int y) {
        boolean hover = inside(mx, my, x, y, 20, 18);
        drawSprite(
                hover ? "/gui/recipe-book-button-highlighted.png" : "/gui/recipe-book-button.png",
                x,
                y,
                20,
                18);
    }

    protected final boolean clickRecipeBookButton(int mx, int my, int x, int y) {
        if (!inside(mx, my, x, y, 20, 18)) return false;
        recipeBookOpen = !recipeBookOpen;
        requirementPreview = null;
        return true;
    }

    protected final void drawRecipeBook(int y, int gridSize, int[] ids, int[] counts) {
        if (!recipeBookOpen) return;
        int x = bookLeft(gridSize);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            drawBox(x, y, x + BOOK_WIDTH, y + CRAFTING_PANEL_HEIGHT, 0xFF303030);
            drawString(
                    fontRenderer,
                    craftableOnly ? "Craftable" : "All recipes",
                    x + 6,
                    y + 6,
                    0xFFFFFF);
            List<Recipe> recipes = recipes(gridSize, ids, counts);
            int start = recipePage * RECIPES_PER_PAGE;
            for (int i = 0; i < RECIPES_PER_PAGE && start + i < recipes.size(); i++) {
                Recipe recipe = recipes.get(start + i);
                int sx = x + 6 + i % 4 * CELL;
                int sy = y + 20 + i / 4 * CELL;
                drawBox(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF494949);
                drawBox(sx, sy, sx + 16, sy + 16, 0xFF909090);
                drawRecipeItem(recipe.resultId, recipe.resultCount, sx, sy);
                if (!craftableOnly
                        && !hasIngredients(recipe, ingredientSlots(recipe, ids, counts))) {
                    drawString(fontRenderer, "X", sx + 1, sy + 1, 0xFF6565);
                }
            }
            drawString(
                    fontRenderer,
                    "<",
                    x + PREVIOUS_X,
                    y + PAGE_Y,
                    recipePage > 0 ? 0xFFFFFF : 0x888888);
            drawCenteredString(
                    fontRenderer,
                    Integer.toString(recipePage + 1),
                    x + BOOK_WIDTH / 2,
                    y + PAGE_Y,
                    0xFFFFFF);
            drawString(
                    fontRenderer,
                    ">",
                    x + NEXT_X,
                    y + PAGE_Y,
                    start + RECIPES_PER_PAGE < recipes.size() ? 0xFFFFFF : 0x888888);
        } finally {
            GL11.glPopAttrib();
        }
    }

    protected abstract void drawRecipeItem(int id, int count, int x, int y);

    protected final void clearRecipeRequirements() {
        requirementPreview = null;
    }

    protected final void drawRecipeRequirements(
            int gridX, int gridY, int gridSize, int[] ids, int[] counts, int mx, int my) {
        Recipe recipe = requirementPreview;
        if (recipe == null) return;
        int[] slots = ingredientSlots(recipe, ids, counts);
        int hovered = -1;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            for (int i = 0; i < recipe.ingredients.length; i++) {
                int ingredient = recipe.ingredients[i];
                if (ingredient <= 0) continue;
                int col = i % recipe.width, row = i / recipe.width;
                int x = gridX + col * CELL, y = gridY + row * CELL;
                if (!OreMaterials.matches(
                        ingredient, Item.inventoryId(ids[col + row * gridSize]))) {
                    drawBox(x, y, x + 16, y + 16, slots[i] < 0 ? 0xFFB78181 : 0xFFAAAAAA);
                    drawRecipeItem(ingredient, 1, x, y);
                }
                if (inside(mx, my, x, y, 16, 16)) hovered = ingredient;
            }
            drawMissingIngredients(gridSize, missingIngredients(recipe, slots));
            if (hovered > 0) ItemTooltip.draw(this, hovered, null, mx, my);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static Map<Integer, Integer> missingIngredients(Recipe recipe, int[] slots) {
        Map<Integer, Integer> missing = new LinkedHashMap<>();
        for (int i = 0; i < slots.length; i++) {
            int ingredient = recipe.ingredients[i];
            if (ingredient > 0 && slots[i] < 0) missing.merge(ingredient, 1, Integer::sum);
        }
        return missing;
    }

    private void drawMissingIngredients(int gridSize, Map<Integer, Integer> missing) {
        int bookX = bookLeft(gridSize);
        int bookY = (height - CRAFTING_PANEL_HEIGHT) / 2;
        if (missing.isEmpty()) {
            drawCenteredString(
                    fontRenderer, "Requirements", bookX + BOOK_WIDTH / 2, bookY + 151, 0xFFFFFF);
            return;
        }

        String heading = "Missing ingredients";
        List<String> lines = new ArrayList<>(missing.size());
        int textWidth = fontRenderer.getWidth(heading);
        for (var entry : missing.entrySet()) {
            String line = entry.getValue() + " x " + BlockSelectScreen.displayName(entry.getKey());
            lines.add(line);
            textWidth = Math.max(textWidth, fontRenderer.getWidth(line));
        }

        int boxWidth = textWidth + 10;
        int boxHeight = 15 + lines.size() * 9;
        int x = Math.max(3, Math.min(bookX + BOOK_WIDTH - boxWidth, width - boxWidth - 3));
        int y = Math.max(3, Math.min(bookY + CRAFTING_PANEL_HEIGHT + 4, height - boxHeight - 3));
        drawBox(x, y, x + boxWidth, y + boxHeight, 0xFF171717);
        drawBox(x + 1, y + 1, x + boxWidth - 1, y + boxHeight - 1, 0xFF303030);
        drawString(fontRenderer, heading, x + 5, y + 4, 0xFF9595);
        for (int i = 0; i < lines.size(); i++) {
            drawString(fontRenderer, lines.get(i), x + 5, y + 14 + i * 9, 0xFFFFFF);
        }
    }

    protected final boolean isRecipeBookArea(int mx, int my, int y, int gridSize) {
        return recipeBookOpen
                && inside(mx, my, bookLeft(gridSize), y, BOOK_WIDTH, CRAFTING_PANEL_HEIGHT);
    }

    protected final boolean clickRecipeBook(
            int mx,
            int my,
            int button,
            int y,
            int gridSize,
            int[] ids,
            int[] counts,
            boolean carrying) {
        if (!isRecipeBookArea(mx, my, y, gridSize)) return false;
        if (button != 0) return true;
        int x = bookLeft(gridSize);
        if (my < y + 18) {
            craftableOnly = !craftableOnly;
            recipePage = 0;
            clearRecipeRequirements();
        } else if (my >= y + PAGE_Y - 4) {
            int last = Math.max(0, (recipes(gridSize, ids, counts).size() - 1) / RECIPES_PER_PAGE);
            int previous = recipePage;
            if (inside(mx, my, x + PREVIOUS_X - 4, y + PAGE_Y - 4, 14, 16)) {
                recipePage = Math.max(0, recipePage - 1);
            } else if (inside(mx, my, x + NEXT_X - 4, y + PAGE_Y - 4, 14, 16)) {
                recipePage = Math.min(last, recipePage + 1);
            }
            if (recipePage != previous) {
                clearRecipeRequirements();
                playButtonClickSound();
            }
        } else if (!carrying && inside(mx, my, x + 6, y + 20, 4 * CELL, 6 * CELL)) {
            List<Recipe> recipes = recipes(gridSize, ids, counts);
            int index =
                    recipePage * RECIPES_PER_PAGE + (mx - x - 6) / CELL + (my - y - 20) / CELL * 4;
            if (index < recipes.size()) {
                Recipe recipe = recipes.get(index);
                requirementPreview = fillGrid(recipe, gridSize, ids, counts) ? null : recipe;
            }
        }
        return true;
    }

    private List<Recipe> recipes(int gridSize, int[] ids, int[] counts) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : CraftingManager.getInstance().getRecipes()) {
            if (recipe.width <= gridSize
                    && recipe.height <= gridSize
                    && (!craftableOnly
                            || hasIngredients(recipe, ingredientSlots(recipe, ids, counts)))) {
                result.add(recipe);
            }
        }
        recipePage = Math.min(recipePage, Math.max(0, (result.size() - 1) / RECIPES_PER_PAGE));
        return result;
    }

    private int[] ingredientSlots(Recipe recipe, int[] ids, int[] counts) {
        Inventory inventory = minecraft.player.inventory;
        int inventorySize = inventory.totalSize();
        int[] used = new int[inventorySize + ids.length];
        int[] slots = new int[recipe.ingredients.length];
        Arrays.fill(slots, -1);
        for (int i = 0; i < slots.length; i++) {
            int ingredient = recipe.ingredients[i];
            if (ingredient <= 0) continue;
            for (int slot = 0; slot < used.length; slot++) {
                boolean inInventory = slot < inventorySize;
                int count = inInventory ? inventory.getCount(slot) : counts[slot - inventorySize];
                int raw = inInventory ? inventory.getId(slot) : ids[slot - inventorySize];
                if (count > used[slot]
                        && (!inInventory || inventory.crate(slot) == null)
                        && OreMaterials.matches(ingredient, Item.inventoryId(raw))) {
                    slots[i] = slot;
                    used[slot]++;
                    break;
                }
            }
        }
        return slots;
    }

    private static boolean hasIngredients(Recipe recipe, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (recipe.ingredients[i] > 0 && slots[i] < 0) return false;
        }
        return true;
    }

    private boolean fillGrid(Recipe recipe, int gridSize, int[] ids, int[] counts) {
        if (recipe.matches(ids, gridSize)) return true;
        for (int id : ids) if (id > 0) return false;
        int[] slots = ingredientSlots(recipe, ids, counts);
        if (!hasIngredients(recipe, slots)) return false;
        Inventory inventory = minecraft.player.inventory;
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot < 0) continue;
            int grid = i % recipe.width + i / recipe.width * gridSize;
            int id = inventory.getId(slot);
            int remaining = inventory.getCount(slot) - 1;
            ids[grid] = id;
            counts[grid] = 1;
            inventory.setIdCount(slot, remaining == 0 ? -1 : id, remaining);
        }
        return true;
    }
}
