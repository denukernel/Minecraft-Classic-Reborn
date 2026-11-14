package net.classicremastered.minecraft.gui;

import net.classicremastered.launcher.Constants;
import net.classicremastered.minecraft.ChatLine;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.generator.BiomeLevelGenerator;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HUDScreen extends Screen {

    public List<ChatLine> chat = new ArrayList<>();
    private final Minecraft mc;
    private net.classicremastered.minecraft.level.Level debugBiomeLevel;
    private BiomeLevelGenerator debugBiomes;
    public int width;
    public int height;
    public String hoveredPlayer = null;
    public int ticks = 0;
    private Player heartPlayer;
    private Level heartLevel;
    private int observedHealth, displayHealth, healthBlinkUntil, settleHealthAt;
    private List<String> debugHardware;

    public HUDScreen(Minecraft mc, int w, int h) {
        this.mc = mc;
        this.width = Minecraft.guiWidth(w, h);
        this.height = Minecraft.guiHeight(w, h);
    }

    private void updateHeartDisplay(Player player, int health) {
        if (player != heartPlayer
                || player.level != heartLevel
                || observedHealth == 0 && health > 0) {
            heartPlayer = player;
            heartLevel = player.level;
            displayHealth = health;
            healthBlinkUntil = settleHealthAt = ticks;
        } else if (health != observedHealth) {
            healthBlinkUntil = ticks + (health < observedHealth ? 20 : 10);
            settleHealthAt = ticks + 20;
        }
        if (ticks >= settleHealthAt) displayHealth = health;
        observedHealth = health;
    }

    private void renderDebugOverlay(FontRenderer font) {
        Player player = mc.player;
        int x = (int) Math.floor(player.x);
        int y = (int) Math.floor(player.bb.y0);
        int z = (int) Math.floor(player.z);
        float yaw = (float) Math.IEEEremainder(player.aimYaw(1f), 360);
        String facing =
                switch (Math.floorMod(Math.round(yaw / 90f), 4)) {
                    case 0 -> "north (-Z)";
                    case 1 -> "east (+X)";
                    case 2 -> "south (+Z)";
                    default -> "west (-X)";
                };
        String mode =
                mc.gamemode.isSpectator()
                        ? "Spectator"
                        : mc.gamemode.isSurvival() ? "Survival" : "Creative";
        List<String> world =
                new ArrayList<>(
                        List.of(
                                "Minecraft Classic Reborn " + Constants.MINECRAFT_VERSION,
                                mc.debug,
                                "",
                                String.format(
                                        Locale.ROOT,
                                        "XYZ: %.3f / %.3f / %.3f",
                                        player.x,
                                        player.bb.y0,
                                        player.z),
                                "Block: " + x + " " + y + " " + z,
                                "Chunk: " + (x >> 4) + " " + (y >> 4) + " " + (z >> 4),
                                String.format(
                                        Locale.ROOT,
                                        "Facing: %s (%.1f / %.1f)",
                                        facing,
                                        yaw,
                                        player.xRot),
                                String.format(
                                        Locale.ROOT,
                                        "Client brightness: %.2f",
                                        mc.level.lightEngine.getBrightness(x, y, z)),
                                "Time: "
                                        + mc.level.timeOfDay
                                        + (mc.level.doDayNightCycle ? " (3x)" : " (paused)"),
                                "Mode: " + mode + " / " + mc.settings.difficulty.getLabel()));
        if (mc.level.biomeSeed != null) {
            if (mc.level != debugBiomeLevel) {
                debugBiomeLevel = mc.level;
                debugBiomes = BiomeLevelGenerator.forLevel(mc.level);
            }
            world.add(
                    "Biome: "
                            + debugBiomes
                                    .surfaceBiomeAt(x, z, mc.level.depth, mc.level.flatBiomes)
                                    .name()
                                    .toLowerCase(Locale.ROOT));
        } else {
            debugBiomeLevel = null;
            debugBiomes = null;
        }
        world.add("");
        world.add("F3 + N: Toggle spectator");
        world.add("F3 + B: Toggle entity hitboxes");
        world.add("F3 + P: Toggle mob paths");
        if (mc.debugPaths)
            world.add("Cyan: route | Yellow: next | Red: target | Green: shore exit");

        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long allocated = runtime.totalMemory();
        long used = allocated - runtime.freeMemory();
        List<String> system =
                new ArrayList<>(
                        List.of(
                                "Java: "
                                        + System.getProperty("java.version")
                                        + " ("
                                        + System.getProperty("os.arch")
                                        + ")",
                                String.format(
                                        Locale.ROOT,
                                        "Heap: %d%% %d/%d MiB",
                                        used * 100 / max,
                                        mib(used),
                                        mib(max)),
                                String.format(
                                        Locale.ROOT,
                                        "Allocated heap: %d%% %d MiB",
                                        allocated * 100 / max,
                                        mib(allocated)),
                                "Display: "
                                        + mc.width
                                        + "x"
                                        + mc.height
                                        + " / "
                                        + mc.settings.graphicsMode.label));
        system.addAll(debugHardware());
        MovingObjectPosition blockHit = mc.debugBlockTarget;
        if (blockHit != null) {
            int id = mc.level.getTile(blockHit.x, blockHit.y, blockHit.z);
            Block block = Block.blocks[id];
            system.add("");
            system.add("Targeted Block: " + blockHit.x + " " + blockHit.y + " " + blockHit.z);
            if (block != null) system.add(block.name + " (" + id + ")");
        }
        if (mc.selected != null && mc.selected.entity != null) {
            system.add("");
            system.add("Targeted Entity");
            system.add(mc.selected.entity.getClass().getSimpleName());
        }
        int leftWidth =
                Math.min(width * 3 / 5, world.stream().mapToInt(font::getWidth).max().orElse(0));
        drawDebugLines(font, world, leftWidth, false);
        drawDebugLines(font, system, width - leftWidth - 8, true);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private List<String> debugHardware() {
        if (debugHardware != null) return debugHardware;
        List<String> lines = new ArrayList<>();
        var os = ManagementFactory.getOperatingSystemMXBean();
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpu == null || cpu.isBlank()) cpu = os.getArch();
        lines.add("CPU: " + os.getAvailableProcessors() + " logical / " + cpu);
        if (os instanceof com.sun.management.OperatingSystemMXBean system
                && system.getTotalMemorySize() > 0) {
            lines.add(
                    String.format(
                            Locale.ROOT,
                            "System RAM: %.1f GiB",
                            system.getTotalMemorySize() / (1024.0 * 1024 * 1024)));
        }
        lines.add("GPU: " + GL11.glGetString(GL11.GL_RENDERER));
        lines.add("GPU vendor: " + GL11.glGetString(GL11.GL_VENDOR));
        lines.add("OpenGL: " + GL11.glGetString(GL11.GL_VERSION));
        lines.add("OS: " + os.getName() + " " + os.getVersion());
        debugHardware = List.copyOf(lines);
        return debugHardware;
    }

    private static long mib(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private void drawDebugLines(
            FontRenderer font, List<String> lines, int maxWidth, boolean right) {
        int y = 2;
        for (String line : lines) {
            while (!line.isEmpty() && y + 8 < height) {
                int end = line.length();
                while (end > 1 && font.getWidth(line.substring(0, end)) > maxWidth) end--;
                if (end < line.length()) {
                    int space = line.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                String part = line.substring(0, end);
                int lineWidth = font.getWidth(part);
                int x = right ? width - 2 - lineWidth : 2;
                drawBox(x - 1, y - 1, x + lineWidth + 1, y + 8, 0x90505050);
                font.render(part, x, y, 0xE0E0E0);
                line = line.substring(end).stripLeading();
                if (!line.isEmpty()) y += 9;
            }
            y += 9;
        }
    }

    public final void render(float partial, boolean mouseOver, int mx, int my) {
        FontRenderer font = this.mc.fontRenderer;
        this.mc.renderer.enableGuiMode();
        boolean showDebug = mc.debugOverlay && mc.currentScreen == null;
        if (!showDebug)
            renderBossBar(font);

        TextureManager tm = this.mc.textureManager;
        ShapeRenderer sr = ShapeRenderer.instance;

        // cache texture ids once per frame
        final int TEX_GUI = tm.load("/gui/gui.png");
        final int TEX_ICONS = tm.load("/gui/icons.png");
        final int TEX_TERR =
                tm.load(net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE);

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // --------------------------------------------------------------------
        // SURVIVAL HUD (hearts + air) ==> icons.png
        // --------------------------------------------------------------------
        if (this.mc.gamemode.isSurvival()) {
            Player player = this.mc.player;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_ICONS);

            // icons.png UVs (9x9 quads)
            final int U_HEART_BG = 16, V_HEART_BG = 0; // empty container
            final int U_HEART_FULL = 52, V_HEART_FULL = 0;
            final int U_HEART_HALF = 61, V_HEART_HALF = 0;
            final int U_BUBBLE_FULL = 16, V_BUBBLE_FULL = 18;
            final int U_BUBBLE_PART = 25, V_BUBBLE_PART = 18;

            int health = Math.max(0, Math.min(20, player.health)); // 0..20 half-hearts
            updateHeartDisplay(player, health);
            int blinkTicks = healthBlinkUntil - ticks;
            boolean hurtFlash = blinkTicks > 0 && blinkTicks / 3 % 2 == 1;

            // hearts (10 slots)
            for (int i = 0; i < 10; i++) {
                int x = this.width / 2 - 91 + i * 8;
                int y = this.height - 32 + (health <= 4 ? (ticks ^ (i * 17 + ticks / 3)) & 1 : 0);

                // empty container
                this.drawImage(x, y, hurtFlash ? U_HEART_BG + 9 : U_HEART_BG, V_HEART_BG, 9, 9);

                // damage flash overlay uses lastHealth
                if (hurtFlash) {
                    if ((i * 2 + 1) < displayHealth) {
                        this.drawImage(x, y, U_HEART_FULL + 18, V_HEART_FULL, 9, 9);
                    } else if ((i * 2 + 1) == displayHealth) {
                        this.drawImage(x, y, U_HEART_HALF + 18, V_HEART_HALF, 9, 9);
                    }
                }

                // current health
                if ((i * 2 + 1) < health) {
                    this.drawImage(x, y, U_HEART_FULL, V_HEART_FULL, 9, 9);
                } else if ((i * 2 + 1) == health) {
                    this.drawImage(x, y, U_HEART_HALF, V_HEART_HALF, 9, 9);
                }
            }

            // air bubbles when underwater
            if (player.isUnderWater()) {
                int full = (int) Math.ceil((player.airSupply - 2) * 10.0 / 300.0);
                int part = (int) Math.ceil(player.airSupply * 10.0 / 300.0) - full;
                int total = Math.max(0, Math.min(10, full + part));

                for (int i = 0; i < total; i++) {
                    int x = this.width / 2 - 91 + i * 8;
                    int y = this.height - 32 - 9;
                    if (i < full) {
                        this.drawImage(x, y, U_BUBBLE_FULL, V_BUBBLE_FULL, 9, 9);
                    } else {
                        this.drawImage(x, y, U_BUBBLE_PART, V_BUBBLE_PART, 9, 9);
                    }
                }
            }
        }

        if (!this.mc.gamemode.isSpectator()) {
            // --------------------------------------------------------------------
            // HOTBAR background + selector ==> gui.png
            // --------------------------------------------------------------------
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_GUI);

            Inventory inv = this.mc.player.inventory;
            this.imgZ = -90.0F;

            this.drawImage(this.width / 2 - 91, this.height - 22, 0, 0, 182, 22);
            this.drawImage(this.width / 2 - 91 - 1 + inv.selected * 20, this.height - 22 - 1, 0, 22,
                    24, 22);

            // --------------------------------------------------------------------
            // Crosshair ==> icons.png
            // --------------------------------------------------------------------
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_ICONS);
            this.drawImage(this.width / 2 - 7, this.height / 2 - 7, 0, 0, 16, 16);
            if (mc.currentScreen == null && mc.input != null) {
                float charge = mc.input.attackCharge(partial);
                if (charge < 1) {
                    int x = width / 2 - 8, y = height / 2 + 9;
                    int filled = Math.round(charge * 16);
                    drawBox(x, y, x + 16, y + 3, 0xA0000000);
                    if (filled > 0) drawBox(x, y, x + filled, y + 2, 0xFFE0E0E0);
                    GL11.glColor4f(1, 1, 1, 1);
                    GL11.glEnable(GL11.GL_BLEND);
                }
            }

            // --------------------------------------------------------------------
            // Hotbar items (ONLY first 9 slots, even if inventory is larger)
            // --------------------------------------------------------------------
            int hotbar = Math.min(9, inv.slots.length);
            for (int slot = 0; slot < hotbar; ++slot) {
                int id = inv.slots[slot];
                if (id <= 0 || inv.getCount(slot) <= 0)
                    continue;

                int x = this.width / 2 - 90 + slot * 20;
                int y = this.height - 16;
                float pop = (inv.popTime[slot] - partial) / Inventory.POP_TIME_DURATION;

                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, -50);
                if (pop > 0) {
                    float bounce = MathHelper.sin(pop * pop * (float) Math.PI);
                    GL11.glTranslatef(10, 10 - bounce * 8.0F, 0);
                    GL11.glScalef(1.0F + bounce, 1.0F + MathHelper.sin(pop * (float) Math.PI),
                            1.0F);
                    GL11.glTranslatef(-10, -10, 0);
                }

                id = net.classicremastered.minecraft.level.itemstack.Item.iconId(id);
                if (id < 256 && Block.blocks[id] != null) {
                    GL11.glScalef(10, 10, 10);
                    GL11.glTranslatef(1.0F, 0.5F, 0.0F);
                    GL11.glRotatef(-30, 1, 0, 0);
                    GL11.glRotatef(45, 0, 1, 0);
                    GL11.glTranslatef(-1.5F, 0.5F, 0.5F);
                    GL11.glScalef(-1, -1, -1);

                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_TERR);
                    sr.begin();
                    Block.blocks[id].renderFullbright(sr);
                    sr.end();

                    // restore icons for subsequent 2D HUD draws if any
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_ICONS);
                } else if (id >= 256) {
                    int itemId = id - 256;
                    if (itemId >= 0 && itemId < Item.items.length) {
                        Item it = Item.items[itemId];
                        if (it != null) {
                            GL11.glTranslatef(2, -4, 0);
                            it.renderIcon(this.mc.textureManager, sr);
                        }
                    }
                }
                GL11.glPopMatrix();

                drawDurabilityBar(id, inv.armorWear(slot), x + 2, y - 4);
                if (inv.count[slot] > 1) {
                    String countStr = "" + inv.count[slot];
                    font.render(countStr, x + 19 - font.getWidth(countStr), y + 6, 0xFFFFFF);
                }
            }
            if (mc.currentScreen == null && inv.getCount(inv.selected) > 0) {
                String durability =
                        ItemTooltip.durabilityText(inv.getSelected(), inv.armorWear(inv.selected));
                if (!durability.isEmpty()) {
                    drawCenteredString(font, durability, width / 2, height - 61, 0xFFFFFF);
                }
            }
        }

        // --------------------------------------------------------------------
        // Text overlays (debug, score, chat)
        // --------------------------------------------------------------------
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);

        if (this.mc.player != null && this.mc.gamemode.isSurvival())
            font.render(
                    "Armor: "
                            + net.classicremastered.minecraft.level.itemstack.Equipment.points(
                                    this.mc.player),
                    this.width / 2 - 90,
                    this.height - 51,
                    0xD0D0D0);
        long totalMem = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        if (!showDebug && totalMem < 4000) {
            String warn = "WARNING: Your PC has less than 4 GB RAM. Expect performance issues.";
            int color = (ticks / 20 % 2 == 0) ? 0xFF4444 : 0xFF8888;
            drawCenteredString(this.mc.fontRenderer, warn, this.width / 2, 2, color);
        }
        if (showDebug) {
            renderDebugOverlay(font);
        } else if (this.mc.settings.showFrameRate) {
            font.render("&7Minecraft &6Classic &b0.30", 2, 2, 0xFFFFFF);
            font.render("&7Difficulty: &e" + mc.settings.difficulty.getLabel(), 2, 12, 0xFFFFFF);
            font.render(this.mc.debug, 2, 22, 0xFFFFFF);
        }
        if (!showDebug && this.mc.gamemode instanceof SurvivalGameMode) {
            String score = "Score: &e" + this.mc.player.getScore();
            font.render(score, this.width - font.getWidth(score) - 2, 2, 0xFFFFFF);

            String coins = "MineCoins: &6" + this.mc.player.coins;
            int coinX = this.width - font.getWidth(coins) - 2;
            drawMinecoinIcon(tm, coinX - 11, 11);
            font.render(coins, coinX, 12, 0xFFFFFF);

            String rep = "Reputation: " + this.mc.player.villagerReputation;
            int repColor =
                    this.mc.player.villagerReputation < 0
                            ? 0xFF5555
                            : 0x55FF55; // red if bad, green if good
            font.render(rep, this.width - font.getWidth(rep) - 2, 22, repColor);
        }

        int maxLines = (this.mc.currentScreen instanceof ChatInputScreen) ? 20 : 10;
        boolean forceShow = this.mc.currentScreen instanceof ChatInputScreen;

        for (int i = 0; i < this.chat.size() && i < maxLines; ++i) {
            ChatLine line = this.chat.get(i);
            if (line.time < 200 || forceShow) {
                int yPos = this.height - 8 - i * 9 - 20;
                String msg = line.message;

                font.render(msg, 2, yPos, 0xFFFFFF);
                int x = 2 + font.getWidth(msg);
                int mouseX = Mouse.isCreated() ? Mouse.getX() * width / mc.width : -1;
                int mouseY =
                        Mouse.isCreated() ? height - Mouse.getY() * height / mc.height - 1 : -1;
                for (ChatLine.Action action : line.actions) {
                    x += 4;
                    int actionWidth = font.getWidth(action.label());
                    boolean hover =
                            mouseX >= x
                                    && mouseX < x + actionWidth
                                    && mouseY >= yPos
                                    && mouseY < yPos + 9;
                    font.render(action.label(), x, yPos, hover ? 0x88FFFF : 0x55FFFF);
                    x += actionWidth;
                }
            }
        }
    }

    public void renderBossBar(FontRenderer font) {
        net.classicremastered.minecraft.mob.StickFigure boss =
                net.classicremastered.minecraft.mob.StickFigure.nearestBoss(mc.level, mc.player);
        if (boss == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            int barWidth = Math.min(182, width - 20), left = (width - barWidth) / 2;
            boolean enraged = boss.isEnraged();
            drawBox(left - 1, 14, left + barWidth + 1, 22, 0xff111111);
            drawBox(left, 15, left + barWidth, 21, 0xff402030);
            drawBox(
                    left,
                    15,
                    left
                            + Math.round(
                                    barWidth
                                            * Math.max(
                                                    0,
                                                    Math.min(
                                                            1,
                                                            boss.health
                                                                    / (float)
                                                                            net.classicremastered
                                                                                    .minecraft.mob
                                                                                    .StickFigure
                                                                                    .MAX_HEALTH))),
                    21,
                    enraged ? 0xffed5432 : 0xffc82c4c);
            int rageMarker = left + Math.round(barWidth * .25f);
            drawBox(rageMarker, 14, rageMarker + 1, 22, 0xffffbe70);
            String cue = enraged ? "Enraged" : "";
            if (boss.ai
                    instanceof
                    net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI ai) {
                cue =
                        switch (ai.powers.phase) {
                            case IDLE -> cue;
                            case THROW -> {
                                if (ai.powers.phaseTicks
                                        >= net.classicremastered.minecraft.mob.ai.stickfigure
                                                .StickFigurePowers.THROW_RELEASE_TICK)
                                    yield "Recovering";
                                yield ai.powers.heldBlockId() > 0
                                        ? "Block incoming"
                                        : "Throw incoming";
                            }
                            case FIREBALL -> "Fireball incoming";
                            case UPPERCUT -> "Uppercut incoming";
                            case BARRAGE ->
                                    ai.powers.canPlayerEscape(mc.player)
                                            ? "Press Jump to escape"
                                            : "Aerial barrage";
                            case SLAM -> "Slam incoming";
                            case PHYSICS_GUN -> "Physics gun hold";
                            case RAGE_TRANSITION -> "Entering rage";
                            case RUSH_WINDUP -> "Charge incoming";
                            case RUSH -> "Charging";
                            case STAGGER -> "Staggered";
                        };
            }
            drawCenteredString(font, "Stick Figure", width / 2, 4, 0xffffff);
            if (!cue.isEmpty())
                drawCenteredString(font, cue, width / 2, 25, enraged ? 0xffffbe70 : 0xffffff);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void onMouseClick(int x, int y, int button) {
        onChatClick(x * width / mc.width, y * height / mc.height, button);
    }

    public boolean onChatClick(int x, int y, int button) {
        if (button != 0) return false;
        boolean chatOpen = mc.currentScreen instanceof ChatInputScreen;
        int maxLines = chatOpen ? 20 : 10;
        for (int i = 0; i < chat.size() && i < maxLines; i++) {
            ChatLine line = chat.get(i);
            int top = height - 28 - i * 9;
            if ((!chatOpen && line.time >= 200) || y < top || y >= top + 9) continue;
            int left = 2 + mc.fontRenderer.getWidth(line.message);
            for (ChatLine.Action action : line.actions) {
                left += 4;
                int actionWidth = mc.fontRenderer.getWidth(action.label());
                if (x >= left && x < left + actionWidth) {
                    action.run().run();
                    return true;
                }
                left += actionWidth;
            }
        }
        return false;
    }

    public final void addChat(String msg) {
        addChat(msg, new ChatLine.Action[0]);
    }

    public final void addChat(String msg, ChatLine.Action... actions) {
        chat.add(0, new ChatLine(msg, actions));
        while (chat.size() > 50) chat.remove(chat.size() - 1);
    }

    public void screenshotSaved(java.io.File file, String label) {
        addChat(
                "&a" + label + " saved!",
                new ChatLine.Action("[View]", () -> openScreenshot(file, false)),
                new ChatLine.Action("[Paint]", () -> openScreenshot(file, true)),
                new ChatLine.Action(
                        "[Folder]",
                        () ->
                                net.classicremastered.minecraft.util.FileOpener.openDirectory(
                                        file.getParentFile())));
    }

    private void openScreenshot(java.io.File file, boolean paint) {
        boolean opened =
                paint
                        ? net.classicremastered.minecraft.util.FileOpener.editImage(file)
                        : net.classicremastered.minecraft.util.FileOpener.openImage(file);
        if (!opened) addChat("&cCould not open " + file.getName());
    }
}
