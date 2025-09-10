package net.classicremastered.minecraft.model;

public final class ModelManager {

   private HumanoidModel human         = new HumanoidModel(0.0F);
   private HumanoidModel armoredHuman  = new HumanoidModel(1.0F);
   private CreeperModel creeper        = new CreeperModel();
   private NewSkeletonModel skeleton   = new NewSkeletonModel();
   private ZombieModel zombie          = new ZombieModel();
   private AnimalModel pig             = new PigModel();
   private AnimalModel sheep           = new SheepModel();
   private SpiderModel spider          = new SpiderModel();
   private SheepFurModel sheepFur      = new SheepFurModel();

   private TNTThrowerModel tntthrower  = new TNTThrowerModel();
   private VillagerModel villager      = new VillagerModel();
   private AnimalModel chicken         = new ChickenModel();
   private IronGolemModel ironGolem = new IronGolemModel();

   private BabyZombieModel   zombieBaby    = new BabyZombieModel();
   private BabyVillagerModel villagerBaby  = new BabyVillagerModel();
   private BabyHumanoidArmorModel armoredHumanBaby = new BabyHumanoidArmorModel();

   // NEW
   private EndermanModel enderman     = new EndermanModel();

   // NEW: Husk model (zombie variant without headwear)
   private HuskModel husk             = new HuskModel();

   // NEW: Bee model
   private BeeModel bee               = new BeeModel();

   public final Model getModel(String key) {
      if (key == null) return null;
      switch (key) {
         case "humanoid":              return this.human;
         case "humanoid.armor":        return this.armoredHuman;
         case "creeper":               return this.creeper;
         case "skeleton":              return this.skeleton;
         case "zombie":                return this.zombie;
         case "zombiebuilder":         return this.zombie;
         case "irongolem": return this.ironGolem;

         case "pig":                   return this.pig;
         case "sheep":                 return this.sheep;
         case "spider":                return this.spider;
         case "sheep.fur":             return this.sheepFur;
         case "chicken":               return this.chicken;
         case "villager":              return this.villager;
         case "tntthrower":            return this.tntthrower;

         case "zombie_baby":
         case "babyzombie":
         case "bzombie":               return this.zombieBaby;

         case "villager_baby":
         case "babyvillager":
         case "bvillager":             return this.villagerBaby;

         case "humanoid.armor.baby":   return this.armoredHumanBaby;

         // NEW
         case "enderman":              return this.enderman;

         // NEW: Husk mapping
         case "husk":                  return this.husk;

         // NEW: Bee mapping
         case "bee":                   return this.bee;

         default:                      return null;
      }
   }
}
