package net.classicremastered.minecraft.crafting;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class CraftingManager {
    private static final CraftingManager instance = new CraftingManager();
    private List<Recipe> recipes = new ArrayList<Recipe>();

    public static final CraftingManager getInstance() {
        return instance;
    }

    private CraftingManager() {
        System.out.println("Initializing CraftingManager...");
        net.classicremastered.minecraft.level.itemstack.ItemRegistry.bootstrap();

        // --- Register Recipes here ---

        // Log -> 4 Planks (Shapeless / 1x1)
        addRecipe(new ItemStack(Block.WOOD.id, 4), "#", '#', Block.LOG.id);

        // 4 Planks -> Workbench
        addRecipe(new ItemStack(Block.WORKBENCH.id, 1), "##", "##", '#', Block.WOOD.id);

        // 8 Cobble -> Furnace
        addRecipe(
                new ItemStack(Block.FURNACE.id, 1), "###", "# #", "###", '#', Block.COBBLESTONE.id);

        // 2 Planks -> 4 Sticks (Example)
        int stick = Equipment.STICK.id + 256;
        addRecipe(new ItemStack(Block.SANDSTONE.id, 1), "ss", "ss", 's', Block.SAND.id);
        addRecipe(new ItemStack(Block.CUT_SANDSTONE.id, 4), "ss", "ss", 's', Block.SANDSTONE.id);
        addRecipe(
                new ItemStack(Block.CHISELED_SANDSTONE.id, 1),
                "s",
                "s",
                's',
                Block.SANDSTONE_SLAB.id);
        addRecipe(new ItemStack(Block.SANDSTONE_SLAB.id, 6), "sss", 's', Block.SANDSTONE.id);
        addRecipe(
                new ItemStack(Block.SANDSTONE_STAIRS.id, 4),
                "s  ",
                "ss ",
                "sss",
                's',
                Block.SANDSTONE.id);
        addRecipe(new ItemStack(Block.CHEST.id, 1), "www", "w w", "www", 'w', Block.WOOD.id);
        addRecipe(
                new ItemStack(Block.LEVER.id, 1), "s", "c", 's', stick, 'c', Block.COBBLESTONE.id);
        addRecipe(new ItemStack(Block.STONE_BUTTON.id, 1), "s", 's', Block.STONE.id);
        addRecipe(new ItemStack(Block.STONE_PRESSURE_PLATE.id, 1), "ss", 's', Block.STONE.id);
        addRecipe(
                new ItemStack(Block.REDSTONE_TORCH.id, 1),
                "r",
                "s",
                'r',
                net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256,
                's',
                stick);
        addRecipe(
                new ItemStack(Block.REDSTONE_BLOCK.id, 1),
                "rrr",
                "rrr",
                "rrr",
                'r',
                net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256,
                        9),
                "r",
                'r',
                Block.REDSTONE_BLOCK.id);
        addRecipe(
                new ItemStack(Block.REPEATER.id, 1),
                "trt",
                "sss",
                't',
                Block.REDSTONE_TORCH.id,
                'r',
                net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256,
                's',
                Block.STONE.id);
        addRecipe(
                new ItemStack(Block.PISTON.id, 1),
                "www",
                "cic",
                "crc",
                'w',
                Block.WOOD.id,
                'c',
                Block.COBBLESTONE.id,
                'i',
                Block.IRON_ORE.id,
                'r',
                net.classicremastered.minecraft.level.itemstack.Item.REDSTONE_DUST_ID + 256);
        addRecipe(
                new ItemStack(Block.TORCH.id, 4),
                "c",
                "s",
                'c',
                net.classicremastered.minecraft.level.itemstack.Item.COAL_ID + 256,
                's',
                stick);
        addRecipe(new ItemStack(stick, 4), "#", "#", '#', Block.WOOD.id);
        int[] materials = {
            Block.WOOD.id,
            Block.COBBLESTONE.id,
            Block.IRON_ORE.id,
            Block.DIAMOND_ORE_NEW.id,
            Block.GOLD_ORE.id
        };
        for (int tier = 0; tier < materials.length; tier++) {
            addRecipe(
                    new ItemStack(Equipment.SWORDS[tier] + 256, 1),
                    "#",
                    "#",
                    "s",
                    '#',
                    materials[tier],
                    's',
                    stick);
            addRecipe(
                    new ItemStack(Equipment.AXES[tier] + 256, 1),
                    "##",
                    "#s",
                    " s",
                    '#',
                    materials[tier],
                    's',
                    stick);
        }
        addRecipe(
                new ItemStack(Equipment.DIAMOND_SHOVEL + 256, 1),
                "#",
                "s",
                "s",
                '#',
                Block.DIAMOND_ORE_NEW.id,
                's',
                stick);
        addRecipe(
                new ItemStack(Equipment.GOLDEN_SHOVEL + 256, 1),
                "#",
                "s",
                "s",
                '#',
                Block.GOLD_ORE.id,
                's',
                stick);
        for (int t = 0; t < 2; t++) {
            int mat =
                    t == 0
                            ? net.classicremastered.minecraft.level.itemstack.Equipment.LEATHER.id
                                    + 256
                            : Block.IRON_ORE.id;
            int[] armor = net.classicremastered.minecraft.level.itemstack.Equipment.ARMOR[t];
            addRecipe(new ItemStack(armor[0] + 256, 1), "###", "# #", '#', mat);
            addRecipe(new ItemStack(armor[1] + 256, 1), "# #", "###", "###", '#', mat);
            addRecipe(new ItemStack(armor[2] + 256, 1), "###", "# #", "# #", '#', mat);
            addRecipe(new ItemStack(armor[3] + 256, 1), "# #", "# #", '#', mat);
        }
        addRecipe(
                new ItemStack(Item.BOW.id + 256, 1),
                " st",
                "s t",
                " st",
                's',
                stick,
                't',
                Equipment.STRING.id + 256);
        addRecipe(
                new ItemStack(Equipment.ARROW.id + 256, 4),
                "#",
                "s",
                "f",
                '#',
                Block.STONE.id,
                's',
                stick,
                'f',
                Item.FEATHER.id + 256);

        // Sort recipes by size (largest first) to prioritize complex recipes
        Collections.sort(
                recipes,
                new Comparator<Recipe>() {
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
            if (args[i + 1] instanceof Integer) id = (Integer) args[i + 1];
            else if (args[i + 1] instanceof Block) id = ((Block) args[i + 1]).id;
            else if (args[i + 1] instanceof Item) id = ((Item) args[i + 1]).id;
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

    public static void consumeIngredients(int[] ids, int[] counts) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] > 0 && --counts[i] == 0) ids[i] = -1;
        }
    }

    public ItemStack findMatchingRecipe(int[] matrix, int matrixWidth) {
        for (Recipe r : recipes) {
            if (r.matches(matrix, matrixWidth)) { // Pass matrix width (e.g., 3 for 3x3 grid)
                return new ItemStack(r.resultId, r.resultCount);
            }
        }
        return null;
    }

    public List<Recipe> getRecipes() {
        return Collections.unmodifiableList(recipes);
    }

    // Helper class since ItemStack might not exist or be accessible easily
    public static class ItemStack {
        public int id;
        public int count;

        public ItemStack(int id, int count) {
            this.id = id;
            this.count = count;
        }
    }
}
