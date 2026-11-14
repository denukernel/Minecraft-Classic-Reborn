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

### stair and ladder block states

stairs use block ids `160` through `191` for facing and upside-down placement. only the base oak (`168`), cobblestone (`176`), and stone brick (`184`) ids appear as inventory variants in that range. sandstone stairs use `85` as their inventory id, so all eight sandstone orientation ids are hidden. ladders likewise use hidden ids `97` through `99` for three wall faces and the ordinary ladder id `91` for the fourth. normal placement creates these states and breaking them drops the corresponding inventory variant. numeric `/give` accepts the hidden ids.

sources [`StairBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/StairBlock.java), [`LadderBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/LadderBlock.java), [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [`GameMode.java`](../src/main/java/net/classicremastered/minecraft/gamemode/GameMode.java), [`BlockSelectScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/BlockSelectScreen.java), [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java)

### creative blocks without survival sources

bedrock (`7`) generates in worlds and is selectable in creative. the survival break path prevents players from mining it. the beacon (`222`), jukebox (`212`), lucky block (`229`), and structure alpha through delta (`60` through `63`) also appear in creative. the current generator, recipe, loot, and shop paths provide no first copy of these blocks in ordinary survival. the beacon emits light, the lucky block triggers random outcomes, and the structure blocks have right-click effects. a creative inventory or `/give` can supply them.

sources [`CreativeInventoryBlocks.java`](../src/main/java/net/classicremastered/minecraft/CreativeInventoryBlocks.java), [`InputManager.java`](../src/main/java/net/classicremastered/minecraft/InputManager.java), [`BiomeLevelGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/BiomeLevelGenerator.java), [`BeaconBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/BeaconBlock.java), [`LuckyBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/LuckyBlock.java), [`StructureBlock.java`](../src/main/java/net/classicremastered/minecraft/level/tile/StructureBlock.java), [`CraftingManager.java`](../src/main/java/net/classicremastered/minecraft/crafting/CraftingManager.java), [`ShopScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/ShopScreen.java)

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

### packed turret

the creative catalog hides the packed crossbow turret (`308`). packing a turret into an empty crate creates a usable stack with the turret data attached, including its variant. `/give 308` can create the stack id without attaching that data, so the resulting stack cannot deploy a turret. packing works in survival.

sources [`TurretItems.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/TurretItems.java), [`Inventory.java`](../src/main/java/net/classicremastered/minecraft/player/Inventory.java), [`BlockSelectScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/BlockSelectScreen.java), [`GiveCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/GiveCommand.java)

### legacy shotgun shell

the legacy shotgun shell (`320`) still works as ammo for the arrow shotgun turret, whose default ammo is the ordinary arrow. the shell is registered and available through creative or `/give`. the current recipes, shops, and loot tables provide no survival source for it.

sources [`TurretItems.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/TurretItems.java), [`TurretVariant.java`](../src/main/java/net/classicremastered/minecraft/mob/TurretVariant.java), [`CraftingManager.java`](../src/main/java/net/classicremastered/minecraft/crafting/CraftingManager.java), [`ShopScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/ShopScreen.java), [`VillagerTrades.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/VillagerTrades.java)

### mob wands and utility items

the four mob wands (`336` through `339`), health drink (`272`), damage bottle (`273`), and glass looker (`275`) are functional entries available through creative or `/give`. the current recipes, trades, and loot provide no ordinary survival source for these items. the glass looker zooms while the use button is held through the frame renderer.

sources [`ItemRegistry.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [`MobWandItem.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/MobWandItem.java), [`ClientFrameRenderer.java`](../src/main/java/net/classicremastered/minecraft/ClientFrameRenderer.java), [`CraftingManager.java`](../src/main/java/net/classicremastered/minecraft/crafting/CraftingManager.java), [`ShopScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/ShopScreen.java), [`VillagerTrades.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/VillagerTrades.java), [`DesertPyramid.java`](../src/main/java/net/classicremastered/minecraft/level/structure/DesertPyramid.java)

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

### legacy terrain texture directory

the `terrain/blocks/` directory contains 72 png files with no built-in atlas or direct texture lookup in the current source. examples include `barrier.png`, `unusedtexture.png`, and the old furnace textures under `new_blocks/`. the active atlas reads its tile names from `textures/block/tiles.properties` and loads images from `textures/block/`.

sources [`barrier.png`](../src/main/resources/terrain/blocks/barrier.png), [`unusedtexture.png`](../src/main/resources/terrain/blocks/unusedtexture.png), [`furnace_front_on.png`](../src/main/resources/terrain/blocks/new_blocks/furnace_front_on.png), [`tiles.properties`](../src/main/resources/textures/block/tiles.properties), and [`BlockTextureAtlas.java`](../src/main/java/net/classicremastered/minecraft/render/BlockTextureAtlas.java)

### unmapped classic tiles

fourteen `classic_*.png` files in the active texture directory are omitted from the atlas manifest. their filename numbers are `023`, `024`, `025`, `026`, `031`, `053`, `054`, `055`, `158`, `191`, `200`, `201`, `213`, and `214`. for example, the atlas entry for tile 200 uses `torch.png`.

sources [`classic_023.png`](../src/main/resources/textures/block/classic_023.png), [`classic_200.png`](../src/main/resources/textures/block/classic_200.png), and [`tiles.properties`](../src/main/resources/textures/block/tiles.properties)

### leftover item and entity images

the bundled `mining_axe.png`, `egg_base.png`, `egg_spots.png`, and `turret.png` have no built-in lookups. the iron pickaxe uses `gear/iron_pickaxe.png`, spawn eggs resolve to per-mob images, and the crossbow turret uses `cobblestone_oak.png`.

sources [`mining_axe.png`](../src/main/resources/items/mining_axe.png), [`egg_base.png`](../src/main/resources/items/gear/egg_base.png), [`egg_spots.png`](../src/main/resources/items/gear/egg_spots.png), [`turret.png`](../src/main/resources/turret/turret.png), [`MiningAxeItem.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/MiningAxeItem.java), [`MobEggColors.java`](../src/main/java/net/classicremastered/minecraft/level/itemstack/MobEggColors.java), and [`TurretVariant.java`](../src/main/java/net/classicremastered/minecraft/mob/TurretVariant.java)

## sounds

all 41 bundled sound clips have a registration path and a gameplay sound key. registration removes trailing digits from filenames to form pools. for example, `teleport1.ogg` and `teleport2.ogg` both belong to the enderman teleport pool, and the seven numbered sweep clips share the sword sweep pool. the audio engine picks a clip from the requested pool.

sources [`ResourceDownloadThread.java`](../src/main/java/net/classicremastered/minecraft/ResourceDownloadThread.java), [`PaulsCodeSoundManager.java`](../src/main/java/net/classicremastered/minecraft/sound/PaulsCodeSoundManager.java), [`EndermanTeleportation.java`](../src/main/java/net/classicremastered/minecraft/mob/ai/goal/enderman/EndermanTeleportation.java), and [`PlayerCombat.java`](../src/main/java/net/classicremastered/minecraft/PlayerCombat.java)

### unused audio classes

`SoundInfo` contains sample interpolation code with an empty initialization method. `SimpleSoundData` stores interleaved pcm samples, sample rate, and channel count. no built-in playback or registration path uses either class.

sources [`SoundInfo.java`](../src/main/java/net/classicremastered/minecraft/sound/SoundInfo.java) and [`SimpleSoundData.java`](../src/main/java/net/classicremastered/minecraft/sound/SimpleSoundData.java)

### legacy ogg vorbis decoder

the `de/jarnbjo` source tree contains 35 java files for ogg streams, vorbis decoding, and related audio utilities. `SoundReader` is the only game class that imports it, and the game has no caller for that reader. the source also includes a java sound `VorbisAudioFileReader`, with no service registration in the project resources. the active sound manager registers paulscode `CodecJOrbis` for ogg and `CodecWav` for wav.

sources [`VorbisStream.java`](../src/main/java/de/jarnbjo/vorbis/VorbisStream.java), [`VorbisAudioFileReader.java`](../src/main/java/de/jarnbjo/vorbis/VorbisAudioFileReader.java), [`SoundReader.java`](../src/main/java/net/classicremastered/minecraft/sound/SoundReader.java), and [`PaulsCodeSoundManager.java`](../src/main/java/net/classicremastered/minecraft/sound/PaulsCodeSoundManager.java)

## animations and visual effects

### tnt thrower wind-up and release

`TNTThrowerModel` has a two-part right arm animation for winding up and releasing a throw. it only runs when the model receives a positive `attackOffset`. the throw ai sets the mob attack timer, but the tnt thrower uses the fallback mob renderer, which never copies that timer into the model offset. the mob can still throw tnt while its arms use the default ready pose.

sources [`TNTThrowerModel.java`](../src/main/java/net/classicremastered/minecraft/model/TNTThrowerModel.java), [`ThrowTntGoal.java`](../src/main/java/net/classicremastered/minecraft/mob/ai/goal/tnt/ThrowTntGoal.java), [`Mob.java`](../src/main/java/net/classicremastered/minecraft/mob/Mob.java), [`EntityRenderBootstrap.java`](../src/main/java/net/classicremastered/minecraft/render/entity/EntityRenderBootstrap.java), and [`MobRenderer.java`](../src/main/java/net/classicremastered/minecraft/render/entity/MobRenderer.java)

### alternate model motion

the unused slime model animates a gentle idle bounce. the unused alternate skeleton model has a walk cycle that swings the legs and moves the arms around a raised base pose. both motions are defined in the models and have no active mob rendering path.

sources [`ModelSlime.java`](../src/main/java/net/classicremastered/minecraft/model/ModelSlime.java), [`NewSkeletonModel.java`](../src/main/java/net/classicremastered/minecraft/model/NewSkeletonModel.java), and [`ModelManager.java`](../src/main/java/net/classicremastered/minecraft/model/ModelManager.java)

### md3 animation playback

the in-game md3 loader and renderer use vertex positions from frame zero only. the bundled rana mesh, `test2.md3`, contains one frame. the separate md3 studio has a multiframe reader and animation playback for imported files.

sources [`MD3Surface.java`](../src/main/java/net/classicremastered/minecraft/model/MD3Surface.java), [`MD3Renderer.java`](../src/main/java/net/classicremastered/minecraft/model/MD3Renderer.java), [`test2.md3`](../src/main/resources/models/test2.md3), and [`MD3Studio.java`](../src/main/java/net/classicremastered/toolkit/md3studio/MD3Studio.java)

### illusionum hud glitch timer

illusionum increases a `glitchTimer` while vanishing and resets it after its attack flurry. the timer has no reader outside its own update and no hud render path. the boss separately swaps hotbar slots and jitters the camera by changing the player view angles.

source [`IllusionumBoss.java`](../src/main/java/net/classicremastered/minecraft/mob/IllusionumBoss.java)

## particles and projectile effects

### zombie bomb flash and debris

the unused zombie bomb has a white flashing overlay that intensifies near detonation. it spawns smoke while the fuse burns and terrain debris for the blocks it destroys. these specific effects have no active spawn path because the game never creates a zombie bomb.

source [`ZombieBomb.java`](../src/main/java/net/classicremastered/minecraft/entity/ZombieBomb.java)

### particle class usage

all five concrete particle subclasses have gameplay emitters. smoke is used by primed tnt, terrain particles by block breaking, water drops by rain, explosion sprites by blasts, and combat particles by melee feedback. the built-in game creates these subclasses without directly instantiating the generic `Particle` base.

sources [`PrimedTnt.java`](../src/main/java/net/classicremastered/minecraft/entity/PrimedTnt.java), [`Block.java`](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [`Minecraft.java`](../src/main/java/net/classicremastered/minecraft/Minecraft.java), [`Level.java`](../src/main/java/net/classicremastered/minecraft/level/Level.java), [`PlayerCombat.java`](../src/main/java/net/classicremastered/minecraft/PlayerCombat.java), and [`Particle.java`](../src/main/java/net/classicremastered/minecraft/particle/Particle.java)

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

## world generation and dimensions

### flat debug village

`FlatDebugVillageGenerator` builds a finite flat map 128 blocks wide, 128 blocks long, and 64 blocks high. it sets the spawn near the center and attempts to generate a single house. no built-in code calls `makeDebugVillageLevel`. the regular flat world option has its own generation path.

sources [`FlatDebugVillageGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/FlatDebugVillageGenerator.java), [`GenerateLevelScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/GenerateLevelScreen.java)

### dimension generation

the readme lists dimensions as still in development. the current world creation screen offers finite and infinite worlds with classic or biome terrain and optional flat generation. there is no separate dimension generator in the current game source. the portal block uses the spawn coordinates of the current level.

sources [readme](../README.md), [`GenerateLevelScreen.java`](../src/main/java/net/classicremastered/minecraft/gui/GenerateLevelScreen.java), and [`Player.java`](../src/main/java/net/classicremastered/minecraft/player/Player.java)

## structure generation

### flat village test layout

the [flat debug village generator](#flat-debug-village) attempts to place a single `HouseStructure` near the center of a test map. that specific layout has no built-in entry point. the same house class is used by the structure registry when generating classic finite terrain.

sources [`FlatDebugVillageGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/FlatDebugVillageGenerator.java), [`HouseStructure.java`](../src/main/java/net/classicremastered/minecraft/level/structure/HouseStructure.java), [`StructureRegistry.java`](../src/main/java/net/classicremastered/minecraft/level/structure/StructureRegistry.java), and [`LevelGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/LevelGenerator.java)

### dormant structure registry paths

the desert pyramid is in the weighted structure registry, but the classic finite generator leaves the biome seed unset and the pyramid rejects those sites. the biome generator places pyramids through its own finite and chunk generation code. cobblestone towers also have a registry entry and are explicitly skipped by the weighted picker. they have their own seeded placement routines.

sources [`StructureRegistry.java`](../src/main/java/net/classicremastered/minecraft/level/structure/StructureRegistry.java), [`DesertPyramid.java`](../src/main/java/net/classicremastered/minecraft/level/structure/DesertPyramid.java), [`CobblestoneTower.java`](../src/main/java/net/classicremastered/minecraft/level/structure/CobblestoneTower.java), [`LevelGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/LevelGenerator.java), and [`BiomeLevelGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/BiomeLevelGenerator.java)

mineshafts generate through direct terrain hooks outside the structure registry. the registry-backed locate command has no mineshaft id, even though mineshafts can generate in finite and infinite terrain.

sources [`Mineshaft.java`](../src/main/java/net/classicremastered/minecraft/level/structure/Mineshaft.java), [`LevelGenerator.java`](../src/main/java/net/classicremastered/minecraft/level/generator/LevelGenerator.java), [`SimpleChunkManager.java`](../src/main/java/net/classicremastered/minecraft/level/infinite/SimpleChunkManager.java), and [`LocateStructureCommand.java`](../src/main/java/net/classicremastered/minecraft/chat/commands/LocateStructureCommand.java)

## unused engine classes

- **`PlacementService`.** provides collision checks for block placement against the player and mobs. the active input path calls the checks on `Minecraft`. sources [`PlacementService.java`](../src/main/java/net/classicremastered/minecraft/client/placement/PlacementService.java), [`InputManager.java`](../src/main/java/net/classicremastered/minecraft/InputManager.java), [`Minecraft.java`](../src/main/java/net/classicremastered/minecraft/Minecraft.java).
- **`ScreenshotService`.** wraps a queued screenshot request and capture. the client calls `Screenshot.take` directly. sources [`ScreenshotService.java`](../src/main/java/net/classicremastered/minecraft/client/screenshot/ScreenshotService.java), [`ClientFrameRenderer.java`](../src/main/java/net/classicremastered/minecraft/ClientFrameRenderer.java).
- **`ModelPose`.** uses reflection to read entity fields, calculate a model pose, and render it. no built-in renderer calls `poseAndRender`. source [`ModelPose.java`](../src/main/java/net/classicremastered/minecraft/model/ModelPose.java).
- **`LevelObjectInputStream`.** defines class descriptor handling for a short list of serialized entity classes. the legacy level loader uses the standard `ObjectInputStream`. sources [`LevelObjectInputStream.java`](../src/main/java/net/classicremastered/minecraft/level/LevelObjectInputStream.java), [`LevelIO.java`](../src/main/java/net/classicremastered/minecraft/level/LevelIO.java).
- **`SoundReader`.** decodes vorbis data into `SoundData`. the built-in audio setup has no call to this reader. sources [`SoundReader.java`](../src/main/java/net/classicremastered/minecraft/sound/SoundReader.java), [`ClientBootstrap.java`](../src/main/java/net/classicremastered/minecraft/ClientBootstrap.java).
