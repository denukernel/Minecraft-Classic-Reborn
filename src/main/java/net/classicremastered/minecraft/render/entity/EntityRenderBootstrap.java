package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.bot.Bot;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;
import net.classicremastered.minecraft.net.NetworkPlayer;
import net.classicremastered.minecraft.player.Player;

public final class EntityRenderBootstrap {
    private EntityRenderBootstrap() {}

    public static void init(ModelManager mm) {
        HumanoidModel human = (HumanoidModel) mm.getModel("humanoid");
        var player = new PlayerRenderer(human);
        RenderManager.register(Player.class, player);
        RenderManager.register(NetworkPlayer.class, player);
        RenderManager.register(Bot.class, new ModelMobRenderer<>(human));
        RenderManager.register(Defender.class, new ModelMobRenderer<>(human));
        RenderManager.register(Steve.class, new ModelMobRenderer<>(human));
        RenderManager.register(FakeHerobrine.class, new ModelMobRenderer<>(human));
        RenderManager.register(FakePlayerClone.class, new ModelMobRenderer<>(human));

        RenderManager.register(Zombie.class, new ZombieRenderer(mm.getModel("zombie")));
        var zombie = new ModelMobRenderer<Zombie>(mm.getModel("zombie"));
        RenderManager.register(ZombieBuilder.class, zombie);
        RenderManager.register(ZombieIgniter.class, zombie);
        RenderManager.register(ScaryMindZombie.class, zombie);
        RenderManager.register(TeenZombie.class, zombie);
        RenderManager.register(IllusionumBoss.class, zombie);
        RenderManager.register(MinetrumBoss.class, zombie);
        RenderManager.register(PlayinusBoss.class, zombie);
        RenderManager.register(IllusionClone.class, new ModelMobRenderer<>(mm.getModel("zombie")));
        RenderManager.register(
                DarkCobblestoneZombie.class,
                new ModelMobRenderer<>(mm.getModel("dark_cobblestone_zombie")));
        RenderManager.register(Husk.class, new HuskRenderer(mm.getModel("husk")));
        RenderManager.register(HuskIgniter.class, new ModelMobRenderer<>(mm.getModel("husk")));
        RenderManager.register(TeenHusk.class, new ModelMobRenderer<>(mm.getModel("husk")));
        RenderManager.register(BabyHusk.class, new ModelMobRenderer<>(mm.getModel("husk_baby")));
        RenderManager.register(Frostbite.class, new ModelMobRenderer<>(mm.getModel("frostbite")));
        RenderManager.register(
                FrostbiteIgniter.class, new ModelMobRenderer<>(mm.getModel("frostbite")));
        RenderManager.register(
                TeenFrostbite.class, new ModelMobRenderer<>(mm.getModel("frostbite")));
        RenderManager.register(
                BabyFrostbite.class, new ModelMobRenderer<>(mm.getModel("frostbite_baby")));
        RenderManager.register(
                Skeleton.class, new SkeletonRenderer<>((SkeletonModel) mm.getModel("skeleton")));
        RenderManager.register(
                BabySkeleton.class,
                new SkeletonRenderer<>((SkeletonModel) mm.getModel("skeleton_baby")));
        RenderManager.register(TeenSkeleton.class, new ModelMobRenderer<>(mm.getModel("skeleton")));
        RenderManager.register(
                SkeletonTrap.class, new ModelMobRenderer<>(mm.getModel("skeleton_trap")));
        RenderManager.register(
                Spider.class, new SpiderRenderer((SpiderModel) mm.getModel("spider")));
        RenderManager.register(
                Sheep.class,
                new SheepRenderer(
                        (SheepModel) mm.getModel("sheep"),
                        (SheepFurModel) mm.getModel("sheep.fur")));
        RenderManager.register(
                Enderman.class, new EndermanRenderer((EndermanModel) mm.getModel("enderman")));
        RenderManager.register(
                Creeper.class, new CreeperRenderer((CreeperModel) mm.getModel("creeper")));
        RenderManager.register(
                Chicken.class, new ChickenRenderer((ChickenModel) mm.getModel("chicken")));
        RenderManager.register(
                IronGolem.class, new IronGolemRenderer((IronGolemModel) mm.getModel("irongolem")));
        RenderManager.register(
                Villager.class, new VillagerRenderer((VillagerModel) mm.getModel("villager")));
        RenderManager.register(
                BabyZombie.class, new BabyZombieRenderer(mm.getModel("zombie_baby")));
        RenderManager.register(
                BabyVillager.class, new BabyVillagerRenderer(mm.getModel("villager_baby")));
        RenderManager.register(Pig.class, new PigRenderer((PigModel) mm.getModel("pig")));
        RenderManager.register(Bee.class, new BeeRenderer(mm.getModel("bee")));
        RenderManager.register(Rana.class, new RanaRenderer("/test2.md3", "/mob/cube-nes.png"));
        RenderManager.register(Cow.class, new CowRenderer((CowModel) mm.getModel("cow")));
        RenderManager.register(Cod.class, new ModelMobRenderer<>(mm.getModel("cod")));
        RenderManager.register(Salmon.class, new ModelMobRenderer<>(mm.getModel("salmon")));
        RenderManager.register(
                TropicalFish.class, new ModelMobRenderer<>(mm.getModel("tropical_fish")));
        RenderManager.register(
                Pufferfish.class,
                new PufferfishRenderer(
                        mm.getModel("pufferfish"),
                        mm.getModel("pufferfish_mid"),
                        mm.getModel("pufferfish_full")));
        RenderManager.register(
                BlueForestGolem.class, new ModelMobRenderer<>(mm.getModel("blue_forest_golem")));
        RenderManager.register(Ironmock.class, new ModelMobRenderer<>(mm.getModel("ironmock")));
        RenderManager.register(
                HouseVandalizer.class, new ModelMobRenderer<>(mm.getModel("housevandalizer")));
        RenderManager.register(TNTThrower.class, new ModelMobRenderer<>(mm.getModel("tntthrower")));
        RenderManager.register(
                StickFigure.class,
                new StickFigureRenderer((StickFigureModel) mm.getModel("stickfigure")));
        RenderManager.register(SignMob.class, new SignMobRenderer());
        RenderManager.register(CrossbowTurret.class, new CrossbowTurretRenderer());

        // ✅ NEW MOB: ShulkerMan
        RenderManager.register(
                ShulkerMan.class,
                new ShulkerManRenderer((ModelShulkerMan) mm.getModel("shulkerman")));

        // ✅ NEW MOB: LeggedMonster
        RenderManager.register(
                LeggedMonster.class, new LeggedMonsterRenderer(mm.getModel("leggedmonster")));

        // ✅ NEW MOB: LongNeckMan
        RenderManager.register(
                LongNeckMan.class, new LongNeckManRenderer(mm.getModel("longneckman")));
    }
}
