package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;

public final class EntityRenderBootstrap {
    private EntityRenderBootstrap() {}

    public static void init(ModelManager mm) {
        RenderManager.register(Zombie.class,        new ZombieRenderer(mm.getModel("zombie")));
        RenderManager.register(Husk.class,          new HuskRenderer(mm.getModel("husk")));
        RenderManager.register(Skeleton.class,      new SkeletonRenderer((NewSkeletonModel) mm.getModel("skeleton")));
        RenderManager.register(Spider.class,        new SpiderRenderer((SpiderModel) mm.getModel("spider")));
        RenderManager.register(Sheep.class,         new SheepRenderer((SheepModel) mm.getModel("sheep"), (SheepFurModel) mm.getModel("sheep.fur")));
        RenderManager.register(Enderman.class,      new EndermanRenderer((EndermanModel) mm.getModel("enderman")));
        RenderManager.register(Creeper.class,       new CreeperRenderer((CreeperModel) mm.getModel("creeper")));
        RenderManager.register(Chicken.class,       new ChickenRenderer((ChickenModel) mm.getModel("chicken")));
        RenderManager.register(IronGolem.class,     new IronGolemRenderer((IronGolemModel) mm.getModel("irongolem")));
        RenderManager.register(Villager.class,      new VillagerRenderer((VillagerModel) mm.getModel("villager")));
        RenderManager.register(BabyZombie.class,    new BabyZombieRenderer(mm.getModel("zombie_baby")));
        RenderManager.register(BabyVillager.class,  new BabyVillagerRenderer(mm.getModel("villager_baby")));
        RenderManager.register(Pig.class,           new PigRenderer((PigModel) mm.getModel("pig")));
        RenderManager.register(Bee.class,           new BeeRenderer(mm.getModel("bee")));
        RenderManager.register(Rana.class,          new RanaRenderer("/test2.md3", "/mob/cube-nes.png"));

        // ✅ NEW MOB: ShulkerMan
        RenderManager.register(ShulkerMan.class,
            new ShulkerManRenderer((ModelShulkerMan) mm.getModel("shulkerman")));
    }
}
