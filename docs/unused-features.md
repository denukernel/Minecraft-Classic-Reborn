# minecraft classic reborn unused features

the minecraft classic reborn source contains a throwable zombie bomb, three zombie bosses, a slime model, and several screens and ai routines with no active entry point in the current checkout. the entries below describe what survives in the code. availability in older builds is unverified.

## mobs and projectiles

### zombie bomb

the zombie bomb is a throwable entity that bounces, leaves smoke as its fuse counts down, and flashes like primed tnt. the blast can break cobblestone, wood, and glass within about three blocks. the default fuse is 40 game ticks. there is no call to create one in the current game code.

source [`entity/ZombieBomb.java`](../src/main/java/net/classicremastered/minecraft/entity/ZombieBomb.java)

### minetrum

minetrum has terrain attacks that temporarily turn nearby water into lava, place a log above the player, and raise short stone pillars around them. its debris attack deals damage directly when the boss has a clear line of sight.

source [`mob/MinetrumBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/MinetrumBoss.java)

### playinus

playinus creates fake player clones and can clear items from the inventory. the attack preserves block stacks and items whose names contain `sword` or `bow`.

source [`mob/PlayinusBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/PlayinusBoss.java)

### illusionum

illusionum can vanish, teleport, spawn illusion clones, and scramble the hotbar. its clone is implemented as a separate mob class and is created by the boss.

sources [`mob/IllusionumBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/IllusionumBoss.java) and [`mob/IllusionClone.java`](../src/main/java/net/classicremastered/minecraft/mob/IllusionClone.java)

all three bosses have checks in the developer power renderer. the mob registry has no entries for them, and the current game source has no calls to their constructors.

sources [`mob/MobRegistry.java`](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java) and [`render/DeveloperPowerRenderer.java`](../src/main/java/net/classicremastered/minecraft/render/DeveloperPowerRenderer.java)

## models and textures

### slime

the slime has a cube model with an inner gel layer, eyes, a mouth, and an idle bounce. a slime texture also ships in the resources. the current game code has no slime mob class and does not instantiate the model.

sources [`model/ModelSlime.java`](../src/main/java/net/classicremastered/minecraft/model/ModelSlime.java) and [`src/main/resources/mob/slime.png`](../src/main/resources/mob/slime.png)

### alternate skeleton model

an alternate skeleton model, `NewSkeletonModel`, is present in the source. the model manager creates `SkeletonModel` for skeleton rendering.

sources [`model/NewSkeletonModel.java`](../src/main/java/net/classicremastered/minecraft/model/NewSkeletonModel.java) and [`model/ModelManager.java`](../src/main/java/net/classicremastered/minecraft/model/ModelManager.java)

## interface screens

### recipe guide

the recipe guide displays one crafting recipe per page. it shows the ingredient layout, output, and item description, with buttons for moving between recipes. no menu or input handler opens this screen.

source [`gui/RecipeGuideScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/RecipeGuideScreen.java)

### save level screen

the standalone save level screen writes local `.mine` files. no code opens this screen. the world selection screen has its own save path.

sources [`gui/SaveLevelScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/SaveLevelScreen.java) and [`gui/SelectLevelScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/SelectLevelScreen.java)

## ai routines

- **projectile dodge goal.** an unregistered combat goal tells a mob to strafe and occasionally hop when an arrow dodge is active. source [`mob/ai/goal/combat/DodgeProjectileGoal.java`](../src/main/java/net/classicremastered/minecraft/mob/ai/goal/combat/DodgeProjectileGoal.java).

- **pit finder.** an uncalled helper searches for ledges with a solid landing below and returns a point just past the edge. source [`mob/HazardFinder.java`](../src/main/java/net/classicremastered/minecraft/mob/HazardFinder.java).

- **player steering.** an uncalled helper turns and nudges the player toward a target point. source [`mob/MindControl.java`](../src/main/java/net/classicremastered/minecraft/mob/MindControl.java).
