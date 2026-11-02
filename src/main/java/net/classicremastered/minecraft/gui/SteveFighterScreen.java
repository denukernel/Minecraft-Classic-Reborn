package net.classicremastered.minecraft.gui;

import net.classicremastered.minigame.SteveFighterGame;
import net.classicremastered.minigame.SteveFighterGame.Mob;
import net.classicremastered.minigame.SteveFighterGame.Kind;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** A side-view arcade game that leaves normal worlds and inventory untouched. */
public final class SteveFighterScreen extends GuiScreen {
    private final GuiScreen parent;
    private SteveFighterGame game = new SteveFighterGame(System.nanoTime());
    private boolean started, paused;

    public SteveFighterScreen(GuiScreen parent) { this.parent = parent; }

    @Override
    public void onOpen() { updateButtons(); }

    private void updateButtons() {
        buttons.clear();
        int x = width / 2 - 100;
        if (!started || paused || game.dead) {
            if (!game.dead) buttons.add(new Button(1, x, 120, 200, 20, started ? "Resume" : "Start fighting"));
            if (started) buttons.add(new Button(2, x, game.dead ? 120 : 144, 200, 20, "Try again"));
            buttons.add(new Button(0, x, started && !game.dead ? 168 : 144, 200, 20, "Back to minigames"));
        }
    }

    @Override
    public void tick() {
        if (!started || paused || game.dead) return;
        boolean keyboard = Keyboard.isCreated();
        game.tick(keyboard && (down(Keyboard.KEY_A) || down(Keyboard.KEY_LEFT)),
                keyboard && (down(Keyboard.KEY_D) || down(Keyboard.KEY_RIGHT)),
                keyboard && (down(Keyboard.KEY_SPACE) || down(Keyboard.KEY_W) || down(Keyboard.KEY_UP)),
                (keyboard && down(Keyboard.KEY_J)) || (Mouse.isCreated() && Mouse.isButtonDown(0)),
                keyboard && (down(Keyboard.KEY_LSHIFT) || down(Keyboard.KEY_RSHIFT) || down(Keyboard.KEY_K)));
        if (game.dead) updateButtons();
    }

    private static boolean down(int key) { return Keyboard.isKeyDown(key); }

    @Override
    protected void onKeyPress(char ch, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (!started) minecraft.setCurrentScreen(parent);
            else if (!game.dead) { paused = !paused; updateButtons(); }
        } else if (key == Keyboard.KEY_RETURN) {
            if (game.dead) restart();
            else { started = true; paused = false; updateButtons(); }
        }
    }

    private void restart() {
        game = new SteveFighterGame(System.nanoTime());
        started = true; paused = false; updateButtons();
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id == 0) minecraft.setCurrentScreen(parent);
        if (button.id == 1) { started = true; paused = false; updateButtons(); }
        if (button.id == 2) restart();
    }

    @Override
    public void render(int mouseX, int mouseY) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        drawBox(0, 0, width, height, 0xFF101820);
        float scale = Math.min(width / 426f, height / 240f);
        GL11.glPushMatrix();
        GL11.glTranslatef((width - 426 * scale) / 2, (height - 240 * scale) / 2, 0);
        GL11.glScalef(scale, scale, 1);
        arena();
        for (Mob mob : game.mobs) fighter(mob.x, SteveFighterGame.FLOOR,
                mob.x < game.x ? 1 : -1, mob.kind, mob.flash > 0 || mob.fuse > 0 && game.ticks % 4 < 2);
        if (game.invulnerable == 0 || game.ticks % 4 < 2 || !started) {
            fighter(game.x, game.y, game.facing, null, false);
        }
        for (SteveFighterGame.Arrow arrow : game.arrows) {
            int ax = (int)arrow.x, ay = (int)arrow.y;
            drawBox(ax - 5, ay, ax + 5, ay + 1, 0xFFE8D2A0);
            drawBox(ax + (arrow.vx > 0 ? 4 : -6), ay - 1,
                    ax + (arrow.vx > 0 ? 6 : -4), ay + 2, 0xFFEEEEEE);
        }
        for (SteveFighterGame.Spark spark : game.sparks) {
            drawBox((int)spark.x, (int)spark.y, (int)spark.x + 2, (int)spark.y + 2, spark.color);
        }
        hud();
        GL11.glPopMatrix();
        if (!started || paused || game.dead) {
            drawBox(0, 0, width, height, 0xCC081321);
            String title = !started ? "STEVE FIGHTER" : game.dead ? "GAME OVER" : "PAUSED";
            drawCenteredString(fontRenderer, title, width / 2, 35, 0x66E5D3);
            String subtitle = started ? "Round " + game.round + "  |  Score " + game.score + "  |  Kills " + game.kills
                    : "A sword. One Steve. Endless waves.";
            drawCenteredString(fontRenderer, subtitle, width / 2, 53, 0xFFFFFF);
            if (!started) {
                drawCenteredString(fontRenderer, "A/D or arrows: move   Space: jump", width / 2, 76, 0xCBD8E5);
                drawCenteredString(fontRenderer, "J or left click: sword   Shift/K: dash", width / 2, 90, 0xCBD8E5);
                drawCenteredString(fontRenderer, "Clear each round to recover 4 health.", width / 2, 104, 0xCBD8E5);
            }
            super.render(mouseX, mouseY);
            drawBox(0, height - 29, width, height, 0xFF101E2C);
            drawCenteredString(fontRenderer, "Enter: " + (game.dead ? "retry" : "play") + "   Esc: pause / resume",
                    width / 2, height - 17, 0x96A6B8);
        }
        GL11.glPopAttrib();
    }

    private void arena() {
        drawBox(0, 0, 426, 190, 0xFF83BDD0);
        drawBox(347, 42, 369, 64, 0xFFFFE5A0);
        for (int i = 0; i < 5; i++) {
            int cx = i * 102 - 25;
            drawBox(cx, 49 + i % 2 * 16, cx + 48, 57 + i % 2 * 16, 0xFFDDECEF);
            drawBox(cx + 10, 45 + i % 2 * 16, cx + 34, 57 + i % 2 * 16, 0xFFDDECEF);
        }
        for (int i = 0; i < 10; i++) {
            int x = i * 49;
            drawBox(x, 128 - i % 3 * 14, x + 55, 190, 0xFF65939B);
            drawBox(x + 7, 119 - i % 3 * 14, x + 40, 190, 0xFF65939B);
        }
        for (int tx : new int[] {34, 104, 312, 391}) {
            drawBox(tx - 3, 126, tx + 4, 190, 0xFF5A5843);
            drawBox(tx - 22, 110, tx + 23, 138, 0xFF416D58);
            drawBox(tx - 15, 99, tx + 15, 145, 0xFF416D58);
            drawBox(tx - 18, 111, tx + 5, 123, 0xFF507E61);
        }
        drawBox(0, 190, 426, 240, 0xFF725441);
        drawBox(0, 190, 426, 196, 0xFF709D48);
        drawBox(0, 196, 426, 199, 0xFF4F7433);
        for (int i = 0; i < 54; i++) {
            int dx = i * 31 % 426, dy = 202 + i * 13 % 35;
            drawBox(dx, dy, dx + 5, dy + 3, i % 2 == 0 ? 0xFF88694D : 0xFF594738);
        }
        drawBox(0, 213, 426, 240, 0xE615202B);
        drawCenteredString(fontRenderer, "A/D move  Space jump  J/click sword  Shift/K dash", 213, 218, 0xE0E9F0);
        drawCenteredString(fontRenderer, "Esc pause   |   Sword swings can block arrows", 213, 230, 0x95AFBD);
    }

    private void hud() {
        drawBox(0, 0, 426, 33, 0xE615202B);
        drawString(fontRenderer, "STEVE FIGHTER", 8, 6, 0x66E5D3);
        drawString(fontRenderer, "Round " + Math.max(1, game.round), 176, 6, 0xFFFFFF);
        drawString(fontRenderer, "Score " + game.score, 315, 6, 0xFFFFBB);
        drawBox(8, 20, 110, 27, 0xFF492D39);
        drawBox(9, 21, 9 + game.health * 5, 26, 0xFFEA6D74);
        drawString(fontRenderer, game.health + "/20", 116, 20, 0xFFFFFF);
        drawString(fontRenderer, "Mobs " + (game.mobs.size() + game.remainingToSpawn), 176, 20, 0xCBD8E5);
        drawString(fontRenderer, game.dashCooldown == 0 ? "Dash ready" : "Dash recharging", 315, 20, 0xCBD8E5);
        if (started && !paused && !game.dead && game.intermission > 0) {
            drawCenteredString(fontRenderer, game.round == 0 ? "Get ready!" : "Round cleared! +4 health", 213, 62, 0xFFFFBB);
            drawCenteredString(fontRenderer, "Round " + (game.round + 1) + " in " + ((game.intermission + 19) / 20), 213, 76, 0xFFFFFF);
        }
    }

    private void fighter(float x, float y, int facing, Kind kind, boolean flash) {
        GL11.glPushMatrix();
        GL11.glTranslatef(Math.round(x), Math.round(y), 0);
        GL11.glScalef(facing, 1, 1);
        int skin = kind == null ? 0xFFBA8665 : kind == Kind.SKELETON ? 0xFFD7D9CC : 0xFF6C9E47;
        int shirt = kind == null ? 0xFF26B9BD : kind == Kind.ZOMBIE ? 0xFF328D8E : skin;
        if (flash) { skin = 0xFFFFD6BC; shirt = 0xFFFFF0DE; }
        int step = (int)(Math.sin(game.ticks * .65 + x * .1) * 2);
        drawBox(-10, -2, 12, 1, 0x55402B28);
        drawBox(-6, -12, 0, step, kind == Kind.SKELETON ? skin : 0xFF464C91);
        drawBox(1, -12, 7, -step, kind == Kind.SKELETON ? skin : 0xFF3B417C);
        drawBox(-7, -24, 7, -11, shirt);
        drawBox(-8, -35, 8, -23, skin);
        if (kind == Kind.CREEPER) {
            drawBox(-7, -24, 7, -6, skin);
            drawBox(-9, -7, -2, 0, 0xFF416832); drawBox(3, -7, 10, 0, 0xFF416832);
            drawBox(-5, -31, -1, -27, 0xFF17251B); drawBox(3, -31, 7, -27, 0xFF17251B);
            drawBox(-1, -28, 3, -22, 0xFF17251B); drawBox(-4, -25, 6, -21, 0xFF17251B);
        } else {
            if (kind == null) {
                drawBox(-8, -35, 8, -32, 0xFF493224); drawBox(-8, -32, -5, -26, 0xFF493224);
                drawBox(0, -26, 8, -23, 0xFF63402F);
            }
            drawBox(2, -30, 7, -28, kind == Kind.SKELETON ? 0xFF343C38 : 0xFFEBF4ED);
            drawBox(5, -30, 7, -28, kind == null ? 0xFF465EAB : 0xFF253D2B);
            drawBox(4, -22, 10, -14, skin);
            if (kind == Kind.ZOMBIE) drawBox(6, -23, 19, -19, skin);
            if (kind == Kind.SKELETON) {
                drawBox(13, -25, 15, -8, 0xFF957149);
                drawBox(15, -22, 18, -11, 0xFF957149);
                drawBox(12, -25, 13, -8, 0xFFDAD9BA);
                drawBox(-4, -22, 4, -20, 0xFF6F8078); drawBox(-4, -17, 4, -15, 0xFF6F8078);
            }
            if (kind == null) {
                if (game.swing > 0) {
                    drawBox(10, -20, 42, -16, 0xFFB3F1E5);
                    drawBox(12, -22, 16, -13, 0xFF4B8F91);
                    drawBox(6, -19, 12, -16, 0xFF704C32);
                    drawBox(27, -28, 40, -26, 0xFFEDFBE4);
                    drawBox(34, -10, 44, -8, 0xFFEDFBE4);
                } else {
                    drawBox(10, -30, 14, -10, 0xFFB3F1E5);
                    drawBox(8, -13, 16, -10, 0xFF4B8F91);
                    drawBox(11, -10, 13, -5, 0xFF704C32);
                }
            }
        }
        GL11.glPopMatrix();
    }
}
