package net.classicremastered.minecraft.level.itemstack;

import java.util.Random;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Defender.Skill;
import net.classicremastered.minecraft.mob.HumanoidMob;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;

public final class SpawnEggLoadout {
    public enum Species {
        DEFENDER("Defender"), ZOMBIE("Zombie"), SKELETON("Skeleton");

        public final String label;

        Species(String label) {
            this.label = label;
        }

        public int eggId() {
            return ItemRegistry.spawnEggInventoryId(label);
        }
    }

    public static final int WEAPON_SLOT = 4;
    public static final int ITEM_SLOT = 5;
    private final Species species;
    private final int[] items = new int[6];
    private final int[] skillRanks = new int[Skill.values().length];
    private final int arrowCount;
    private boolean randomizeSkills = true;
    private boolean randomizeCombatExperience = true;

    public SpawnEggLoadout(Species species, Random random) {
        ItemRegistry.bootstrap();
        this.species = species;
        for (int slot = 0; slot < WEAPON_SLOT; slot++) {
            if (species == Species.DEFENDER || random.nextBoolean()) {
                items[slot] = Equipment.ARMOR[random.nextInt(Equipment.ARMOR.length)][slot] + 256;
            }
        }
        items[WEAPON_SLOT] = rollWeapon(species, random);
        if (species == Species.DEFENDER) {
            Item[] supplies = {Item.APPLE, Item.BUCKET_WATER, Item.ENDER_PEARL};
            items[ITEM_SLOT] = supplies[random.nextInt(supplies.length)].id + 256;
            for (Skill skill : Skill.values()) {
                skillRanks[skill.ordinal()] = switch (skill) {
                    case VITALITY, GUARD, COMBAT -> random.nextInt(Defender.MAX_SKILL_LEVEL + 1);
                    case COMBAT_EXPERIENCE -> 1 + random.nextInt(Defender.MAX_SKILL_LEVEL);
                    case WATER_CLUTCH -> items[ITEM_SLOT] == Item.BUCKET_WATER.id + 256 ? 1 : 0;
                    case ENDER_PEARL_USE -> items[ITEM_SLOT] == Item.ENDER_PEARL.id + 256 ? 1 : 0;
                };
            }
        }
        arrowCount = species == Species.DEFENDER && items[WEAPON_SLOT] == Item.BOW.id + 256
                ? 8 + random.nextInt(25)
                : 0;
    }

    private static int rollWeapon(Species species, Random random) {
        if (species == Species.SKELETON)
            return Item.BOW.id + 256;
        if (species == Species.DEFENDER && random.nextInt(4) == 0)
            return Item.BOW.id + 256;
        if (species == Species.ZOMBIE && random.nextInt(4) == 0)
            return 0;
        int[] weapons = random.nextBoolean() ? Equipment.SWORDS : Equipment.AXES;
        return weapons[random.nextInt(weapons.length)] + 256;
    }

    public Species species() {
        return species;
    }

    public int item(int slot) {
        return items[slot];
    }

    public int arrowCount() {
        return arrowCount;
    }

    public void setProgressOptions(boolean skills, boolean combatExperience) {
        randomizeSkills = skills;
        randomizeCombatExperience = combatExperience;
    }

    public int skillRank(Skill skill) {
        int rank = skillRanks[skill.ordinal()];
        return switch (skill) {
            case VITALITY, GUARD, COMBAT -> randomizeSkills ? rank : 0;
            case COMBAT_EXPERIENCE -> randomizeCombatExperience ? rank : 0;
            case WATER_CLUTCH, ENDER_PEARL_USE -> rank;
        };
    }

    public Mob createMob(Level level, float x, float y, float z) {
        short id = MobRegistry.idOfName(species.label);
        Mob mob = MobRegistry.create(id, level, x, y, z);
        if (!(mob instanceof HumanoidMob humanoid) || mob.getRegistryId() != id)
            return null;
        humanoid.equipmentRolled = true;
        humanoid.legacyArmor = humanoid.helmet = humanoid.armor = false;
        // Skeletons already carry a bow through their normal model and AI.
        int endSlot = species == Species.SKELETON ? WEAPON_SLOT : ITEM_SLOT;
        for (int slot = 0; slot < endSlot; slot++) {
            Equipment.equip(humanoid, slot, items[slot], 0);
        }
        if (humanoid instanceof Defender defender) {
            defender.pack().setIdCount(0, items[ITEM_SLOT], 1);
            if (arrowCount > 0) {
                defender.pack().setIdCount(1, Equipment.ARROW.id + 256, arrowCount);
            }
            defender.initializeSpawnSkills(this::skillRank);
        }
        return humanoid;
    }
}
