package net.classicremastered.minecraft.crafting;

import net.classicremastered.minecraft.level.itemstack.BoatItem;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.OreMaterials;
import net.classicremastered.minecraft.level.itemstack.StructureLocatorItem.Filter;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.itemstack.TurretUpgrade;
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

        addRecipe(new ItemStack(Block.SNOW_BLOCK.id, 1), "ss", "ss", 's', Item.SNOWBALL.id + 256);

        // --- Register Recipes here ---

        addRecipe(
                new ItemStack(Block.LADDER.id, 3),
                "s s",
                "sss",
                "s s",
                's',
                Equipment.STICK.id + 256);

        // Log -> 4 Planks (Shapeless / 1x1)
        addRecipe(new ItemStack(Block.WOOD.id, 4), "#", '#', Block.LOG.id);
        addRecipe(new ItemStack(BoatItem.ID + 256, 1), "w w", "www", 'w', Block.WOOD.id);

        int upgrade =
                net.classicremastered.minecraft.level.itemstack.TurretItems.FIRST_UPGRADE + 256;
        for (int i = 0; i < 4; i++) {
            addRecipe(
                    new ItemStack(upgrade + i, 1),
                    "iii",
                    "ici",
                    "iii",
                    'i',
                    net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                    'c',
                    i == 0 ? Block.COBBLESTONE.id : upgrade + i - 1);
        }
        addRecipe(
                new ItemStack(upgrade + 4, 1),
                "iri",
                'i',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                'r',
                Item.REDSTONE_DUST_ID + 256);
        addRecipe(
                new ItemStack(upgrade + 5, 1),
                "rur",
                'u',
                upgrade + 4,
                'r',
                Item.REDSTONE_DUST_ID + 256);
        addRecipe(
                new ItemStack(upgrade + 6, 1),
                "ici",
                'i',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                'c',
                Block.CHEST.id);

        int baseTurret = net.classicremastered.minecraft.level.itemstack.TurretItems.TURRET + 256;
        int iron = net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256;
        int redstone = Item.REDSTONE_DUST_ID + 256;
        int locator = Filter.ALL.id + 256;
        addRecipe(
                new ItemStack(locator, 1),
                "igi",
                "iri",
                " i ",
                'i',
                iron,
                'g',
                Block.GLASS.id,
                'r',
                redstone);
        addRecipe(
                new ItemStack(Filter.PYRAMID.id + 256, 1),
                "ls",
                'l',
                locator,
                's',
                Block.SANDSTONE.id);
        addRecipe(new ItemStack(Filter.HOUSE.id + 256, 1), "lw", 'l', locator, 'w', Block.WOOD.id);
        addRecipe(
                new ItemStack(Filter.TOWER.id + 256, 1),
                "lc",
                'l',
                locator,
                'c',
                Block.COBBLESTONE.id);
        addRecipe(new ItemStack(316, 1), "itw", 'i', iron, 't', baseTurret, 'w', Block.WOOD.id);
        addRecipe(
                new ItemStack(317, 1),
                "its",
                'i',
                iron,
                't',
                baseTurret,
                's',
                Equipment.STRING.id + 256);
        addRecipe(
                new ItemStack(318, 1),
                "rte",
                'r',
                redstone,
                't',
                baseTurret,
                'e',
                Item.ENDER_PEARL.id + 256);
        addRecipe(
                new ItemStack(319, 1), "rtg", 'r', redstone, 't', baseTurret, 'g', Block.GLASS.id);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.mob.TurretVariant.ROVER.item + 256, 1),
                "iti",
                "www",
                'i',
                iron,
                't',
                baseTurret,
                'w',
                Block.WOOD.id);
        addRecipe(new ItemStack(352, 1), "iti", " r ", 'i', iron, 't', baseTurret, 'r', redstone);
        addRecipe(new ItemStack(353, 1), "iai", " b ", 'i', iron, 'a', 316, 'b', 352);
        addRecipe(new ItemStack(354, 8), "ici", 'i', iron, 'c', Item.COAL_ID + 256);
        addRecipe(new ItemStack(321, 4), "ii", " s", 'i', iron, 's', Equipment.STICK.id + 256);
        addRecipe(new ItemStack(322, 4), "br", 'b', 304, 'r', redstone);

        addRecipe(new ItemStack(326, 1), "grg", 'g', Block.GLASS.id, 'r', redstone);
        addRecipe(
                new ItemStack(327, 1),
                "drd",
                'd',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.DIAMOND + 256,
                'r',
                redstone);

        // Tier 1 crossbow turret and supplies
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.TurretItems.TURRET + 256,
                        1),
                " b ",
                "wsw",
                "ccc",
                'b',
                Item.BOW.id + 256,
                'w',
                Block.WOOD.id,
                's',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                'c',
                Block.COBBLESTONE.id);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.TurretItems.BOLT + 256, 4),
                "i",
                "s",
                'i',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                's',
                Equipment.STICK.id + 256);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.TurretItems.REPAIR + 256,
                        1),
                "ir",
                'i',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
                'r',
                Item.REDSTONE_DUST_ID + 256);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.TurretItems.CONTROL + 256,
                        1),
                "gr",
                "ii",
                'g',
                Block.GLASS.id,
                'r',
                Item.REDSTONE_DUST_ID + 256,
                'i',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256);
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.TurretItems.CRATE + 256, 1),
                "www",
                "wcw",
                "www",
                'w',
                Block.WOOD.id,
                'c',
                Block.CHEST.id);
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
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
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
            net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_IRON + 256,
            net.classicremastered.minecraft.level.itemstack.OreMaterials.DIAMOND + 256,
            net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_GOLD + 256
        };
        for (int tier = 0; tier < materials.length; tier++) {
            addRecipe(
                    new ItemStack(Equipment.PICKAXES[tier] + 256, 1),
                    "###",
                    " s ",
                    " s ",
                    '#',
                    materials[tier],
                    's',
                    stick);
            if (tier < 3)
                addRecipe(
                        new ItemStack(Equipment.SHOVELS[tier] + 256, 1),
                        "#",
                        "s",
                        "s",
                        '#',
                        materials[tier],
                        's',
                        stick);
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
                net.classicremastered.minecraft.level.itemstack.OreMaterials.DIAMOND + 256,
                's',
                stick);
        addRecipe(
                new ItemStack(Equipment.GOLDEN_SHOVEL + 256, 1),
                "#",
                "s",
                "s",
                '#',
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_GOLD + 256,
                's',
                stick);
        int[] armorMaterials = {
            Equipment.LEATHER.id + 256,
            OreMaterials.RAW_IRON + 256,
            OreMaterials.RAW_GOLD + 256,
            OreMaterials.DIAMOND + 256,
            Block.WOOD.id,
            Block.COBBLESTONE.id
        };
        addRecipe(
                new ItemStack(
                        net.classicremastered.minecraft.level.itemstack.SaddleItem.ID + 256, 1),
                " L ",
                "LIL",
                'L',
                Equipment.LEATHER.id + 256,
                'I',
                OreMaterials.RAW_IRON + 256);
        for (int t = 0; t < armorMaterials.length; t++) {
            int mat = armorMaterials[t];
            int[] armor = Equipment.ARMOR[t];
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

        addTurretUpgrade(TurretUpgrade.ARMOR_I, Block.IRON_BLOCK.id);
        addTurretUpgrade(TurretUpgrade.ARMOR_II, Block.IRON_BLOCK.id);
        addTurretUpgrade(TurretUpgrade.POWER_I, Equipment.ARROW.id + 256);
        addTurretUpgrade(TurretUpgrade.POWER_II, Equipment.ARROW.id + 256);
        addTurretUpgrade(
                TurretUpgrade.RECYCLER_I,
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_GOLD + 256);
        addTurretUpgrade(
                TurretUpgrade.RECYCLER_II,
                net.classicremastered.minecraft.level.itemstack.OreMaterials.RAW_GOLD + 256);
        addTurretUpgrade(TurretUpgrade.TRACKING_I, Block.GLASS.id);
        addTurretUpgrade(TurretUpgrade.TRACKING_II, Block.GLASS.id);
        addTurretUpgrade(TurretUpgrade.REPAIR, TurretItems.REPAIR + 256);
        addTurretUpgrade(TurretUpgrade.FIRE_SEAL, Block.OBSIDIAN.id);
        addTurretUpgrade(TurretUpgrade.WATER_SEAL, Block.SPONGE.id);
        addTurretUpgrade(TurretUpgrade.SHOCK_ABSORBER, Block.WHITE_WOOL.id);

        addRecipe(new ItemStack(Block.STONE_BRICKS.id, 4), "ss", "ss", 's', Block.STONE.id);
        addStairs(Block.SANDSTONE_STAIRS, Block.SANDSTONE);
        addStairs(Block.OAK_STAIRS, Block.WOOD);
        addStairs(Block.COBBLESTONE_STAIRS, Block.COBBLESTONE);
        addStairs(Block.STONE_BRICK_STAIRS, Block.STONE_BRICKS);

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

    private void addStairs(Block stairs, Block material) {
        addRecipe(new ItemStack(stairs.id, 4), "s  ", "ss ", "sss", 's', material.id);
    }

    private void addTurretUpgrade(TurretUpgrade upgrade, int material) {
        int core = upgrade.previous == null ? TurretItems.CONTROL : upgrade.previous.item;
        addRecipe(
                new ItemStack(upgrade.item + 256, 1),
                " m ",
                "rcr",
                " m ",
                'm',
                material,
                'r',
                Item.REDSTONE_DUST_ID + 256,
                'c',
                core + 256);
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
