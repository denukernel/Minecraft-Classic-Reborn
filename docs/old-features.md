# minecraft classic features from 2019 to 2022

the 2019 minecraft classic 0.34 project experimented with survival, creative, crafting, and new mobs. the 2022 minecraft classic remastered source retained several of those ideas. this page compares both with the 2026 minecraft classic reborn checkout. similar features can have different implementations across the projects.

## what survives from each year

| period | surviving evidence |
| --- | --- |
| 2019 | the 0.34 repository preserves builds from `031_09.jar` through `0.34pre2.jar`. the december 2019 readme marks the project discontinued. [archived builds](https://github.com/Processmodifyer/0.34/tree/a73202892), [discontinuation commit](https://github.com/Processmodifyer/0.34/commit/a73202892). |
| 2020–2021 | the old readme marks its planned beta and full release as canceled. neither repository history provides a dated public build for these two years. [discontinuation readme](https://github.com/Processmodifyer/0.34/blob/a73202892/README.md). |
| 2022 | the remastered repository preserves an august source snapshot. the readme describes an extension of classic 0.30 with a title screen, new items, blocks, and mobs. [2022 readme](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/README.md), [2022 source history](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/commits/main/). |
| 2026 | classic reborn provides finite and infinite terrain, creative and survival inventories, and a day and night cycle. the comparison below uses the current mod source. [classic reborn readme](../README.md). |

the 2019 findings use the archived wiki, commit dates, and static inspection of five jars. the 2022 findings refer to source. an archived class, texture, or button confirms what was present in the files. its call sites determine whether it had an entry point.

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

## mobs and seasonal assets

### crocodile

the 0.33 jar contains `Crocodille`, `CrocodileModel`, and `mob/croc.png`. the 2019 wiki describes the crocodile as a test mob available through mods, and the 2022 source still has its class and model. the 2026 mob registry has no crocodile entry. [`0.33.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33.jar), [2019 wiki](https://github.com/Processmodifyer/0.34/blob/a73202892/wiki/wiki.txt), [2022 crocodile class](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/game/entity/creature/Crocodille.java), [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java).

### halloween build

the 2019 halloween jar bundles alternate mob textures, including halloween creeper, crocodile, spider, and zombie-themed images. the spider constructor selects `mob/hallowen/naturalinsert.png`. the 2019 history records a separate halloween removal commit. the 2026 checkout has no equivalent halloween texture set. [`0.33_02hallowen.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.33_02hallowen.jar), [halloween removal commit](https://github.com/Processmodifyer/0.34/commit/f6e1719e0), [2026 spider](../src/main/java/net/classicremastered/minecraft/mob/Spider.java).

### spawn menus and older mobs

the 0.34 pre-release 2 jar includes mob, boss, and demonic mob menu classes. the 2022 source verifies action handlers that construct zombie pigmen and humans from the spawn menu and speer, chickens, cows, sheep, and endermen from its submenus. these are source-level menu actions. [`0.34pre2.jar`](https://github.com/Processmodifyer/0.34/blob/a73202892/0.34pre2.jar), [2022 spawn menu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/SpawnMobs.java), [first submenu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMobs.java), [second submenu](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/cheats/MoreMoreMobs.java).

**2026 comparison.** chickens, cows, sheep, and endermen have registry entries. zombie pigman, human, and speer have no corresponding registry entries. reborn uses spawn eggs and commands for manual spawning. [2026 mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java), [spawn egg registration](../src/main/java/net/classicremastered/minecraft/level/itemstack/ItemRegistry.java), [summon command](../src/main/java/net/classicremastered/minecraft/chat/commands/SpawnMobCommand.java).

## 2022 title screen and terrain plans

the 2022 title screen opens a finite map size picker with small, normal, and huge options. the title screen labels multiplayer as work in progress. its readme lists custom terrain, flat worlds, day and night, infinite terrain, and dimensions as roadmap entries. [2022 title screen](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/MainNemuScreen.java), [2022 map size picker](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java/net/classic/remastered/client/gui/mainnemu/GenerateLevelScreenMainNemu.java), [2022 roadmap](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/blob/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/README.md#list-of-roadmap).

**2026 comparison.** reborn has a multiplayer connection screen, a day and night cycle, and finite and infinite world generation with classic, biome, and flat options. its portal sends the player to the spawn of the current world. [classic reborn readme](../README.md), [current main menu](../src/main/java/net/classicremastered/minecraft/gui/MainMenuScreen.java), [current world creation screen](../src/main/java/net/classicremastered/minecraft/gui/GenerateLevelScreen.java), [dimension generation notes](unused-features.md#dimension-generation).

## audio code

the 2019 wiki describes a switch to jcraft for additional sound effects. the 2022 source tree includes jorbis, paulscode, and `de/jarnbjo` code alongside its own sound classes. the 2026 game registers paulscode codecs, with the remaining `de/jarnbjo` code referenced by an unused reader. [2019 wiki](https://github.com/Processmodifyer/0.34/blob/a73202892/wiki/wiki.txt), [2022 audio source tree](https://github.com/Processmodifyer/Minecraft-Classic-Remastered/tree/34ed2cfb8d0343e2763ee8b02e68c563f826d76b/src/main/java), [current sound manager](../src/main/java/net/classicremastered/minecraft/sound/PaulsCodeSoundManager.java), [legacy decoder notes](unused-features.md#legacy-ogg-vorbis-decoder).

for removals and replacements within classic reborn itself, see [removed features](removed-features.md).
