package net.classicremastered.minecraft.crafting;

import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;

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
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int id = ingredients[i + j * width];
                int gridId = grid[(startX + i) + (startY + j) * gridWidth];
                if (id == -1) continue; // Any? Or empty? usually -1 in ingredient means empty. 
                // Wait, if ingredient is -1, it expects empty or anything?
                // Usually recipe ingredients are non-empty.
                // If ingredient is 0/-1 (Empty), grid must be empty.
                if (id != gridId && (id != -1 || gridId != 0 && gridId != -1)) {
                     // Check if both are considered empty (-1 or 0)
                     if (!isEmpty(id) || !isEmpty(gridId)) return false;
                }
            }
        }
        return true;
    }

    private boolean isEmpty(int id) {
        return id <= 0;
    }
    
    public int getResultId() { return resultId; }
    public int getResultCount() { return resultCount; }
}
