package net.classicremastered.minecraft.model;

public final class ModelManager {

    private HumanoidModel human = new HumanoidModel(0.0F);
    private HumanoidModel armoredHuman = new HumanoidModel(1.0F);
    private CreeperModel creeper = new CreeperModel();
    private SkeletonModel skeleton = new SkeletonModel();
    private final SkeletonTrapModel skeletonTrap = new SkeletonTrapModel();
    private BabySkeletonModel babySkeleton = new BabySkeletonModel();
    private ZombieModel zombie = new ZombieModel();
    private final FrostbiteModel frostbite = new FrostbiteModel();
    private final FrostbiteModel babyFrostbite = new FrostbiteModel("frostbite_baby", true);
    private final FrostbiteModel babyHusk = new FrostbiteModel("husk_baby", true);
    private final CowModel cow = new CowModel();
    private AnimalModel pig = new PigModel();
    private AnimalModel sheep = new SheepModel();
    private SpiderModel spider = new SpiderModel();
    private SheepFurModel sheepFur = new SheepFurModel();

    private TNTThrowerModel tntthrower = new TNTThrowerModel();
    private VillagerModel villager = new VillagerModel();
    private AnimalModel chicken = new ChickenModel();
    private IronGolemModel ironGolem = new IronGolemModel();

    private BabyZombieModel zombieBaby = new BabyZombieModel();
    private BabyVillagerModel villagerBaby = new BabyVillagerModel();
    private BabyHumanoidArmorModel armoredHumanBaby = new BabyHumanoidArmorModel();

    // NEW
    private EndermanModel enderman = new EndermanModel();

    // NEW: Husk model (zombie variant without headwear)
    private HuskModel husk = new HuskModel();

    // NEW: Bee model
    private BeeModel bee = new BeeModel();

    // NEW: ShulkerMan model
    private ModelShulkerMan shulkerman = new ModelShulkerMan();

    // NEW: LeggedMan model
    private LeggedMan leggedman = new LeggedMan();

    // NEW: LongNeckMan model
    private LongNeckManModel longneckman = new LongNeckManModel();

    // NEW: Agent model
    private AgentModel agent = new AgentModel();
    private BlueForestGolemModel blueForestGolem = new BlueForestGolemModel();
    private IronmockModel ironmock = new IronmockModel();
    private HouseVandalizerModel houseVandalizer = new HouseVandalizerModel();
    private StickFigureModel stickFigure = new StickFigureModel();

    public final Model getModel(String key) {
        if (key == null) return null;
        switch (key) {
            case "ironmock":
                return this.ironmock;
            case "housevandalizer":
                return this.houseVandalizer;
            case "stickfigure":
                return this.stickFigure;
            case "humanoid":
                return this.human;
            case "humanoid.armor":
                return this.armoredHuman;
            case "creeper":
                return this.creeper;
            case "skeleton_baby":
                return babySkeleton;
            case "skeleton_baby.armor":
                return armoredHumanBaby;
            case "skeleton":
            case "skeleton_teen":
                return this.skeleton;
            case "skeleton_trap":
                return skeletonTrap;
            case "frostbite_baby":
                return babyFrostbite;
            case "husk_baby":
                return babyHusk;
            case "frostbite":
            case "frostbite_teen":
                return frostbite;
            case "zombie":
            case "zombie_teen":
                return this.zombie;
            case "zombiebuilder":
                return this.zombie;
            case "irongolem":
                return this.ironGolem;

            case "cow":
                return cow;
            case "pig":
                return this.pig;
            case "sheep":
                return this.sheep;
            case "spider":
                return this.spider;
            case "sheep.fur":
                return this.sheepFur;
            case "chicken":
                return this.chicken;
            case "villager":
                return this.villager;
            case "tntthrower":
                return this.tntthrower;

            case "zombie_baby":
            case "babyzombie":
            case "bzombie":
                return this.zombieBaby;

            case "villager_baby":
            case "babyvillager":
            case "bvillager":
                return this.villagerBaby;

            case "humanoid.armor.baby":
                return this.armoredHumanBaby;

            // NEW
            case "enderman":
                return this.enderman;

            // NEW: Husk mapping
            case "husk":
            case "husk_teen":
                return this.husk;

            // NEW: Bee mapping
            case "bee":
                return this.bee;

            // NEW: ShulkerMan mapping
            case "shulkerman":
                return this.shulkerman;

            // NEW: LeggedMan mapping
            case "leggedmonster":
                return this.leggedman;

            // NEW: LongNeckMan mapping
            case "longneckman":
                return this.longneckman;

            // NEW: Agent mapping
            case "agent":
                return this.agent;
            case "blue_forest_golem":
                return this.blueForestGolem;

            default:
                return null;
        }
    }
}
