package net.classicremastered.minecraft.crafting;

import net.classicremastered.minecraft.level.itemstack.Item;

public class Recipe {
    public final int width;
    public final int height;
    public final int[] ingredients; // IDs
    public final int resultId;
    public final int resultCount;

    public Recipe(int width, int height, int[] ingredients, int resultId, int resultCount) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.resultId = resultId;
        this.resultCount = resultCount;
    }

    public boolean matches(int[] grid, int gridWidth) {
        // Simple check for shapeless-like matching or exact matching
        // For now, let's implement exact matching within the grid.
        // But since we want to support 2x2 in 3x3, we need to scan.

        for (int x = 0; x <= gridWidth - width; x++) {
            for (int y = 0; y <= gridWidth - height; y++) {
                if (checkMatch(grid, x, y, gridWidth)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkMatch(int[] grid, int startX, int startY, int gridWidth) {
        for (boolean mirror : new boolean[]{false, true}) {
            boolean match = true;
            for (int y = 0; y < gridWidth; y++)
                for (int x = 0; x < gridWidth; x++) {
                    int rx = x - startX, ry = y - startY;
                    int expected = rx >= 0 && rx < width && ry >= 0 && ry < height
                            ? ingredients[(mirror ? width - 1 - rx : rx) + ry * width]
                            : -1;
                    int actual = Item.inventoryId(grid[x + y * gridWidth]);
                    if (!net.classicremastered.minecraft.level.itemstack.OreMaterials.matches(
                                    expected, actual)
                            && !(isEmpty(expected) && isEmpty(actual))) match = false;
                }
            if (match)
                return true;
        }
        return false;
    }

    private boolean isEmpty(int id) {
        return id <= 0;
    }

    public int getResultId() {
        return resultId;
    }

    public int getResultCount() {
        return resultCount;
    }
}
