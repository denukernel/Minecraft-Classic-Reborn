<img width="1000" height="99" alt="image" src="https://github.com/user-attachments/assets/7db7fdae-070e-47a6-977d-69646d8f85da" />

# Minecraft Classic Reborn

A Java modification of Minecraft Classic 0.30 with infinite terrain, additional mobs and items, commands, an updated inventory, and a day and night cycle.

## Downloads Have Moved

**New releases are now available at [classicremastered.net](https://classicremastered.net/).**

Download the current runnable game from the website as `minecraft-classic-reborn.jar`. GitHub remains the home for source code, issue reports, and older releases.

- [Download the Game](https://classicremastered.net/downloads/minecraft-classic-reborn.jar)
- [Visit the Website](https://classicremastered.net/)
- [Report a Bug](https://github.com/denukernel/Minecraft-Classic-Reborn/issues)
- [Older GitHub Releases](https://github.com/denukernel/Minecraft-Classic-Reborn/releases)

## How to Play

1. Install Java 16 or newer.
2. Download `minecraft-classic-reborn.jar` from the website.
3. Double-click the JAR, or open a terminal in the download folder and run:

```sh
java -jar minecraft-classic-reborn.jar
```

The launcher downloads native libraries and game resources on the first run. Keep an internet connection available and allow the downloads to finish, including the audio resources.

## Features

### Worlds and Rendering

- Finite worlds, infinite flat worlds, and infinite terrain.
- Day and night cycle with a sun, moon, stars, and changing lighting.
- NBT world saving and loading, including player state.
- First-person and third-person camera modes, toggled with F5.
- Camera collision handling and updated held-item rendering.
- MD3 model rendering.

### Items and Gameplay

- Updated inventory, crafting interface, and item pickup handling.
- Commands including `/spawn`, `/gamemode`, and `/time`.
- Non-block items including tools, swords, bows, and buckets.
- Physics Gun for picking up, holding, dropping, and throwing entities.
- Gravity Gun with a separate 3D model.
- Grappling Hook, Ender Pearl, Health Drink, Damage Bottle, Bag of Salt, and Glass Looker.
- Spawn eggs for registered mobs.
- Animated bow drawing and skeleton ranged attacks.

### Mobs

- Additional mobs, including the Blue Forest Golem and Long Neck Man.
- Blue Forest Golem following behavior, animation, and save support.
- Updated natural spawning, mob hitboxes, and floor contact.
- Improved pathfinding around obstacles, through doorways, and across slopes and stairs.
- Updated wandering, crowd movement, and ranged combat behavior.

### Launcher and Model Editor

- Custom launcher and main menu.
- Automatic native-library and resource downloads.
- A model editor with a 3D viewport, editable parts, textures, and animation support.
- UV painting, undo and redo, model import and export, and save validation.

## Recent Improvements

Recent work includes terrain lighting and rendering changes, smoother camera and held-item behavior, more consistent bow animations, and fixes for inventory handling, world saves, physics interactions, and mob navigation.

The client and model editor have also been split into smaller components to make maintenance easier.

## Still in Development

- Tile entities.
- Dimensions.
- Additional mobs and gameplay features.

Features may change between builds. Report reproducible problems through [GitHub Issues](https://github.com/denukernel/Minecraft-Classic-Reborn/issues), including the game build, steps to reproduce, and screenshots or logs when available.

## Screenshots

These screenshots are from earlier builds and may differ from the current version.

<img width="1280" height="720" alt="An earlier Classic Reborn build" src="https://github.com/user-attachments/assets/4c4fae63-0340-4144-98cd-a314f379f3bb" />
<img width="1280" height="720" alt="Terrain and lighting in Classic Reborn" src="https://github.com/user-attachments/assets/004fd701-6649-4042-9657-f32859d8592a" />
<img width="854" height="480" alt="Custom characters in an earlier build" src="https://github.com/user-attachments/assets/e0c9b5c4-da35-4ad3-9175-ac48813c912a" />

## Gameplay Video

[![Classic Reborn Gameplay](https://img.youtube.com/vi/0FA7ctPQWiE/0.jpg)](https://www.youtube.com/watch?v=0FA7ctPQWiE)

## Source Code

Clone the repository:

```sh
git clone https://github.com/denukernel/Minecraft-Classic-Reborn.git
cd Minecraft-Classic-Reborn
```

Use a JDK that supports Java 16 and import `pom.xml` into a Java IDE such as IntelliJ IDEA or Eclipse. Keep the bundled `libjar` and `repo` directories with the source tree so the local dependencies can be resolved.

- Game launcher entry point: `net.classicremastered.launcher.ClassicLauncher`.
- Model editor entry point: `net.classicremastered.toolkit.modeleditor.ModelEditorApp`.

## Credits and Project Background

Classic Reborn builds on the Java MCraft-Client / Minecraft Classic Forever lineage. Credit to MCraft-Client and ManiaDevelopment for the sources and references, and to Mojang for the original Minecraft Classic.

## License and Ownership

See [LICENSE](LICENSE) for the repository license. Original Minecraft content belongs to its respective owners.

This is an independent project and is not affiliated with, endorsed by, or associated with Mojang or Microsoft.
