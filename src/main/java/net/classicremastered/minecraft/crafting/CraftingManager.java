package net.classicremastered.minecraft.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.itemstack.Item;

public class CraftingManager {
    private static final CraftingManager instance = new CraftingManager();
    private List<Recipe> recipes = new ArrayList<Recipe>();

    public static final CraftingManager getInstance() {
        return instance;
    }

    private CraftingManager() {
        System.out.println("Initializing CraftingManager...");
        
        // --- Register Recipes here ---

        // Log -> 4 Planks (Shapeless / 1x1)
        addRecipe(new ItemStack(Block.WOOD.id, 4), "#", '#', Block.LOG.id);

        // 4 Planks -> Workbench
        addRecipe(new ItemStack(Block.WORKBENCH.id, 1), "##", "##", '#', Block.WOOD.id);

        // 8 Cobble -> Furnace
        addRecipe(new ItemStack(Block.FURNACE.id, 1), "###", "# #", "###", '#', Block.COBBLESTONE.id);
        
        // 2 Planks -> 4 Sticks (Example)
        addRecipe(new ItemStack(Item.FEATHER.id, 4), "#", "#", '#', Block.WOOD.id);
        
        // Sort recipes by size (largest first) to prioritize complex recipes
        Collections.sort(recipes, new Comparator<Recipe>() {
            public int compare(Recipe r1, Recipe r2) {
                return (r2.width * r2.height) - (r1.width * r1.height);
            }
        });
        System.out.println(recipes.size() + " recipes loaded.");
    }
    
    public void addRecipe(ItemStack result, Object... args) {
        String shape = "";
        int i = 0;
        int width = 0;
        int height = 0;
        
        if (args[i] instanceof String[]) { // array of strings?
            String[] s = (String[]) args[i++];
            for (String l : s) {
                height++;
                width = l.length(); // assumes uniform width
                shape += l;
            }
        } else {
            while (args[i] instanceof String) {
                String s = (String) args[i++];
                height++;
                width = s.length();
                shape += s;
            }
        }
        
        HashMap<Character, Integer> map = new HashMap<Character, Integer>();
        for (; i < args.length; i += 2) {
            Character c = (Character) args[i];
            Integer id = 0;
            if (args[i+1] instanceof Integer) id = (Integer) args[i+1];
            else if (args[i+1] instanceof Block) id = ((Block) args[i+1]).id;
            else if (args[i+1] instanceof Item) id = ((Item) args[i+1]).id;
            map.put(c, id);
        }
        
        int[] ingredients = new int[width * height];
        for (int x = 0; x < width * height; x++) {
            char c = shape.charAt(x);
            if (map.containsKey(c)) {
                ingredients[x] = map.get(c);
            } else {
                ingredients[x] = -1; // Empty/Space
            }
        }
        
        recipes.add(new Recipe(width, height, ingredients, result.id, result.count));
    }

    public ItemStack findMatchingRecipe(int[] matrix, int matrixWidth) {
        for (Recipe r : recipes) {
            if (r.matches(matrix, matrixWidth)) { // Pass matrix width (e.g., 3 for 3x3 grid)
                 return new ItemStack(r.resultId, r.resultCount);
            }
        }
        return null;
    }
    
    // Helper class since ItemStack might not exist or be accessible easily
    public static class ItemStack {
        public int id;
        public int count;
        public ItemStack(int id, int count) { this.id = id; this.count = count; }
    }
}
