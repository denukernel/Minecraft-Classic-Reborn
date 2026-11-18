package net.classicremastered.minecraft.crafting;

import net.classicremastered.minecraft.level.itemstack.Item;

public class Recipe {
    public final int width;
    public final int height;
    public final int[] ingredients; // IDs
    private final int[] ingredientCounts;
    public final int resultId;
    public final int resultCount;

    public Recipe(int width, int height, int[] ingredients, int resultId, int resultCount) {
        this(width, height, ingredients, null, resultId, resultCount);
    }

    public Recipe(
            int width,
            int height,
            int[] ingredients,
            int[] ingredientCounts,
            int resultId,
            int resultCount) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.ingredientCounts = ingredientCounts;
        this.resultId = resultId;
        this.resultCount = resultCount;
    }

    public int ingredientCount(int index) {
        if (ingredients[index] <= 0) return 0;
        return ingredientCounts == null ? 1 : ingredientCounts[index];
    }

    public boolean matches(int[] grid, int[] counts, int gridWidth) {
        return match(grid, counts, gridWidth, false);
    }

    public boolean consume(int[] grid, int[] counts, int gridWidth) {
        return match(grid, counts, gridWidth, true);
    }

    private boolean match(int[] grid, int[] counts, int gridWidth, boolean consume) {
        for (int x = 0; x <= gridWidth - width; x++) {
            for (int y = 0; y <= gridWidth - height; y++) {
                for (int mirror = 0; mirror < 2; mirror++) {
                    if (!checkMatch(grid, counts, x, y, gridWidth, mirror != 0)) continue;
                    if (consume) {
                        for (int ry = 0; ry < height; ry++) {
                            for (int rx = 0; rx < width; rx++) {
                                int ingredient = ry * width + (mirror == 0 ? rx : width - 1 - rx);
                                int slot = x + rx + (y + ry) * gridWidth;
                                int amount = ingredientCount(ingredient);
                                if (amount > 0 && (counts[slot] -= amount) == 0) grid[slot] = -1;
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkMatch(
            int[] grid, int[] counts, int startX, int startY, int gridWidth, boolean mirror) {
        for (int y = 0; y < gridWidth; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int rx = x - startX, ry = y - startY;
                int ingredient =
                        rx >= 0 && rx < width && ry >= 0 && ry < height
                                ? (mirror ? width - 1 - rx : rx) + ry * width
                                : -1;
                int expected = ingredient >= 0 ? ingredients[ingredient] : -1;
                int slot = x + y * gridWidth;
                int actual = Item.inventoryId(grid[slot]);
                if (expected <= 0 && actual <= 0) continue;
                if (ingredient < 0
                        || !net.classicremastered.minecraft.level.itemstack.OreMaterials.matches(
                                expected, actual)
                        || counts[slot] < ingredientCount(ingredient)) return false;
            }
        }
        return true;
    }

    public int getResultId() {
        return resultId;
    }

    public int getResultCount() {
        return resultCount;
    }
}
