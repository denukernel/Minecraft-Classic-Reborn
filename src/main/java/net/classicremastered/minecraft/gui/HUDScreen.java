package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.ChatLine;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.gamemode.SurvivalGameMode;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class HUDScreen extends Screen {

    public List<ChatLine> chat = new ArrayList<>();
    private final Random random = new Random();
    private final Minecraft mc;
    public int width;
    public int height;
    public String hoveredPlayer = null;
    public int ticks = 0;

    public HUDScreen(Minecraft mc, int w, int h) {
        this.mc = mc;
        this.width = w * 240 / h;
        this.height = h * 240 / h;
    }

    public final void render(float partial, boolean mouseOver, int mx, int my) {
        FontRenderer font = this.mc.fontRenderer;
        this.mc.renderer.enableGuiMode();
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

            boolean hurtFlash =
                    (player.invulnerableTime / 3 % 2 == 1) && player.invulnerableTime >= 10;
            int health = Math.max(0, Math.min(20, player.health)); // 0..20 half-hearts
            int lastHealth = Math.max(0, Math.min(20, player.lastHealth));

            // hearts (10 slots)
            for (int i = 0; i < 10; i++) {
                int x = this.width / 2 - 91 + i * 8;
                int y = this.height - 32 + (health <= 4 ? this.random.nextInt(2) : 0);

                // empty container
                this.drawImage(x, y, U_HEART_BG, V_HEART_BG, 9, 9);

                // damage flash overlay uses lastHealth
                if (hurtFlash) {
                    if ((i * 2 + 1) < lastHealth) {
                        this.drawImage(x, y, U_HEART_FULL, V_HEART_FULL, 9, 9);
                    } else if ((i * 2 + 1) == lastHealth) {
                        this.drawImage(x, y, U_HEART_HALF, V_HEART_HALF, 9, 9);
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

        // --------------------------------------------------------------------
        // HOTBAR background + selector ==> gui.png
        // --------------------------------------------------------------------
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_GUI);

        Inventory inv = this.mc.player.inventory;
        this.imgZ = -90.0F;

        this.drawImage(this.width / 2 - 91, this.height - 22, 0, 0, 182, 22);
        this.drawImage(
                this.width / 2 - 91 - 1 + inv.selected * 20, this.height - 22 - 1, 0, 22, 24, 22);

        // --------------------------------------------------------------------
        // Crosshair ==> icons.png
        // --------------------------------------------------------------------
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_ICONS);
        this.drawImage(this.width / 2 - 7, this.height / 2 - 7, 0, 0, 16, 16);

        // --------------------------------------------------------------------
        // Hotbar items (ONLY first 9 slots, even if inventory is larger)
        // --------------------------------------------------------------------
        int hotbar = Math.min(9, inv.slots.length);
        for (int slot = 0; slot < hotbar; ++slot) {
            int id = inv.slots[slot];
            if (id <= 0) continue;

            int x = this.width / 2 - 90 + slot * 20;
            int y = this.height - 16;
            float pop = (inv.popTime[slot] - partial) / Inventory.POP_TIME_DURATION;

            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, -50);
            if (pop > 0) {
                float bounce = MathHelper.sin(pop * pop * (float) Math.PI);
                GL11.glTranslatef(10, 10 - bounce * 8.0F, 0);
                GL11.glScalef(1.0F + bounce, 1.0F + MathHelper.sin(pop * (float) Math.PI), 1.0F);
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

            if (inv.count[slot] > 1) {
                String countStr = "" + inv.count[slot];
                font.render(countStr, x + 19 - font.getWidth(countStr), y + 6, 0xFFFFFF);
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
        // Title string with colored segments
        if (this.mc != null && this.mc.settings != null && this.mc.settings.showFrameRate) {
            String diff = "&7Difficulty: &e" + this.mc.settings.difficulty.getLabel();
            font.render(diff, 2, 12, 0xFFFFFF);
        }
        long totalMem = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        if (totalMem < 4000) {
            String warn = "WARNING: Your PC has less than 4 GB RAM. Expect performance issues.";
            int color = (ticks / 20 % 2 == 0) ? 0xFF4444 : 0xFF8888;
            drawCenteredString(this.mc.fontRenderer, warn, this.width / 2, 2, color);
        }
        if (this.mc.settings.showFrameRate) {
            font.render("&7Minecraft &6Classic &b0.30", 2, 2, 0xFFFFFF);
            font.render(this.mc.debug, 2, 22, 0xFFFFFF);
        }
        if (this.mc.settings.showFrameRate) {
            font.render(this.mc.debug, 2, 22, 16777215);
        }
        if (this.mc.gamemode instanceof SurvivalGameMode) {
            String score = "Score: &e" + this.mc.player.getScore();
            font.render(score, this.width - font.getWidth(score) - 2, 2, 0xFFFFFF);

            String coins = "MineCoins: &6" + this.mc.player.coins;
            font.render(coins, this.width - font.getWidth(coins) - 2, 12, 0xFFFFFF);

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

                if (msg.contains("[Open folder]")) {
                    String left = msg.substring(0, msg.indexOf("[Open folder]"));
                    String clickable = "[Open folder]";
                    String right = msg.substring(msg.indexOf("[Open folder]") + clickable.length());

                    int x = 2;
                    font.render(left, x, yPos, 0xFFFFFF);
                    x += font.getWidth(left);

                    // detect hover
                    int mouseX = Mouse.getX() * this.width / this.mc.width;
                    int mouseY = this.height - (Mouse.getY() * this.height / this.mc.height) - 1;
                    boolean hovering =
                            mouseX >= x
                                    && mouseX <= x + font.getWidth(clickable)
                                    && mouseY >= yPos - 1
                                    && mouseY <= yPos + 9;

                    // render clickable text (hover = underline + brighter color)
                    int color = hovering ? 0x88FFFF : 0x55FFFF;
                    font.render(clickable, x, yPos, color);
                    if (hovering) {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        GL11.glColor3f(0.33F, 1.0F, 1.0F);
                        GL11.glBegin(GL11.GL_LINES);
                        GL11.glVertex2f(x, yPos + 9);
                        GL11.glVertex2f(x + font.getWidth(clickable), yPos + 9);
                        GL11.glEnd();
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                    }

                    x += font.getWidth(clickable);
                    font.render(right, x, yPos, 0xFFFFFF);
                } else {
                    font.render(msg, 2, yPos, 0xFFFFFF);
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
                    0xffc82c4c);
            String name = "Stick Figure";
            if (boss.isEnraged()) name += " - RAGE";
            if (boss.ai
                    instanceof
                    net.classicremastered.minecraft.mob.ai.stickfigure.StickFigureAI ai) {
                if (ai.powers.phase
                        != net.classicremastered.minecraft.mob.ai.stickfigure.StickFigurePowers
                                .Phase.IDLE) name += " - " + ai.powers.phase.name();
                else if (ai.powers.rushTicks > 0) name += " - RUSH";
            }
            drawCenteredString(font, name, width / 2, 4, 0xffffff);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void onMouseClick(int x, int y, int button) {
        if (button != 0) return;

        // Convert from full window → HUD logical scale
        int scaledX = (int) ((x / (float) this.mc.width) * this.width);
        int scaledY = (int) ((y / (float) this.mc.height) * this.height);

        int lineHeight = 9;
        int baseY = this.height - 8 - 20;
        int maxLines =
                (this.mc.currentScreen
                                instanceof net.classicremastered.minecraft.chat.ChatInputScreen)
                        ? 20
                        : 10;

        for (int i = 0; i < this.chat.size() && i < maxLines; ++i) {
            ChatLine line = this.chat.get(i);
            if (!line.message.contains("[Open folder]")) continue;

            int yTop = baseY - i * lineHeight;
            int yBottom = yTop - lineHeight;

            // check if click inside the text's Y range
            if (scaledY >= yBottom && scaledY <= yTop) {
                String left = line.message.substring(0, line.message.indexOf("[Open folder]"));
                int leftW = this.mc.fontRenderer.getWidth(left);
                int openW = this.mc.fontRenderer.getWidth("[Open folder]");
                int openStart = 2 + leftW;
                int openEnd = openStart + openW;

                // now compare using scaledX (not raw x)
                if (scaledX >= openStart && scaledX <= openEnd) {
                    java.io.File dir =
                            new java.io.File(
                                    net.classicremastered.minecraft.Minecraft.mcDir, "screenshots");
                    net.classicremastered.minecraft.util.FileOpener.openDirectory(dir);
                    this.mc.hud.addChat("&7Opening screenshots folder...");
                    System.out.println("[HUD] Clicked Open folder → " + dir.getAbsolutePath());
                    return;
                }
            }
        }
    }

    public final void addChat(String msg) {
        this.chat.add(0, new ChatLine(msg));
        while (this.chat.size() > 50) {
            this.chat.remove(this.chat.size() - 1);
        }
    }
}
