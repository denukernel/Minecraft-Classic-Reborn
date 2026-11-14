# minecraft classic reborn unused features

the minecraft classic reborn source contains unused mobs, item definitions, models, screens, and ai routines. some active world blocks also have inventory forms that are hidden from creative or blocked by `/give`. availability in older builds is unverified.

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

### boss clones

playinus is the sole creator of `FakePlayerClone`, a humanoid that deals light melee damage and despawns after 200 ticks. illusionum is the sole creator of `IllusionClone`, a drifting zombie decoy that expires after 120 ticks. neither clone has its own mob registry entry. their built-in spawn paths depend on the unused bosses.

sources [`FakePlayerClone.java`](../src/main/java/net/classicremastered/minecraft/mob/FakePlayerClone.java), [`PlayinusBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/PlayinusBoss.java), [`IllusionClone.java`](../src/main/java/net/classicremastered/minecraft/mob/IllusionClone.java), and [`IllusionumBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/IllusionumBoss.java)

## hidden blocks and inventory forms

### fire stack

fire exists in the world as block id `50`. it drops no items, the creative catalog and pick block omit it, and `/give 50` is explicitly rejected. flint and steel creates fire directly in the world. an nbt editor can put a fire stack in a local save because the inventory loader accepts a positive count with raw id `50`.

sources [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [`FireBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/FireBlock.java), [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java), [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), [`InputManager.java`](../src/main/java/net/classicremastered/minecraft/InputManager.java), [`PlayerNbtCodec.java`](../src/main/java/net/classicremastered/minecraft/level/PlayerNbtCodec.java), and [`Inventory.java`](../src/main/java/net/classicremastered/minecraft/player/Inventory.java)

### liquid block stacks

the same `/give` guard rejects flowing water (`8`) and flowing lava (`10`). the still water (`9`) and still lava (`11`) stacks are also absent from the creative catalog, but the command accepts their numeric ids. buckets place liquids directly in the world.

sources [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java), [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), and [`BucketItem.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/BucketItem.java)

### redstone and slab states

the creative catalog skips the lit redstone lamp (`73`), piston head (`75`), and double slab (`43`). the game creates these blocks when a lamp is powered, a piston extends, or two slabs merge. all three ids pass the numeric `/give` check.

sources [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [`RedstoneEngine.java`](../src/main/java/net/classicremastered/minecraft/level/RedstoneEngine.java), [`SlabBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/SlabBlock.java), and [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java)

### portal block

the portal (`227`) is absent from the creative catalog and can be obtained through `/give 227`. it has a purple tint and no collision box. the player code checks for the portal and teleports the player to the current world spawn when they enter it.

sources [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), [`BlockPortal.java`](../src/main/java/net/classicremastered/minecraft/level/tile/BlockPortal.java), [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), and [`Player.java`](../src/main/java/net/classicremastered/minecraft/player/Player.java)

### jukebox interaction

the jukebox (`212`) is available in creative. it is registered as a plain block, with no jukebox interaction or record playback wired to it.

sources [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java) and [`BlockSelectScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/BlockSelectScreen.java)

## unused item definitions and icons

### original sword

the item registry constructs a generic `SwordItem` at item id `2`. equipment registration replaces the entry at the same id with the iron sword. `/give 258` therefore gives the iron sword. the original sword texture remains in the resources.

sources [`SwordItem.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/SwordItem.java), [`ItemRegistry.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [`Equipment.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/Equipment.java), and [`sword.png`](../src/main/resources/items/sword.png)

### block icon entries

the lever, torch, redstone torch, repeater, comparator, and ladder have separate item entries for displaying their icons. the creative catalog hides those entries, while `/give` accepts their raw item ids (`302` for the lever and `330` through `334` for the others). the icon entries do not place blocks when used as inventory stacks.

sources [`Item.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/Item.java), [`ItemRegistry.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [`BlockSelectScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/BlockSelectScreen.java), [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java), and [`InputManager.java`](../src/main/java/net/classicremastered/minecraft/InputManager.java)

### leftover item textures

`physics_test.png` and `egg_null.png` remain in the item resources with no references in the built-in item code. the physics gun uses `physics_gun.png`, and the spawn egg texture lookup uses `unknown.png` as its fallback.

sources [`physics_test.png`](../src/main/resources/items/physics_test.png), [`egg_null.png`](../src/main/resources/items/egg_null.png), [`PhysicsGunItem.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/PhysicsGunItem.java), and [`MobEggColors.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/MobEggColors.java)

## models and textures

### slime

the slime has a cube model with an inner gel layer, eyes, a mouth, and an idle bounce. a slime texture also ships in the resources. the current game code has no slime mob class and does not instantiate the model.

sources [`model/ModelSlime.java`](../src/main/java/net/classicremastered/minecraft/model/ModelSlime.java) and [`src/main/resources/mob/slime.png`](../src/main/resources/mob/slime.png)

### alternate skeleton model

an alternate skeleton model, `NewSkeletonModel`, is present in the source. the model manager creates `SkeletonModel` for skeleton rendering.

sources [`model/NewSkeletonModel.java`](../src/main/java/net/classicremastered/minecraft/model/NewSkeletonModel.java) and [`model/ModelManager.java`](../src/main/java/net/classicremastered/minecraft/model/ModelManager.java)

### agent

the unused agent model duplicates the basic body design of ironmock, a registered mob. both models use the same proportions for the head, torso, arms, and legs. ironmock uses its own texture, renders at 1.5 times scale, and adds a two-arm attack animation. the model manager exposes the unused variant under the `agent` key, and no built-in mob uses that key.

sources [`model/AgentModel.java`](../src/main/java/net/classicremastered/minecraft/model/AgentModel.java), [`model/IronmockModel.java`](../src/main/java/net/classicremastered/minecraft/model/IronmockModel.java), [`mob/Ironmock.java`](../src/main/java/net/classicremastered/minecraft/mob/Ironmock.java), [`model/ModelManager.java`](../src/main/java/net/classicremastered/minecraft/model/ModelManager.java), and [`mob/agent.png`](../src/main/resources/mob/agent.png)

### cube item model

`ItemModel` builds a small cube by mapping the same texture atlas tile onto all six faces. no current game code creates an instance of this model.

source [`entity/ItemModel.java`](../src/main/java/net/classicremastered/minecraft/entity/ItemModel.java)

### obj and collada mesh rendering

the source includes readers for wavefront obj and collada dae files. both produce mesh data that `MeshModel` can draw as textured triangles. the built-in model and entity setup does not use either reader or instantiate that renderer.

sources [`model/ObjMesh.java`](../src/main/java/net/classicremastered/minecraft/model/ObjMesh.java), [`model/DaeMesh.java`](../src/main/java/net/classicremastered/minecraft/model/DaeMesh.java), and [`model/MeshModel.java`](../src/main/java/net/classicremastered/minecraft/model/MeshModel.java)

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

## mod events

the built-in client tick hook calls the mod loader, while its line for posting `TickEvent.Client` to the event bus is commented out. the hook class also defines methods that post chat send and world render events, with no calls from the built-in game code.

sources [`Hooks.java`](../src/main/java/com/mcraft/api/hooks/Hooks.java), [`TickEvent.java`](../src/main/java/com/mcraft/api/event/events/TickEvent.java), and [`Minecraft.java`](../src/main/java/net/classicremastered/minecraft/Minecraft.java)

## online level saving

`LevelIO` still has a `saveOnline` method with parameters for a host, account, level name, and slot id. the method has no callers and only prints a diagnostic message. local world saving is handled through the world selection screen.

sources [`level/LevelIO.java`](../src/main/java/net/classicremastered/minecraft/level/LevelIO.java) and [`gui/SelectLevelScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/SelectLevelScreen.java)
