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

import org.lwjgl.input.Keyboard;
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

        TextureManager tm = this.mc.textureManager;
        ShapeRenderer sr = ShapeRenderer.instance;

        // cache texture ids once per frame
        final int TEX_GUI = tm.load("/gui/gui.png");
        final int TEX_ICONS = tm.load("/gui/icons.png");
        final int TEX_TERR = tm.load("/terrain.png");

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

            boolean hurtFlash = (player.invulnerableTime / 3 % 2 == 1) && player.invulnerableTime >= 10;
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
        this.drawImage(this.width / 2 - 91 - 1 + inv.selected * 20, this.height - 22 - 1, 0, 22, 24, 22);

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
            if (id <= 0)
                continue;

            int x = this.width / 2 - 90 + slot * 20;
            int y = this.height - 16;

            if (id < 256 && Block.blocks[id] != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, -50);

                if (inv.popTime[slot] > 0) {
                    float t = ((float) inv.popTime[slot]) / 5.0F;
                    float s1 = -MathHelper.sin(t * t * (float) Math.PI) * 8.0F;
                    float s2 = MathHelper.sin(t * t * (float) Math.PI) + 1.0F;
                    float s3 = MathHelper.sin(t * (float) Math.PI) + 1.0F;
                    GL11.glTranslatef(10, s1 + 10, 0);
                    GL11.glScalef(s2, s3, 1.0F);
                    GL11.glTranslatef(-10, -10, 0);
                }

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
                GL11.glPopMatrix();

                // restore icons for subsequent 2D HUD draws if any
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, TEX_ICONS);
            } else if (id >= 256) {
                int itemId = id - 256;
                if (itemId >= 0 && itemId < Item.items.length) {
                    Item it = Item.items[itemId];
                    if (it != null) {
                        GL11.glPushMatrix();

                        if (inv.popTime[slot] > 0) {
                            float t = ((float) inv.popTime[slot]) / 5.0F;
                            float s = 1.0F + 0.15F * MathHelper.sin(t * t * (float) Math.PI);
                            GL11.glTranslatef(x + 10, y + 10, -50);
                            GL11.glScalef(s, s, 1.0F);
                            GL11.glTranslatef(-(x + 10), -(y + 10), 0);
                        }

                        GL11.glTranslatef(x + 2, y - 4, -50);
                        String tex = it.getTexture();
                        if (tex != null) {
                            int texId = this.mc.textureManager.load(tex);
                            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                            sr.begin();
                            sr.vertexUV(0, 16, 0, 0, 1);
                            sr.vertexUV(16, 16, 0, 1, 1);
                            sr.vertexUV(16, 0, 0, 1, 0);
                            sr.vertexUV(0, 0, 0, 0, 0);
                            sr.end();
                        }
                        GL11.glPopMatrix();
                    }
                }
            }

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

     // Title string with colored segments
     if (this.mc != null && this.mc.settings != null && this.mc.settings.showFrameRate) {
         String diff = "&7Difficulty: &e" + this.mc.settings.difficulty.getLabel();
         font.render(diff, 2, 12, 0xFFFFFF);
     }
     if (this.mc.settings.showFrameRate) {
         font.render("&7Minecraft &6Classic &b0.45 Pre Release 1", 2, 2, 0xFFFFFF);
         font.render(this.mc.debug, 2, 22, 0xFFFFFF);
     }

     if (this.mc.gamemode instanceof SurvivalGameMode) {
         String score = "Score: &e" + this.mc.player.getScore();
         font.render(score, this.width - font.getWidth(score) - 2, 2, 0xFFFFFF);

         String coins = "MineCoins: &6" + this.mc.player.coins;
         font.render(coins, this.width - font.getWidth(coins) - 2, 12, 0xFFFFFF);

         String rep = "Reputation: " + this.mc.player.villagerReputation;
         int repColor = this.mc.player.villagerReputation < 0 ? 0xFF5555 : 0x55FF55; // red if bad, green if good
         font.render(rep, this.width - font.getWidth(rep) - 2, 22, repColor);

         font.render("Arrows: " + this.mc.player.arrows, this.width / 2 + 8, this.height - 33, 0xFFFFFF);
     }




        int maxLines = (this.mc.currentScreen instanceof ChatInputScreen) ? 20 : 10;
        boolean forceShow = this.mc.currentScreen instanceof ChatInputScreen;

        for (int i = 0; i < this.chat.size() && i < maxLines; ++i) {
            ChatLine line = this.chat.get(i);
            if (line.time < 200 || forceShow) {
                font.render(line.message, 2, this.height - 8 - i * 9 - 20, 0xFFFFFF);
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
