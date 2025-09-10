package net.classicremastered.toolkit.debugmodel;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import net.classicremastered.minecraft.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MobModelDebugger {

    // -------- runtime entry --------
    public static void main(String[] args) throws Exception {
        LWJGLNatives.useMCraftClientNatives();  // %APPDATA%\.mcraft\client\native\windows
        new MobModelDebugger().run();
    }

    // -------- model/texture entries --------
    private static class MobEntry {
        final String name;
        final String texPath; // /mob/*.png
        int glTex;
        final ModelAdapter adapter;
        MobEntry(String name, String texPath, ModelAdapter adapter) {
            this.name = name; this.texPath = texPath; this.adapter = adapter;
        }
    }

    private final LinkedHashMap<Integer, MobEntry> mobs = new LinkedHashMap<Integer, MobEntry>() {{
        put(1, new MobEntry("Zombie",   "/mob/zombie.png",   ModelAdapter.ofHumanoid(new ZombieModel())));
        put(2, new MobEntry("Skeleton", "/mob/skeleton.png", ModelAdapter.ofHumanoid(new SkeletonModel())));
        put(3, new MobEntry("Creeper",  "/mob/creeper.png",  ModelAdapter.ofCreeper(new CreeperModel())));
        put(4, new MobEntry("Spider",   "/mob/spider.png",   ModelAdapter.ofSpider(new SpiderModel())));
        put(5, new MobEntry("Pig",      "/mob/pig.png",      ModelAdapter.ofAnimal(new PigModel())));
        put(6, new MobEntry("Sheep",    "/mob/sheep.png",    ModelAdapter.ofAnimal(new SheepModel())));
        put(7, new MobEntry("Villager", "/mob/villager.png", ModelAdapter.ofVillager(new VillagerModel())));
    }};

    private int currentKey = 1;

    // camera/orbit
    private float yaw = 30f, pitch = 15f, dist = 7.0f;
    private boolean wire = false, textured = true, lighting = false; // start FULLBRIGHT
    private float time; // seconds

    // head look (degrees, Classic expects degrees)
    private float headYawDeg = 0f;    // left(-) / right(+)
    private float headPitchDeg = 0f;  // down(-) / up(+)

    private void run() throws Exception {
        Display.setDisplayMode(new DisplayMode(1000, 680));
        Display.setTitle("Mob Model Debugger (1:Zombie 2:Skeleton 3:Creeper 4:Spider 5:Pig 6:Sheep 7:Villager)");
        Display.create();
        Keyboard.create();
        Mouse.create();
        initGL();

        for (MobEntry e : mobs.values()) e.glTex = loadTexture(e.texPath);

        long last = System.nanoTime();
        while (!Display.isCloseRequested()) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            time += dt;

            tickInput();
            renderFrame();
            Display.update();
            Display.sync(60);
        }

        for (MobEntry e : mobs.values()) if (e.glTex != 0) GL11.glDeleteTextures(e.glTex);
        Mouse.destroy(); Keyboard.destroy(); Display.destroy();
    }

    private void initGL() {
        GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60f, Display.getWidth() / (float) Display.getHeight(), 0.05f, 200f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        // Classic fullbright defaults
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LIGHT0);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

        // Cutout (skeleton ribs, creeper face holes)
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);

        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private void applyLitModeIfNeeded() {
        if (!lighting) return;
        // Bright white light + ambient; switch to MODULATE so lighting affects texture
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, vec4(0.0f, 1.0f, 0.0f, 0f));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE,  vec4(1f, 1f, 1f, 1f));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, vec4(1f, 1f, 1f, 1f));
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, vec4(0.35f, 0.35f, 0.35f, 1f));
    }

    private void applyFullbrightIfNeeded() {
        if (lighting) return;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LIGHT0);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
    }

    private void tickInput() {
        if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
            Display.destroy(); System.exit(0);
        }
        for (int i = Keyboard.KEY_1; i <= Keyboard.KEY_7; i++) {
            if (Keyboard.isKeyDown(i)) currentKey = 1 + (i - Keyboard.KEY_1);
        }
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                switch (Keyboard.getEventKey()) {
                    case Keyboard.KEY_W: wire = !wire; break;
                    case Keyboard.KEY_T: textured = !textured; break;
                    case Keyboard.KEY_L: // toggle fullbright <-> lit
                        lighting = !lighting;
                        if (lighting) applyLitModeIfNeeded(); else applyFullbrightIfNeeded();
                        break;
                    case Keyboard.KEY_R:
                        yaw = 30f; pitch = 15f; dist = 7.0f;
                        headYawDeg = 0f; headPitchDeg = 0f;
                        break;
                    // Head look (H/J yaw, U/K pitch)
                    case Keyboard.KEY_H: headYawDeg = clamp(headYawDeg - 5f, -90f, 90f); break;
                    case Keyboard.KEY_J: headYawDeg = clamp(headYawDeg + 5f, -90f, 90f); break;
                    case Keyboard.KEY_U: headPitchDeg = clamp(headPitchDeg + 5f, -60f, 60f); break;
                    case Keyboard.KEY_K: headPitchDeg = clamp(headPitchDeg - 5f, -60f, 60f); break;
                    default: break;
                }
            }
        }
        if (Mouse.isButtonDown(0)) {
            yaw   += Mouse.getDX() * 0.4f;
            pitch -= Mouse.getDY() * 0.4f;
            if (pitch < -89f) pitch = -89f; if (pitch > 89f) pitch = 89f;
        }
        dist -= Mouse.getDWheel() * 0.01f;
        if (dist < 2f) dist = 2f; if (dist > 25f) dist = 25f;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void renderFrame() {
        GL11.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, wire ? GL11.GL_LINE : GL11.GL_FILL);
        if (textured) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);

        // apply current shading mode (fullbright or lit)
        if (lighting) applyLitModeIfNeeded(); else applyFullbrightIfNeeded();

        GL11.glLoadIdentity();
        float ex = (float)(Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        float ey = (float)(Math.sin(Math.toRadians(pitch)));
        float ez = (float)(Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        GLU.gluLookAt(ex*dist, ey*dist, ez*dist, 0, 1.2f, 0, 0, 1, 0);

        drawGrid(12, 1.0f);

        MobEntry e = mobs.get(currentKey);
        if (e == null) e = mobs.values().iterator().next();

        if (textured && e.glTex != 0) GL11.glBindTexture(GL11.GL_TEXTURE_2D, e.glTex);
        else GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        float walkPhase = time * 1.5f;
        float walkSpeed = 0.6f;

        GL11.glPushMatrix();
        {
            final float modelScale = 0.0625f;
            GL11.glTranslatef(0f, 24f * modelScale, 0f);
            GL11.glScalef(1f, -1f, 1f);
            GL11.glFrontFace(GL11.GL_CW);

            e.adapter.render(walkPhase, walkSpeed, time, headYawDeg, headPitchDeg);

            GL11.glFrontFace(GL11.GL_CCW);
        }
        GL11.glPopMatrix();

        Display.setTitle("Mob Model Debugger — " + e.name +
                "  [W:wire T:texture L:light R:reset H/J yaw U/K pitch]  scroll zoom / drag rotate");
    }

    // ---------- adapters from your models to the viewer ----------

    public interface ModelAdapter {
        void render(float walkPhase, float walkAmp, float time, float headYawDeg, float headPitchDeg);

        static ModelAdapter ofHumanoid(final HumanoidModel m) {
            return (wp, wa, t, hy, hp) -> m.render(wp, wa, t, hy, hp, 0.0625f);
        }
        static ModelAdapter ofCreeper(final CreeperModel m) {
            return (wp, wa, t, hy, hp) -> m.render(wp, wa, t, hy, hp, 0.0625f);
        }
        static ModelAdapter ofSpider(final SpiderModel m) {
            return (wp, wa, t, hy, hp) -> m.render(wp, wa, t, hy, hp, 0.0625f);
        }
        static ModelAdapter ofAnimal(final AnimalModel m) {
            return (wp, wa, t, hy, hp) -> m.render(wp, wa, t, hy, hp, 0.0625f);
        }
        static ModelAdapter ofVillager(final VillagerModel m) {
            return (wp, wa, t, hy, hp) -> m.render(wp, wa, t, hy, hp, 0.0625f);
        }
    }

    // ---------- utils ----------

    private static FloatBuffer vec4(float a, float b, float c, float d) {
        FloatBuffer fb = BufferUtils.createFloatBuffer(4);
        fb.put(a).put(b).put(c).put(d);
        fb.flip();
        return fb;
    }

    private static int loadTexture(String classpath) {
        try (InputStream in = MobModelDebugger.class.getResourceAsStream(classpath)) {
            if (in == null) { System.err.println("Missing texture: " + classpath); return 0; }
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w*h];
            img.getRGB(0,0,w,h,px,0,w);

            ByteBuffer buf = BufferUtils.createByteBuffer(w*h*4);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                int p = px[y*w+x];
                buf.put((byte)((p>>16)&0xFF));
                buf.put((byte)((p>> 8)&0xFF));
                buf.put((byte)((p    )&0xFF));
                buf.put((byte)((p>>24)&0xFF));
            }
            buf.flip();

            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Exception e) {
            e.printStackTrace(); return 0;
        }
    }

    private void drawGrid(int half, float step) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(0.18f, 0.20f, 0.24f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = -half; i <= half; i++) {
            GL11.glVertex3f(i*step, 0, -half*step);
            GL11.glVertex3f(i*step, 0,  half*step);
            GL11.glVertex3f(-half*step, 0, i*step);
            GL11.glVertex3f( half*step, 0, i*step);
        }
        GL11.glEnd();
        if (textured) GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
    }
}
