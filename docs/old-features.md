# minecraft classic features from 2018 to 2022

the project traces back to private minecraft classic source modding in 2018. the public 2019 minecraft classic 0.34 project experimented with survival, creative, crafting, and new mobs. the 2022 minecraft classic remastered source retained several of those ideas. this page compares that history with the 2026 minecraft classic reborn checkout.

## private modding in 2018

according to the project history supplied for this wiki, the developer began testing private modifications to the minecraft classic source in 2018. the old builds were never archived and are now lost. the surviving public record begins with the 2019 repository.

## what survives from each year

| period | surviving evidence |
| --- | --- |
| 2019 | the 0.34 repository preserves builds from `031_09.jar` through `0.34pre2.jar`. the december 2019 readme marks the project discontinued. [archived builds](https://github.com/Processmodifyer/0.34/tree/a73202892), [discontinuation commit](https://github.com/Processmodifyer/0.34/commit/a73202892). |
| 2020–2021 | the old readme marks its planned beta and full release as canceled. neither repository history provides a dated public build for these two years. [discontinuation readme](https://github.com/Processmodifyer/0.34/blob/a73202892/README.md). |
| 2022 | the remastered repository preserves an august source snapshot. the readme describes an extension of classic 0.30 with a title screen, new items, blocks, and mobs. [2022 readme](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/README.md), [2022 source history](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/commits/main/). |
| 2026 | classic reborn provides finite and infinite terrain, creative and survival inventories, and a day and night cycle. the comparison below uses the current mod source. [classic reborn readme](../README.md). |

the 2019 findings use the archived wiki, commit dates, class inventories from all ten jars in the archived tree, and targeted bytecode inspection of the latest `0.34pre2.jar`. the 2022 findings refer to source. an archived class, texture, or button confirms what was present in the files. its call sites determine whether it had an entry point.

### minecraft classic coder pack

the archive contains four source snapshots in `for0.32`, `for0.32_5`, `for0.33`, and `for0.33_04`. these include the old block table, mob code, and cheat screens. the boss and demonic spawn actions below were checked against the later `0.34pre2.jar`. [minecraft classic coder pack.7z](https://github.com/Processmodifyer/0.34/blob/a73202892/minecraft%20classic%20coder%20pack.7z), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

## survival and creative

the 2019 wiki credits `0.31_09` with items, a cheat menu, and respawning. the jar has a respawn button and a pause-menu path to the cheat menu. the wiki describes creative mode and mob spawning in `0.32`, as well as a generator that omitted trees and flowers. those descriptions apply to the builds named in the archived wiki. [`031_09.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/031_09.jar), [2019 wiki](https://github.com/Processmodifyer/0.34/blob/93fadab6c/wiki/wiki.txt).

**2026 comparison.** respawning remains available. reborn uses a creative catalog and commands such as `/gamemode`, `/give`, and `/summon`. its terrain generators place vegetation, and survival has a bow recipe and craftable arrows. [respawn screen](../src/main/java/net/classicremastered/minecraft/gui/GameOverScreen.java), [creative catalog](../src/main/java/net/classicremastered/minecraft/gui/BlockSelectScreen.java), [game mode command](../src/main/java/net/classicremastered/minecraft/chat/commands/GameModeCommand.java), [biome generator](../src/main/java/net/classicremastered/minecraft/level/generator/BiomeLevelGenerator.java), [recipes](../src/main/java/net/classicremastered/minecraft/crafting/CraftingManager.java).

## lighter, cobwebs, and crafting

### lighter and fire

the 2019 `032_5update2.jar` contains `LighterItem` and `FireBlock`. its wiki identifies the lighter as a way to place fire. the 2022 lighter writes the fire block directly into the world. 2022 remastered registered fire as block `67`. [`032_5update2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/032_5update2.jar), [2019 wiki](https://github.com/Processmodifyer/0.34/blob/a73202892/wiki/wiki.txt), [2022 lighter](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/item/LighterItem.java), [2022 block registry](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/tile/Block.java).

**2026 comparison.** flint and steel still creates fire. reborn uses block `50` and checks for valid support before lighting it. the fire block handles smoke, spread, and burning. [fire inventory restrictions](unused-features.md#fire-stack), [ignition input](../src/main/java/net/classicremastered/minecraft/InputManager.java), [fire behavior](../src/main/java/net/classicremastered/minecraft/level/tile/FireBlock.java).

### spider webs

the 0.33 jar contains a cobweb block, and spider bytecode places `Block.WEB`. the 2022 source registers the cobweb at block `55`. [`0.33.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33.jar), [2022 block registry](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/tile/Block.java).

**2026 comparison.** the current atlas still maps a web texture at tile `52`, but the game has no cobweb block registration or spider web placement. the atlas tile number identifies an image position. [current atlas manifest](../src/main/resources/textures/block/tiles.properties), [current blocks](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java), [current spider](../src/main/java/net/classicremastered/minecraft/mob/Spider.java).

### workbench and crafting guide

the 0.33 jar has a crafting screen, and 0.34 pre-release 2 also contains a crafting guide. the 2022 guide includes instructions for planks, a workbench, a torch made from stone and coal ore, and a booktable. those lines document the guide text. [`0.33.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 crafting guide](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/CraftingGuideScreen.java).

**2026 comparison.** reborn keeps workbench crafting with a different recipe list. its torch recipe uses coal and a stick. the booktable has no registered block or recipe, and the separate recipe guide screen has no current menu entry. [2026 recipes](../src/main/java/net/classicremastered/minecraft/crafting/CraftingManager.java), [unused recipe guide](unused-features.md#recipe-guide).

### blocks and items in 0.34 pre-release 2

the jar registers apple, feather, arrow, and bow through the block table at ids `50` through `53`. the workbench, cobweb, and torch occupy `54`, `55`, and `56`. the steve head is registered through `HeadBlock`. the bootstrap passes `59` to its constructor, but the constructor uses `57` when it calls the base block constructor. the `Human` class references that head. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

the same jar contains `PoisonedAppleItem`, `ExplodingArrow`, and `SiteOpenerBlock` with no references from other game classes. the poisoned apple extends the ordinary apple. the exploding arrow contains a call to the world explosion routine. the site opener has a collision method that moves the mouse cursor and logs a message. these are packaged experiments with no verified acquisition or spawn path. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

**2022 and 2026 comparison.** the 2022 block registry retains workbench `54`, cobweb `55`, and torch `56`. it registers a redstone block at `59` and redstone ore at `60`, and the source includes a sign item. reborn registers apples, feathers, arrows, and bows as items. its workbench and torch use block ids `228` and `76`, and its redstone block and ore use `71` and `89`. [2022 block registry](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/tile/Block.java), [2022 sign item](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/item/SignItem.java), [2026 item registry](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [2026 equipment](../src/main/java/net/classicremastered/minecraft/level/itemstack/Equipment.java), [2026 block registry](../src/main/java/net/classicremastered/minecraft/level/tile/Block.java).

### flower block item rendering

in both `031_09.jar` and `0.34pre2.jar`, the apple, feather, arrow, and bow classes extend `DragableItem`, which extends `FlowerBlock`. they inherit the flower preview renderer, which draws crossed textured quads from the block atlas. this let items registered in the block table appear as flat sprites inside the existing block preview system. [early 2019 jar](https://github.com/Processmodifyer/0.34/blob/a73202892/031_09.jar), [late 2019 jar](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

in the 2022 source, `Item` extends `Block` directly and supplies its own flower-style renderer. it draws a single atlas-textured plane, with geometry on both sides. the survival inventory still looks up stacks through `Block.blocks` and calls `renderFullbright`. [2022 item renderer](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/item/Item.java), [2022 flower renderer](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/world/tile/FlowerBlock.java), [2022 survival inventory](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/SurvivalInventoryScreen.java).

**2026 comparison.** reborn stores items separately in `Item.items` and encodes their inventory ids as the item id plus `256`. `Item.renderIcon` draws a flat icon in the inventory. held and dropped sprite items use `ItemMeshRenderer`, which draws textured front and back faces and adds thickness along the exposed edges of opaque pixels. block stacks have their own preview path. [2026 item class](../src/main/java/net/classicremastered/minecraft/level/itemstack/Item.java), [inventory drawing](../src/main/java/net/classicremastered/minecraft/gui/InventoryScreen.java), [held item renderer](../src/main/java/net/classicremastered/minecraft/render/HeldItemRenderer.java), [dropped item renderer](../src/main/java/net/classicremastered/minecraft/entity/DroppedItem.java), [item mesh renderer](../src/main/java/net/classicremastered/minecraft/render/ItemMeshRenderer.java).

## mobs and seasonal assets

### crocodile

the 0.33 jar contains `Crocodille`, `CrocodileModel`, and `mob/croc.png`. the archived wiki describes an earlier test version as available through mods. the 0.33 jar itself has a cheat-menu action that adds a crocodile to the world, and its mob spawner also references the class. the 0.34 pre-release jars keep the class without references from other game classes. the 2022 source still has the class and model. the 2026 mob registry has no crocodile entry. [`0.33.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33.jar), [`0.34pre.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2019 wiki](https://github.com/Processmodifyer/0.34/blob/a73202892/wiki/wiki.txt), [2022 crocodile class](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/entity/creature/Crocodille.java), [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java).

### halloween build

the 2019 halloween jar bundles alternate mob textures, including halloween creeper, crocodile, spider, and zombie-themed images. the spider constructor selects `mob/hallowen/naturalinsert.png`. the 2019 history records a separate halloween removal commit. the 2026 checkout has no equivalent halloween texture set. [`0.33_02hallowen.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33_02hallowen.jar), [halloween removal commit](https://github.com/Processmodifyer/0.34/commit/f6e1719e0), [2026 spider](../src/main/java/net/classicremastered/minecraft/mob/Spider.java).

### spawn menus and older mobs

the 0.34 pre-release 2 jar includes mob, boss, and demonic mob menu classes. the 2022 source verifies action handlers that construct zombie pigmen and humans from the spawn menu and speer, chickens, cows, sheep, and endermen from its submenus. these are source-level menu actions. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 spawn menu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/SpawnMobs.java), [first submenu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMobs.java), [second submenu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMoreMobs.java).

**2026 comparison.** chickens, cows, sheep, and endermen have registry entries. zombie pigman, human, and speer have no corresponding registry entries. reborn uses spawn eggs and commands for manual spawning. [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java), [spawn egg registration](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [summon command](../src/main/java/net/classicremastered/minecraft/chat/commands/SpawnMobCommand.java).

### boss and demonic mob menus

in `0.34pre2.jar`, the pause menu opens cheats, then spawn mob, then two more mobs pages. the second page opens the bosses and demonic mobs screens. each entry below has bytecode that constructs its mob and adds it to the world. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

| menu entry | class | behavior or asset confirmed in the jar |
| --- | --- | --- |
| giant creeper | `GiantCreeper` | uses `giantcreeper.png`. its ai includes creating an ordinary creeper and triggering an explosion. |
| lisonio | `Hingno` | the enderman boss. it uses `enderking.png` and its ai creates angry endermen. |
| skeleton king | `KingSkeleton` | uses `skeletonking.png`. it fires `NewArrow` projectiles, and its ai creates skeletons. |
| demonic chicken | `DemonChicken` | uses a dedicated demonic chicken texture and an ai that targets the player. |
| demonic creeper | `DemonicCreeper` | uses a dedicated demonic creeper texture and an ai with an explosion routine. |

the 2022 source keeps the ordinary spawn submenus and includes a `GiantCreeperModel`. its second submenu only offers sheep and endermen, with no boss or demonic submenu. the five 2019 variants have no entries in the 2026 mob registry. [2022 second spawn submenu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMoreMobs.java), [2022 giant creeper model](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/model/GiantCreeperModel.java), [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java).

### other 2019 mobs

- **giant and hungelanger.** both classes appear in `031_09.jar`. the coder pack shows that they extend zombie. the giant uses the zombie texture, and hungelanger uses the player texture. the latest 2019 spawn menu has a hungelanger action. [`031_09.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/031_09.jar), [minecraft classic coder pack.7z](https://github.com/Processmodifyer/0.34/blob/a73202892/minecraft%20classic%20coder%20pack.7z), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).
- **headset zombie and pig zombie.** both classes are present by `0.33_02.jar`. the latest 2019 menu calls the headset zombie entry **in control1** and offers pig zombie separately. the 2022 menu still spawns a pig zombie. [`0.33_02.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33_02.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 spawn menu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/SpawnMobs.java).
- **printer, wolf, and villager.** the three classes appear in `0.33_04.jar`. the latest 2019 menu creates a printer under the label `mob.Prit.name` and offers wolf on the same page. the 2022 source retains a printer model. reborn registers villagers and has no printer or wolf entry. [`0.33_04.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33_04.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 printer model](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/model/PrinterModel.java), [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java).
- **cutemil and speer.** both classes appear in `0.34pre.jar` and `0.34pre2.jar`. speer has a spawn-menu action in the latest 2019 jar and in the 2022 source. cutemil has no verified spawn path. [`0.34pre.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 speer menu action](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMobs.java).

## unused classes in the 2019 jars

### mob loader and mod api experiments

`MobLoader` sits at the jar root. in 0.33, its `loadmobs` method returns immediately. in 0.34 pre-release 2, the class instead has an empty `Mods` method. both jars launch through `MinecraftStandalone`, and neither has a game-class reference to `MobLoader`. [`0.33.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

0.34 pre-release 2 also packages a `MinecraftMod` base class with empty tick, mob-render, and world-generator callbacks, an `ExampleMod`, a separate `pluginplus.minecraft` experiment, and pf4j classes. the example and plugin classes contain stubs. the jar has no game-class reference to pf4j, no runtime plugin service entry, and no verified startup path that discovers or enables these mods. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

the 2022 `MClassicLoader` creates a desktop game window and starts the game thread. reborn uses a separate `com.mcraft.api.loader.ModLoader` that the game initializes. [2022 desktop launcher](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classicloader/main/MClassicLoader.java), [2026 mod loader](../src/main/java/com/mcraft/api/loader/ModLoader.java).

### leftover mobs, blocks, and projectiles

| class | archive evidence |
| --- | --- |
| `Giant` | present from `031_09.jar` through `0.34pre2.jar`. the standalone giant has no game-class references in the checked jars. the latest boss menu creates the separate `GiantCreeper` class. |
| `Cutemil` | present in both 0.34 pre-release jars with no references from other game classes. the pre-release 2 class uses `/mob/Anime.png` and contains item-drop code. |
| `SiteOpenerBlock` | present in all ten jars, with no references from other game classes. |
| `PoisonedAppleItem` | present from `0.33_02.jar` through both 0.34 pre-releases, with no references from other game classes. |
| `ExplodingArrow` | present in both 0.34 pre-release jars, with no references from other game classes. |

the table reflects class reference checks. see [the pre-release blocks and items](#blocks-and-items-in-034-pre-release-2) for the behavior inside those classes. [`031_09.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/031_09.jar), [`0.33_02.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33_02.jar), [`0.34pre.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre.jar), [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar).

## 2022 title screen and terrain plans

the 2022 title screen opens a finite map size picker with small, normal, and huge options. the title screen labels multiplayer as work in progress. its readme lists custom terrain, flat worlds, day and night, infinite terrain, and dimensions as roadmap entries. [2022 title screen](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/MainNemuScreen.java), [2022 map size picker](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/mainnemu/GenerateLevelScreenMainNemu.java), [2022 roadmap](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/README.md#list-of-roadmap).

**2026 comparison.** reborn has a multiplayer connection screen, a day and night cycle, and finite and infinite world generation with classic, biome, and flat options. its portal sends the player to the spawn of the current world. [classic reborn readme](../README.md), [current main menu](../src/main/java/net/classicremastered/minecraft/gui/MainMenuScreen.java), [current world creation screen](../src/main/java/net/classicremastered/minecraft/gui/GenerateLevelScreen.java), [dimension generation notes](unused-features.md#dimension-generation).

## audio code

the 2019 wiki describes a switch to jcraft for additional sound effects. the 2022 source tree includes jorbis, paulscode, and `de/jarnbjo` code alongside its own sound classes. the 2026 game registers paulscode codecs, with the remaining `de/jarnbjo` code referenced by an unused reader. [2019 wiki](https://github.com/Processmodifyer/0.34/blob/a73202892/wiki/wiki.txt), [2022 audio source tree](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/tree/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java), [current sound manager](../src/main/java/net/classicremastered/minecraft/sound/PaulsCodeSoundManager.java), [legacy decoder notes](unused-features.md#legacy-ogg-vorbis-decoder).

### ironmock sound origins

the project history supplied for this wiki dates the sounds associated with ironmock to 2018, when the developer was ten years old. the current ironmock code assigns anvil use to idle, anvil land to hurt, and anvil break to death. the original 2018 sound files are unavailable for comparison. [current ironmock sound assignments](../src/main/java/net/classicremastered/minecraft/mob/Ironmock.java).

for removals and replacements within classic reborn itself, see [removed features](removed-features.md).
