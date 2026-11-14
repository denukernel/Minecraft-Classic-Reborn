# minecraft classic reborn removed features

this page records features and behavior that were present in an earlier release or commit and were later removed, replaced, or temporarily disabled. see the [unused features wiki](unused-features.md) for code and assets that remain in the current tree. historical jar findings come from static bytecode inspection.

## mob sounds

### old villager voice

villagers in 0.43, 0.43_01, 0.44, and 0.45 pre-release 1 used `random/classic_hurt` for idle, hurt, and death sounds. the early source sets idle pitch between 0.5 and 0.75, hurt pitch to 0.3, and death pitch to 0.6. the november 1 source update removed these assignments. the november 12 update assigned dedicated villager idle, hit, and death sound pools. [0.43](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.43), [0.45 pre-release 1](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/PreReleases), [early source](https://github.com/denukernel/Minecraft-Classic-Reborn/blob/68ee4b04fbff325e4ed0528284e025428edb9a14/src/main/java/net/classicremastered/minecraft/mob/Villager.java#L34), [placeholder voice removal](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/837766e2105ba747ebc49876c99de60ed31e6b41), [dedicated voice update](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/fd82657accc6bf6af7f19e7804740a9803ef0740).

### old enderman voice

the 0.43 jar used `random/breath` for hurt and `random/anvil_land` for death. the 0.43_01 and 0.44 jars use `random/breath` for idle, hurt, and death. the current enderman has no such voice assignments and plays a dedicated teleport sound when teleporting. [0.43](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.43), [0.43_01](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.43_01), [0.44](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.44), [current enderman code](../src/main/java/net/classicremastered/minecraft/mob/Enderman.java), [teleport sound](../src/main/java/net/classicremastered/minecraft/mob/ai/goal/enderman/EndermanTeleportation.java).

## spawn shortcuts

### f7 sign placement

the 0.45 pre-release notes describe an f7 shortcut that spawned a test sign. early source puts the sign at the player position. the november 12 input refactor deleted that shortcut. signs are now placed using the oak sign item. [0.45 pre-release notes](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/PreReleases), [shortcut removal](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/fd82657accc6bf6af7f19e7804740a9803ef0740), [oak sign placement code](../src/main/java/net/classicremastered/minecraft/level/itemstack/SignItem.java).

### b key tnt thrower spawn

the 0.45 pre-release notes report that the b key used to spawn a tnt thrower and that the shortcut was removed to avoid accidental spawns. the notes leave the removal build uncertain. the current mob registry still includes the tnt thrower. [0.45 pre-release notes](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/PreReleases), [current mob registry](../src/main/java/net/classicremastered/minecraft/mob/MobRegistry.java).

## world creation and saves

### temporary removal of infinite flat

the 0.42 and 0.43 jars expose infinite flat as a world type and construct `LevelInfiniteFlat`. the 0.43_01 notes announce its temporary removal, and the jar for that release omits the selector branch. the june 17 source update restored the option. current world creation supports it through the infinite, classic terrain, and flat settings. [0.42](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.42), [0.43](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.43), [0.43_01](https://github.com/denukernel/Minecraft-Classic-Reborn/releases/tag/0.43_01), [infinite flat restoration](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/7c99271db3bd05f22c7a187923f66d3804314d73), [current world creation code](../src/main/java/net/classicremastered/minecraft/gui/GenerateLevelScreen.java).

### old world save writers

the may 29 nbt update replaced the earlier finite and infinite save writers with nbt variants. the older formats remain in the compatibility loader, while new saves use nbt. [nbt save format update](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/575934acd796e9204b9451bd1d7fb4195c463b8c), [current level io](../src/main/java/net/classicremastered/minecraft/level/LevelIO.java).

## menus and rendering

### stickman main menu mascot

the old menu drew a 64-pixel stickman mascot near its lower-right corner. the may 29 update removed the draw code. the june release commit deleted the old `gui/stickman.png` asset. [mascot draw removal](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/575934acd796e9204b9451bd1d7fb4195c463b8c), [mascot asset removal](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/c281ad5f0816353811727632a9283a9b16b952fd).

### monolithic terrain texture

the november 9 update removed the old `terrain.png` sheet. the current block atlas is built from individual images indexed in `tiles.properties`. [terrain atlas change](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/53a16f465f3af9d2853b0c7b819098538ca4e710), [current atlas builder](../src/main/java/net/classicremastered/minecraft/render/BlockTextureAtlas.java).

## legacy audio pipeline

the february 5 cleanup removed the old music playback thread, positional sound classes, and related game hooks. the current game uses the paulscode sound manager. the unused feature wiki covers the [remaining legacy decoder](unused-features.md#legacy-ogg-vorbis-decoder). [legacy audio cleanup](https://github.com/denukernel/Minecraft-Classic-Reborn/commit/c28c7c034071f5d4e4e7eabf8cb3be1a8f376906), [current sound manager](../src/main/java/net/classicremastered/minecraft/sound/PaulsCodeSoundManager.java).
